package com.school.student_manage.ai.service;

import com.school.student_manage.ai.agent.AgentEngine;
import com.school.student_manage.ai.agent.AgentListener;
import com.school.student_manage.ai.agent.AgentOutcome;
import com.school.student_manage.ai.agent.AgentStep;
import com.school.student_manage.ai.llm.LlmMessage;
import com.school.student_manage.ai.store.ConversationStore;
import com.school.student_manage.ai.tool.JsonSupport;
import com.school.student_manage.ai.web.AiChatRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 对话编排服务：会话管理 + SSE 事件推送 + 消息持久化。
 *
 * <p>事件协议（前端按 event 名分发）：</p>
 * <pre>
 * meta      会话与模型元信息
 * step      工具调用步骤（running / success / failed）
 * delta     回答增量文本
 * citations 知识库引用
 * done      结束（含 token 用量与耗时）
 * error     异常
 * </pre>
 */
@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    @Resource
    private ConversationStore store;

    @Resource
    private AgentEngine engine;

    private final ExecutorService executor = Executors.newFixedThreadPool(8, runnable -> {
        Thread thread = new Thread(runnable, "ai-chat");
        thread.setDaemon(true);
        return thread;
    });

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    /**
     * 非流式对话：便于接口验收、自动化测试与二次开发（同一套 Agent 逻辑，无 SSE）。
     */
    public Map<String, Object> chatOnce(AiChatRequest request, String userName) {
        long start = System.currentTimeMillis();
        String question = request.getMessage() == null ? "" : request.getMessage().trim();
        String scene = request.getScene() == null ? "assistant" : request.getScene();
        if (question.isEmpty()) {
            throw new IllegalArgumentException("问题不能为空");
        }

        int conversationId;
        boolean newConversation = Boolean.TRUE.equals(request.getNewConversation()) || request.getConversationId() == null;
        if (newConversation) {
            String title = question.length() > 24 ? question.substring(0, 24) + "…" : question;
            conversationId = store.create(title, scene, userName);
        } else {
            conversationId = request.getConversationId();
            if (store.get(conversationId) == null) {
                String title = question.length() > 24 ? question.substring(0, 24) + "…" : question;
                conversationId = store.create(title, scene, userName);
            }
        }

        List<LlmMessage> messages = new ArrayList<LlmMessage>();
        messages.add(engine.systemMessage(scene));
        if (!newConversation) {
            messages.addAll(store.history(conversationId, 6));
        }
        messages.add(LlmMessage.user(question));
        store.append(conversationId, "user", question, null, null, null, null, null);

        AgentOutcome outcome = engine.execute(messages, scene, new AgentListener() {
        });

        List<Map<String, Object>> stepsLog = new ArrayList<Map<String, Object>>();
        for (AgentStep step : outcome.getSteps()) {
            stepsLog.add(step.toEvent());
        }
        String citationsJson = outcome.getCitations().isEmpty() ? null : JsonSupport.toJson(outcome.getCitations());
        store.append(conversationId, "assistant", outcome.getContent(),
                stepsLog.isEmpty() ? null : JsonSupport.toJson(stepsLog), citationsJson, citationsJson,
                outcome.getMode(), outcome.getModel());

        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("conversationId", conversationId);
        response.put("mode", outcome.getMode());
        response.put("model", outcome.getModel());
        response.put("content", outcome.getContent());
        response.put("steps", stepsLog);
        response.put("citations", outcome.getCitations());
        response.put("toolRounds", outcome.getToolRounds());
        response.put("promptTokens", outcome.getPromptTokens());
        response.put("completionTokens", outcome.getCompletionTokens());
        response.put("elapsedMs", System.currentTimeMillis() - start);
        return response;
    }

    public SseEmitter chatStream(AiChatRequest request, String userName) {
        SseEmitter emitter = new SseEmitter(300_000L);
        emitter.onTimeout(emitter::complete);
        executor.submit(() -> {
            try {
                doChat(request, userName, emitter);
            } catch (Exception e) {
                log.warn("对话执行失败：{}", e.getMessage());
                send(emitter, "error", message("对话执行失败：" + e.getMessage(), null));
            } finally {
                try {
                    emitter.complete();
                } catch (Exception ignore) {
                    // 已关闭
                }
            }
        });
        return emitter;
    }

    private void doChat(AiChatRequest request, String userName, SseEmitter emitter) {
        long start = System.currentTimeMillis();
        String question = request.getMessage() == null ? "" : request.getMessage().trim();
        if (question.isEmpty()) {
            send(emitter, "error", message("问题不能为空", null));
            return;
        }
        String scene = request.getScene() == null ? "assistant" : request.getScene();

        // 1. 会话准备
        int conversationId;
        boolean newConversation = Boolean.TRUE.equals(request.getNewConversation()) || request.getConversationId() == null;
        if (newConversation) {
            String title = question.length() > 24 ? question.substring(0, 24) + "…" : question;
            conversationId = store.create(title, scene, userName);
        } else {
            conversationId = request.getConversationId();
            if (store.get(conversationId) == null) {
                String title = question.length() > 24 ? question.substring(0, 24) + "…" : question;
                conversationId = store.create(title, scene, userName);
            }
        }

        // 2. 组装上下文（先取历史，再落库当前提问，避免重复）
        List<LlmMessage> messages = new ArrayList<LlmMessage>();
        messages.add(engine.systemMessage(scene));
        if (!newConversation) {
            messages.addAll(store.history(conversationId, 6));
        }
        messages.add(LlmMessage.user(question));
        store.append(conversationId, "user", question, null, null, null, null, null);

        Map<String, Object> meta = new LinkedHashMap<String, Object>();
        meta.put("conversationId", conversationId);
        meta.put("scene", scene);
        meta.put("question", question);
        send(emitter, "meta", meta);

        // 3. Agent 执行
        final int finalConversationId = conversationId;
        List<Map<String, Object>> stepsLog = new ArrayList<Map<String, Object>>();
        AgentOutcome outcome = engine.execute(messages, scene, new AgentListener() {
            @Override
            public void onMeta(Map<String, Object> metaPayload) {
                send(emitter, "meta", metaPayload);
            }

            @Override
            public void onStepStart(AgentStep step) {
                send(emitter, "step", step.toEvent());
            }

            @Override
            public void onStepEnd(AgentStep step) {
                stepsLog.add(step.toEvent());
                send(emitter, "step", step.toEvent());
            }

            @Override
            public void onDelta(String text) {
                send(emitter, "delta", message(text, null));
            }

            @Override
            public void onCitations(List<Map<String, Object>> citations) {
                Map<String, Object> payload = new LinkedHashMap<String, Object>();
                payload.put("items", citations);
                send(emitter, "citations", payload);
            }
        });

        // 4. 落库
        String citationsJson = outcome.getCitations().isEmpty() ? null : JsonSupport.toJson(outcome.getCitations());
        int messageId = store.append(finalConversationId, "assistant", outcome.getContent(),
                stepsLog.isEmpty() ? null : JsonSupport.toJson(stepsLog), citationsJson, citationsJson,
                outcome.getMode(), outcome.getModel());

        Map<String, Object> done = new LinkedHashMap<String, Object>();
        done.put("conversationId", finalConversationId);
        done.put("messageId", messageId);
        done.put("mode", outcome.getMode());
        done.put("model", outcome.getModel());
        done.put("toolRounds", outcome.getToolRounds());
        done.put("steps", stepsLog);
        done.put("promptTokens", outcome.getPromptTokens());
        done.put("completionTokens", outcome.getCompletionTokens());
        done.put("elapsedMs", System.currentTimeMillis() - start);
        send(emitter, "done", done);
    }

    private Map<String, Object> message(String text, String extra) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("text", text);
        if (extra != null) {
            payload.put("extra", extra);
        }
        return payload;
    }

    private void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(JsonSupport.toJson(data)));
        } catch (IOException | IllegalStateException e) {
            log.debug("SSE 推送失败（连接可能已关闭）：{}", e.getMessage());
        } catch (Exception e) {
            log.debug("SSE 推送异常：{}", e.getMessage());
        }
    }
}
