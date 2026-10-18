package com.school.student_manage.ai.llm;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次模型调用的结果：文本内容 + 需要执行的工具调用。
 */
public class ChatResult {

    private String content = "";
    private List<LlmToolCall> toolCalls = new ArrayList<LlmToolCall>();
    private String model;
    private String finishReason;
    private int promptTokens;
    private int completionTokens;

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public boolean hasContent() {
        return content != null && !content.trim().isEmpty();
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<LlmToolCall> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<LlmToolCall> toolCalls) {
        this.toolCalls = toolCalls;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getFinishReason() {
        return finishReason;
    }

    public void setFinishReason(String finishReason) {
        this.finishReason = finishReason;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(int promptTokens) {
        this.promptTokens = promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(int completionTokens) {
        this.completionTokens = completionTokens;
    }
}
