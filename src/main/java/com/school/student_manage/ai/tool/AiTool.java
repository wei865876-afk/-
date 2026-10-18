package com.school.student_manage.ai.tool;

import java.util.Map;

/**
 * Agent 可调用的工具（Function Calling）。
 */
public interface AiTool {

    /** 工具名，需符合 [a-zA-Z0-9_-]，与模型返回的 function.name 对应 */
    String name();

    /** 中文名，用于前端展示 Agent 执行步骤 */
    String title();

    /** 给模型看的说明：何时调用、参数含义、口语示例 */
    String description();

    /** JSON Schema 形式的参数定义 */
    Map<String, Object> parameterSchema();

    /** 执行工具 */
    ToolResult execute(Map<String, Object> args) throws Exception;

    /** 是否需要展示给前端（内部工具可设为 false） */
    default boolean exposed() {
        return true;
    }
}
