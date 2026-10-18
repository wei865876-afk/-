package com.school.student_manage.ai.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.student_manage.ai.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * OpenAI 兼容协议客户端（DeepSeek / 通义 / 智谱 / Moonshot / OpenAI / vLLM / Ollama-v1 均可）。
 *
 * <p>实现要点：</p>
 * <ul>
 *   <li>优先使用 SSE 流式输出，逐 token 回调，首字延迟低；</li>
 *   <li>流式失败（如服务端不支持）自动降级为非流式；</li>
 *   <li>支持 function calling 的增量参数拼接；</li>
 *   <li>可选 /embeddings 接口，用于知识库语义检索。</li>
 * </ul>
 */
@Component
public class OpenAiCompatibleClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleClient.class);

    private final AiProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http;

    public OpenAiCompatibleClient(AiProperties props) {
        this.props = props;
        int timeout = Math.max(10, props.getTimeoutSeconds());
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String provider() {
        return props.getModel() + " @ " + host(props.getBaseUrl());
    }

    @Override
    public boolean available() {
        return props.isRemote();
    }

    @Override
    public boolean supportsEmbedding() {
        return props.isEmbeddingEnabled();
    }

    @Override
    public ChatResult chat(List<LlmMessage> messages, List<ToolSpec> tools, StreamHandler handler) {
        try {
            return streamChat(messages, tools, handler);
        } catch (Exception e) {
            log.warn("流式调用失败，降级为非流式：{}", e.getMessage());
            try {
                ChatResult result = blockingChat(messages, tools);
                if (handler != null && result.hasContent()) {
                    handler.onDelta(result.getContent());
                }
                return result;
            } catch (Exception ex) {
                throw new LlmException("调用大模型失败：" + ex.getMessage(), ex);
            }
        }
    }

    // ------------------------------------------------------------------ 流式

    private ChatResult streamChat(List<LlmMessage> messages, List<ToolSpec> tools, StreamHandler handler)
            throws IOException, InterruptedException {
        Map<String, Object> body = baseBody(messages, tools);
        body.put("stream", Boolean.TRUE);

        HttpRequest request = baseRequest("/chat/completions", body);
        HttpResponse<Stream<String>> response =
                http.send(request, HttpResponse.BodyHandlers.ofLines());

        if (response.statusCode() >= 400) {
            String err = readError(response.body());
            throw new IOException("HTTP " + response.statusCode() + " " + err);
        }

        StringBuilder content = new StringBuilder();
        Map<Integer, LlmToolCall> toolCalls = new HashMap<Integer, LlmToolCall>();
        Map<Integer, StringBuilder> argBuffers = new HashMap<Integer, StringBuilder>();
        String finishReason = null;
        int promptTokens = 0;
        int completionTokens = 0;

        try (Stream<String> lines = response.body()) {
            for (String raw : (Iterable<String>) lines::iterator) {
                if (raw == null) {
                    continue;
                }
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith(":")) {
                    continue;
                }
                if (!line.startsWith("data:")) {
                    continue;
                }
                String payload = line.substring(5).trim();
                if ("[DONE]".equals(payload)) {
                    break;
                }
                JsonNode node;
                try {
                    node = mapper.readTree(payload);
                } catch (Exception ignore) {
                    continue;
                }
                if (node.has("usage") && !node.get("usage").isNull()) {
                    promptTokens = node.get("usage").path("prompt_tokens").asInt(promptTokens);
                    completionTokens = node.get("usage").path("completion_tokens").asInt(completionTokens);
                }
                JsonNode choices = node.path("choices");
                if (!choices.isArray() || choices.isEmpty()) {
                    continue;
                }
                JsonNode choice = choices.get(0);
                if (choice.hasNonNull("finish_reason")) {
                    finishReason = choice.get("finish_reason").asText();
                }
                JsonNode delta = choice.path("delta");
                JsonNode textNode = delta.get("content");
                if (textNode != null && !textNode.isNull()) {
                    String text = textNode.asText();
                    if (!text.isEmpty()) {
                        content.append(text);
                        if (handler != null) {
                            handler.onDelta(text);
                        }
                    }
                }
                JsonNode calls = delta.get("tool_calls");
                if (calls != null && calls.isArray()) {
                    for (JsonNode call : calls) {
                        int index = call.path("index").asInt(0);
                        LlmToolCall acc = toolCalls.get(index);
                        if (acc == null) {
                            acc = new LlmToolCall();
                            acc.setId(call.path("id").asText(null));
                            acc.setName(call.path("function").path("name").asText(null));
                            toolCalls.put(index, acc);
                            argBuffers.put(index, new StringBuilder());
                        }
                        if (call.hasNonNull("id")) {
                            acc.setId(call.get("id").asText());
                        }
                        JsonNode fn = call.get("function");
                        if (fn != null) {
                            if (fn.hasNonNull("name")) {
                                acc.setName(fn.get("name").asText());
                            }
                            if (fn.hasNonNull("arguments")) {
                                argBuffers.get(index).append(fn.get("arguments").asText());
                            }
                        }
                    }
                }
            }
        }

        ChatResult result = new ChatResult();
        result.setContent(content.toString());
        result.setModel(props.getModel());
        result.setFinishReason(finishReason);
        result.setPromptTokens(promptTokens);
        result.setCompletionTokens(completionTokens);
        List<LlmToolCall> calls = new ArrayList<LlmToolCall>();
        for (Map.Entry<Integer, LlmToolCall> e : toolCalls.entrySet()) {
            LlmToolCall call = e.getValue();
            StringBuilder buf = argBuffers.get(e.getKey());
            call.setArguments(buf == null ? "{}" : buf.toString());
            if (call.getId() == null) {
                call.setId("call_" + e.getKey() + "_" + System.nanoTime());
            }
            if (call.getName() != null && !call.getName().isEmpty()) {
                calls.add(call);
            }
        }
        result.setToolCalls(calls);
        return result;
    }

    // ---------------------------------------------------------------- 非流式

    private ChatResult blockingChat(List<LlmMessage> messages, List<ToolSpec> tools)
            throws IOException, InterruptedException {
        Map<String, Object> body = baseBody(messages, tools);
        body.put("stream", Boolean.FALSE);
        HttpRequest request = baseRequest("/chat/completions", body);
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            throw new IOException("HTTP " + response.statusCode() + " " + truncate(response.body(), 300));
        }
        JsonNode root = mapper.readTree(response.body());
        JsonNode choice = root.path("choices").path(0);
        JsonNode message = choice.path("message");

        ChatResult result = new ChatResult();
        result.setContent(message.path("content").asText(""));
        result.setModel(root.path("model").asText(props.getModel()));
        result.setFinishReason(choice.path("finish_reason").asText(null));
        result.setPromptTokens(root.path("usage").path("prompt_tokens").asInt(0));
        result.setCompletionTokens(root.path("usage").path("completion_tokens").asInt(0));

        List<LlmToolCall> calls = new ArrayList<LlmToolCall>();
        JsonNode toolCalls = message.get("tool_calls");
        if (toolCalls != null && toolCalls.isArray()) {
            for (JsonNode node : toolCalls) {
                LlmToolCall call = new LlmToolCall();
                call.setId(node.path("id").asText("call_" + calls.size()));
                call.setName(node.path("function").path("name").asText(null));
                call.setArguments(node.path("function").path("arguments").asText("{}"));
                if (call.getName() != null && !call.getName().isEmpty()) {
                    calls.add(call);
                }
            }
        }
        result.setToolCalls(calls);
        return result;
    }

    // ------------------------------------------------------------------ 向量

    @Override
    public float[] embed(String text) {
        if (!supportsEmbedding() || text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            Map<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("model", props.getEmbeddingModel());
            body.put("input", text);
            HttpRequest request = baseRequest("/embeddings", body);
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                log.warn("向量接口返回 {}：{}", response.statusCode(), truncate(response.body(), 200));
                return null;
            }
            JsonNode arr = mapper.readTree(response.body()).path("data").path(0).path("embedding");
            if (!arr.isArray()) {
                return null;
            }
            float[] vec = new float[arr.size()];
            for (int i = 0; i < arr.size(); i++) {
                vec[i] = (float) arr.get(i).asDouble();
            }
            return vec;
        } catch (Exception e) {
            log.warn("获取向量失败：{}", e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------ 工具

    private Map<String, Object> baseBody(List<LlmMessage> messages, List<ToolSpec> tools) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("model", props.getModel());
        List<Map<String, Object>> msgs = new ArrayList<Map<String, Object>>();
        for (LlmMessage m : messages) {
            msgs.add(m.toApiMap());
        }
        body.put("messages", msgs);
        body.put("temperature", props.getTemperature());
        body.put("max_tokens", props.getMaxTokens());
        if (tools != null && !tools.isEmpty()) {
            List<Map<String, Object>> specs = new ArrayList<Map<String, Object>>();
            for (ToolSpec spec : tools) {
                Map<String, Object> fn = new LinkedHashMap<String, Object>();
                fn.put("name", spec.getName());
                fn.put("description", spec.getDescription());
                fn.put("parameters", spec.getParameters() == null ? emptySchema() : spec.getParameters());
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("type", "function");
                item.put("function", fn);
                specs.add(item);
            }
            body.put("tools", specs);
            body.put("tool_choice", "auto");
        }
        return body;
    }

    private HttpRequest baseRequest(String path, Map<String, Object> body) throws IOException {
        String url = normalize(props.getBaseUrl()) + path;
        String json = mapper.writeValueAsString(body);
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(Math.max(20, props.getTimeoutSeconds())))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "text/event-stream, application/json")
                .header("Authorization", "Bearer " + props.getApiKey().trim())
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
    }

    private Map<String, Object> emptySchema() {
        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<String, Object>());
        return schema;
    }

    private String normalize(String baseUrl) {
        String url = baseUrl == null ? "" : baseUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private String host(String url) {
        try {
            return URI.create(normalize(url)).getHost();
        } catch (Exception e) {
            return url;
        }
    }

    private String readError(Stream<String> lines) {
        StringBuilder sb = new StringBuilder();
        try (Stream<String> s = lines) {
            s.limit(5).forEach(line -> sb.append(line).append(' '));
        }
        return truncate(sb.toString(), 300);
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    /** 远程调用异常 */
    public static class LlmException extends RuntimeException {
        public LlmException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
