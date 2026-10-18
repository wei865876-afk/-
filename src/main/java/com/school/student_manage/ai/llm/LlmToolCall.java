package com.school.student_manage.ai.llm;

/**
 * 一次工具调用请求（OpenAI function calling 协议）。
 */
public class LlmToolCall {

    private String id;
    private String type = "function";
    private String name;
    private String arguments;

    public LlmToolCall() {
    }

    public LlmToolCall(String id, String name, String arguments) {
        this.id = id;
        this.name = name;
        this.arguments = arguments;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getArguments() {
        return arguments;
    }

    public void setArguments(String arguments) {
        this.arguments = arguments;
    }
}
