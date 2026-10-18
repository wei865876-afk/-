package com.school.student_manage.ai.tool;

import com.school.student_manage.ai.llm.ToolSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册中心：收集所有 {@link AiTool} Bean，向模型暴露声明，并负责统一执行与耗时统计。
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, AiTool> tools = new LinkedHashMap<String, AiTool>();

    public ToolRegistry(List<AiTool> toolList) {
        for (AiTool tool : toolList) {
            tools.put(tool.name(), tool);
        }
        log.info("AI 工具注册完成，共 {} 个：{}", tools.size(), tools.keySet());
    }

    /** 给模型使用的工具声明 */
    public List<ToolSpec> specs() {
        List<ToolSpec> specs = new ArrayList<ToolSpec>();
        for (AiTool tool : tools.values()) {
            if (tool.exposed()) {
                specs.add(new ToolSpec(tool.name(), tool.description(), tool.parameterSchema()));
            }
        }
        return specs;
    }

    /** 前端展示用的工具清单 */
    public List<Map<String, Object>> catalog() {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        for (AiTool tool : tools.values()) {
            if (!tool.exposed()) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("name", tool.name());
            item.put("title", tool.title());
            item.put("description", tool.description());
            item.put("parameters", tool.parameterSchema());
            list.add(item);
        }
        return list;
    }

    public boolean contains(String name) {
        return tools.containsKey(name);
    }

    public ToolResult execute(String name, Map<String, Object> args) {
        AiTool tool = tools.get(name);
        if (tool == null) {
            return ToolResult.fail(name, name, "未找到工具 " + name);
        }
        long start = System.currentTimeMillis();
        try {
            ToolResult result = tool.execute(args == null ? new LinkedHashMap<String, Object>() : args);
            if (result == null) {
                result = ToolResult.fail(name, tool.title(), "工具返回空结果");
            }
            result.setDurationMs(System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            log.warn("工具 {} 执行异常：{}", name, e.getMessage());
            ToolResult fail = ToolResult.fail(name, tool.title(), e.getMessage() == null ? e.toString() : e.getMessage());
            fail.setDurationMs(System.currentTimeMillis() - start);
            return fail;
        }
    }
}
