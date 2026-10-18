package com.school.student_manage.ai.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对话消息（对齐 OpenAI messages 结构，可直接序列化为请求体）。
 */
public class LlmMessage {

    private String role;
    private String content;
    private String toolCallId;
    private String name;
    private List<LlmToolCall> toolCalls;

    public static LlmMessage system(String content) {
        return of("system", content);
    }

    public static LlmMessage user(String content) {
        return of("user", content);
    }

    public static LlmMessage assistant(String content) {
        return of("assistant", content);
    }

    public static LlmMessage assistantToolCalls(List<LlmToolCall> calls) {
        LlmMessage m = of("assistant", null);
        m.setToolCalls(calls);
        return m;
    }

    public static LlmMessage tool(String toolCallId, String name, String content) {
        LlmMessage m = of("tool", content);
        m.setToolCallId(toolCallId);
        m.setName(name);
        return m;
    }

    public static LlmMessage of(String role, String content) {
        LlmMessage m = new LlmMessage();
        m.setRole(role);
        m.setContent(content);
        return m;
    }

    /** 转换为 OpenAI 协议所需的 Map 结构（省略空字段） */
    public Map<String, Object> toApiMap() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("role", role);
        if (content != null) {
            map.put("content", content);
        }
        if (toolCallId != null) {
            map.put("tool_call_id", toolCallId);
        }
        if (name != null) {
            map.put("name", name);
        }
        if (toolCalls != null && !toolCalls.isEmpty()) {
            List<Map<String, Object>> calls = new ArrayList<Map<String, Object>>();
            for (LlmToolCall call : toolCalls) {
                Map<String, Object> fn = new LinkedHashMap<String, Object>();
                fn.put("name", call.getName());
                fn.put("arguments", call.getArguments() == null ? "{}" : call.getArguments());
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("id", call.getId());
                item.put("type", call.getType() == null ? "function" : call.getType());
                item.put("function", fn);
                calls.add(item);
            }
            map.put("tool_calls", calls);
        }
        return map;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<LlmToolCall> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<LlmToolCall> toolCalls) {
        this.toolCalls = toolCalls;
    }
}
