package com.school.student_manage.ai.tool.impl;

import com.school.student_manage.ai.tool.AiTool;
import com.school.student_manage.ai.tool.Schemas;
import com.school.student_manage.ai.tool.ToolResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 奖惩记录查询：奖学金、评优、处分等 */
@Component
public class RewardPunishTool implements AiTool {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public String name() {
        return "query_reward_punish";
    }

    @Override
    public String title() {
        return "奖惩记录查询";
    }

    @Override
    public String description() {
        return "查询奖励/处分记录，支持按学生或类型筛选，并输出数量分布。"
                + "示例：今年的奖学金名单 -> {type:'奖励'}；张三有没有处分 -> {keyword:'张三'}。";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        Map<String, Object> props = new LinkedHashMap<String, Object>();
        props.put("keyword", Schemas.string("学生姓名或学号"));
        props.put("type", Schemas.enumString("记录类型", "奖励", "处分"));
        props.put("limit", Schemas.integer("返回条数，默认 30"));
        return Schemas.object(props);
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        String keyword = text(args.get("keyword"));
        String type = text(args.get("type"));
        int limit = intValue(args.get("limit"), 30);
        if (limit <= 0 || limit > 200) {
            limit = 30;
        }
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> params = new ArrayList<Object>();
        if (keyword != null) {
            where.append(" AND (stu_name LIKE ? OR stu_id LIKE ?)");
            params.add("%" + keyword + "%");
            params.add("%" + keyword + "%");
        }
        if (type != null) {
            where.append(" AND TRIM(rp_type) = ?");
            params.add(type);
        }
        List<Map<String, Object>> raw = jdbcTemplate.queryForList(
                "SELECT rp_id, stu_id, stu_name, TRIM(rp_type) AS rp_type, level_name, amount, reason, rp_date "
                        + "FROM reward_punish" + where + " ORDER BY rp_date DESC NULLS LAST LIMIT " + limit,
                params.toArray());

        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> item : raw) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("stuId", item.get("stu_id"));
            row.put("stuName", item.get("stu_name"));
            row.put("type", item.get("rp_type"));
            row.put("levelName", item.get("level_name"));
            row.put("amount", item.get("amount"));
            row.put("reason", item.get("reason"));
            row.put("date", item.get("rp_date") == null ? "-" : item.get("rp_date").toString());
            rows.add(row);
        }
        List<Map<String, Object>> byType = jdbcTemplate.queryForList(
                "SELECT TRIM(rp_type) AS name, COUNT(*) AS value FROM reward_punish"
                        + (type == null ? "" : " WHERE TRIM(rp_type) = ?") + " GROUP BY 1",
                type == null ? new Object[]{} : new Object[]{type});

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("rows", rows);
        data.put("byType", byType);
        ToolResult result = ToolResult.table(name(), title(),
                rows.isEmpty() ? "未查询到符合条件的奖惩记录" : "共 " + rows.size() + " 条奖惩记录",
                Arrays.asList("stuId", "stuName", "type", "levelName", "amount", "reason", "date"), rows);
        result.setData(data);
        result.setRender(rows.isEmpty() ? "text" : "table");
        return result;
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private int intValue(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
