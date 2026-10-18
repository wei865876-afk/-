package com.school.student_manage.ai.agent;

import java.util.List;
import java.util.Map;

/** Agent 事件监听器：把执行过程实时推给前端（SSE） */
public interface AgentListener {

    default void onMeta(Map<String, Object> meta) {
    }

    default void onStepStart(AgentStep step) {
    }

    default void onStepEnd(AgentStep step) {
    }

    default void onDelta(String text) {
    }

    default void onCitations(List<Map<String, Object>> citations) {
    }
}
