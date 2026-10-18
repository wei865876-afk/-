package com.school.student_manage.ai.agent;

import java.util.ArrayList;
import java.util.List;

/** Agent 单次会话的执行结果 */
public class AgentOutcome {

    private String content = "";
    private final List<AgentStep> steps = new ArrayList<AgentStep>();
    private final List<java.util.Map<String, Object>> citations = new ArrayList<java.util.Map<String, Object>>();
    private String mode;
    private String model;
    private int toolRounds;
    private int promptTokens;
    private int completionTokens;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<AgentStep> getSteps() {
        return steps;
    }

    public List<java.util.Map<String, Object>> getCitations() {
        return citations;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getToolRounds() {
        return toolRounds;
    }

    public void setToolRounds(int toolRounds) {
        this.toolRounds = toolRounds;
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
