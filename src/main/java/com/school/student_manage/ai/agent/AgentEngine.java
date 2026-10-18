package com.school.student_manage.ai.agent;

import com.school.student_manage.ai.config.AiProperties;
import com.school.student_manage.ai.data.SchemaService;
import com.school.student_manage.ai.llm.ChatResult;
import com.school.student_manage.ai.llm.LlmClient;
import com.school.student_manage.ai.llm.LlmClientRouter;
import com.school.student_manage.ai.llm.LlmMessage;
import com.school.student_manage.ai.llm.LlmToolCall;
import com.school.student_manage.ai.llm.ToolSpec;
import com.school.student_manage.ai.prompt.Prompts;
import com.school.student_manage.ai.tool.JsonSupport;
import com.school.student_manage.ai.tool.ToolRegistry;
import com.school.student_manage.ai.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 执行引擎：大模型 + 工具循环（ReAct 风格）。
 *
 * <pre>
 * 用户提问 → 模型决策是否调用工具 → 执行工具 → 结果回填 → 模型继续推理 → … → 生成最终回答
 * </pre>
 *
 * 每一轮的工具调用都会通过 {@link AgentListener} 实时上报，前端据此渲染"思考过程"、
 * 工具结果卡片（表格/图表/引用），实现可解释的 AI 交互。
 */
@Component
public class AgentEngine {

    private static final Logger log = LoggerFactory.getLogger(AgentEngine.class);
    private static final int TOOL_RESULT_MAX_CHARS = 6000;

    @Resource
    private AiProperties props;

    @Resource
    private LlmClientRouter llm;

    @Resource
    private ToolRegistry toolRegistry;

    @Resource
    private SchemaService schemaService;

    /**
     * 执行一轮完整对话。
     *
     * @param messages 已包含 system + 历史 + 本次用户提问的消息列表
     * @param scene    场景标识，用于选择系统提示词
     * @param listener 事件监听
     */
    public AgentOutcome execute(List<LlmMessage> messages, String scene, AgentListener listener) {
        long start = System.currentTimeMillis();
        AgentOutcome outcome = new AgentOutcome();
        outcome.setMode(llm.activeMode());
        outcome.setModel(llm.describe());

        List<ToolSpec> specs = toolRegistry.specs();
        Map<String, Object> meta = new LinkedHashMap<String, Object>();
        meta.put("mode", llm.activeMode());
        meta.put("model", llm.describe());
        meta.put("tools", toolNames(specs));
        meta.put("localFallback", "local".equals(llm.activeMode()));
        listener.onMeta(meta);

        StringBuilder content = new StringBuilder();
        int rounds = 0;
        int maxRounds = Math.max(1, props.getMaxToolRounds());

        while (rounds <= maxRounds) {
            rounds++;
            ChatResult result = llm.chat(messages, specs, listener::onDelta);
            outcome.setPromptTokens(outcome.getPromptTokens() + result.getPromptTokens());
            outcome.setCompletionTokens(outcome.getCompletionTokens() + result.getCompletionTokens());

            if (!result.hasToolCalls()) {
                if (result.hasContent()) {
                    content.append(result.getContent());
                }
                break;
            }

            // 模型同时输出了文本又发起工具调用（少见），保留文本
            if (result.hasContent()) {
                content.append(result.getContent());
            }
            messages.add(LlmMessage.assistantToolCalls(result.getToolCalls()));

            for (LlmToolCall call : result.getToolCalls()) {
                Map<String, Object> args = JsonSupport.parseObject(call.getArguments());
                String title = toolRegistry.contains(call.getName()) ? null : call.getName();
                AgentStep step = new AgentStep(
                        "step_" + System.nanoTime(),
                        call.getName(),
                        title == null ? toolTitle(call.getName()) : title,
                        args);
                listener.onStepStart(step);

                ToolResult toolResult = toolRegistry.execute(call.getName(), args);
                step.complete(toolResult);
                outcome.getSteps().add(step);
                listener.onStepEnd(step);

                collectCitations(toolResult, outcome, listener);

                messages.add(LlmMessage.tool(call.getId(), call.getName(),
                        truncate(JsonSupport.toJson(toolResult), TOOL_RESULT_MAX_CHARS)));
            }

            if (rounds > maxRounds) {
                content.append("\n\n> 已达到最大工具调用轮次，先给出当前结论。\n");
                break;
            }
        }

        if (content.length() == 0) {
            // 兜底：不带工具再问一次，确保有自然语言回答
            ChatResult finalResult = llm.chat(messages, null, listener::onDelta);
            if (finalResult.hasContent()) {
                content.append(finalResult.getContent());
            }
        }

        outcome.setContent(content.toString());
        outcome.setToolRounds(Math.max(0, rounds - 1));
        log.debug("Agent 执行完成，工具轮次 {}，耗时 {}ms", outcome.getToolRounds(), System.currentTimeMillis() - start);
        return outcome;
    }

    /** 供非对话场景（如看板洞察）直接调用一次模型 */
    public ChatResult chatOnce(List<LlmMessage> messages) {
        return llm.chat(messages, null, null);
    }

    /** 组装系统提示词 */
    public LlmMessage systemMessage(String scene) {
        return LlmMessage.system(Prompts.system(props, schemaService.schemaPrompt(), scene));
    }

    private void collectCitations(ToolResult result, AgentOutcome outcome, AgentListener listener) {
        Object data = result.getData();
        if (!(data instanceof Map)) {
            return;
        }
        Object citations = ((Map<?, ?>) data).get("citations");
        if (!(citations instanceof List)) {
            return;
        }
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        for (Object item : (List<?>) citations) {
            if (item instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) item;
                list.add(map);
            }
        }
        if (!list.isEmpty()) {
            outcome.getCitations().addAll(list);
            listener.onCitations(list);
        }
    }

    private List<String> toolNames(List<ToolSpec> specs) {
        List<String> names = new ArrayList<String>();
        for (ToolSpec spec : specs) {
            names.add(spec.getName());
        }
        return names;
    }

    private String toolTitle(String name) {
        for (Map<String, Object> item : toolRegistry.catalog()) {
            if (name.equals(item.get("name"))) {
                return String.valueOf(item.get("title"));
            }
        }
        return name;
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "{}";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    /** 暴露给外部（如洞察服务）使用的模型客户端 */
    public LlmClient client() {
        return llm;
    }
}
