package com.school.student_manage.ai.llm;

import java.util.Map;

/**
 * 暴露给大模型的工具/函数声明（JSON Schema 参数）。
 */
public class ToolSpec {

    private String name;
    private String description;
    private Map<String, Object> parameters;

    public ToolSpec() {
    }

    public ToolSpec(String name, String description, Map<String, Object> parameters) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }
}
