package com.school.student_manage.ai.store;

import com.school.student_manage.ai.llm.LlmMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** AI 会话与消息持久化（多轮上下文可追溯、可回看） */
@Repository
public class ConversationStore {

    @Resource
    private JdbcTemplate jdbcTemplate;

    public int create(String title, String scene, String userName) {
        jdbcTemplate.update(
                "INSERT INTO ai_conversation (title, scene, user_name) VALUES (?,?,?)",
                title, scene, userName);
        Integer id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM ai_conversation", Integer.class);
        return id == null ? 0 : id;
    }

    public void rename(int id, String title) {
        jdbcTemplate.update("UPDATE ai_conversation SET title = ?, updated_at = NOW() WHERE id = ?", title, id);
    }

    public void delete(int id) {
        jdbcTemplate.update("DELETE FROM ai_conversation WHERE id = ?", id);
    }

    public List<Map<String, Object>> list(int limit) {
        return jdbcTemplate.queryForList(
                "SELECT id, title, scene, user_name, message_count, created_at, updated_at "
                        + "FROM ai_conversation ORDER BY updated_at DESC LIMIT ?", Math.max(1, limit));
    }

    public Map<String, Object> get(int id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, title, scene, user_name, message_count, created_at, updated_at "
                        + "FROM ai_conversation WHERE id = ?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> messages(int conversationId) {
        return jdbcTemplate.queryForList(
                "SELECT id, role, content, tool_calls, tool_results, citations, mode, model, created_at "
                        + "FROM ai_message WHERE conversation_id = ? ORDER BY id", conversationId);
    }

    public int append(int conversationId, String role, String content, String toolCallsJson,
                      String toolResultsJson, String citationsJson, String mode, String model) {
        jdbcTemplate.update(
                "INSERT INTO ai_message (conversation_id, role, content, tool_calls, tool_results, citations, mode, model) "
                        + "VALUES (?,?,?,?,?,?,?,?)",
                conversationId, role, content, toolCallsJson, toolResultsJson, citationsJson, mode, model);
        jdbcTemplate.update(
                "UPDATE ai_conversation SET message_count = message_count + 1, updated_at = NOW() WHERE id = ?",
                conversationId);
        Integer id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM ai_message", Integer.class);
        return id == null ? 0 : id;
    }

    /** 重建给模型的多轮上下文（只保留问答文本，工具调用不重复回放） */
    public List<LlmMessage> history(int conversationId, int maxTurns) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT role, content FROM ai_message WHERE conversation_id = ? "
                        + "AND role IN ('user','assistant') AND content IS NOT NULL AND content <> '' "
                        + "ORDER BY id DESC LIMIT ?", conversationId, Math.max(2, maxTurns * 2));
        List<LlmMessage> messages = new ArrayList<LlmMessage>();
        for (int i = rows.size() - 1; i >= 0; i--) {
            Map<String, Object> row = rows.get(i);
            String role = String.valueOf(row.get("role"));
            String content = String.valueOf(row.get("content"));
            if (content.length() > 2000) {
                content = content.substring(0, 2000) + "…";
            }
            messages.add(LlmMessage.of(role, content));
        }
        return messages;
    }

    /** 统计信息 */
    public Map<String, Object> stats() {
        Map<String, Object> stats = new LinkedHashMap<String, Object>();
        Integer conversations = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_conversation", Integer.class);
        Integer messages = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_message", Integer.class);
        stats.put("conversations", conversations == null ? 0 : conversations);
        stats.put("messages", messages == null ? 0 : messages);
        return stats;
    }
}
