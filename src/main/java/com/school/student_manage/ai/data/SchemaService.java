package com.school.student_manage.ai.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 数据库表结构自省服务：启动后从 information_schema 实时读取允许访问的表结构，
 * 注入到系统提示词中，避免"提示词与真实库结构漂移"。
 */
@Component
public class SchemaService {

    private static final Logger log = LoggerFactory.getLogger(SchemaService.class);

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private ReadOnlySqlExecutor readOnlySqlExecutor;

    private volatile String cachedSchema;
    private volatile long cachedAt;

    /** 供模型使用的建表说明文本 */
    public String schemaPrompt() {
        long now = System.currentTimeMillis();
        if (cachedSchema == null || now - cachedAt > 300_000L) {
            cachedSchema = build();
            cachedAt = now;
        }
        return cachedSchema;
    }

    /** 手动失效缓存（知识库 / 表结构变更后调用） */
    public void invalidate() {
        cachedSchema = null;
    }

    private String build() {
        StringBuilder sb = new StringBuilder();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT table_name, column_name, data_type, character_maximum_length "
                            + "FROM information_schema.columns WHERE table_schema = 'public' "
                            + "ORDER BY table_name, ordinal_position");
            String currentTable = null;
            for (Map<String, Object> row : rows) {
                String table = String.valueOf(row.get("table_name"));
                if (!readOnlySqlExecutor.allowedTables().contains(table.toLowerCase())) {
                    continue;
                }
                if (!table.equals(currentTable)) {
                    if (currentTable != null) {
                        sb.append('\n');
                    }
                    sb.append(table).append("(");
                    currentTable = table;
                } else {
                    sb.append(", ");
                }
                sb.append(row.get("column_name"));
            }
            if (currentTable != null) {
                sb.append(')');
            }
            // 视图
            List<String> views = new ArrayList<String>();
            for (String v : readOnlySqlExecutor.allowedTables()) {
                if (v.startsWith("v_")) {
                    views.add(v);
                }
            }
            if (!views.isEmpty()) {
                sb.append("\n视图：").append(String.join(", ", views));
            }
            // 关键口径说明
            String keys = jdbcTemplate.queryForList(
                    "SELECT string_agg(DISTINCT status, ',') st FROM student", String.class).stream().findFirst().orElse("");
            sb.append("\n口径：student.status 取值 [").append(keys).append("]（历史数据不统一，默认全部视为在校生）");
        } catch (Exception e) {
            log.warn("读取表结构失败：{}", e.getMessage());
            return "student, class, major, department, score, course, reward_punish, change_apply, student_change, archive";
        }
        return sb.toString();
    }
}
