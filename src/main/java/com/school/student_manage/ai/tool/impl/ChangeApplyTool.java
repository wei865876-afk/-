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

/** 学籍异动（转专业/休学/复学/退学等）申请查询与办理状态统计 */
@Component
public class ChangeApplyTool implements AiTool {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public String name() {
        return "query_change_apply";
    }

    @Override
    public String title() {
        return "学籍异动查询";
    }

    @Override
    public String description() {
        return "查询学籍异动申请（转专业、休学、复学、退学等）及其审核状态，并统计各状态数量。"
                + "示例：有多少待审核申请 -> {status:'待审核'}；张三的异动申请 -> {keyword:'张三'}。";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        Map<String, Object> props = new LinkedHashMap<String, Object>();
        props.put("keyword", Schemas.string("学生姓名或学号"));
        props.put("status", Schemas.string("审核状态，如 待审核 / 已通过 / 已驳回"));
        props.put("type", Schemas.string("异动类型，如 转专业 / 休学 / 复学"));
        props.put("limit", Schemas.integer("返回条数，默认 30"));
        return Schemas.object(props);
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        String keyword = text(args.get("keyword"));
        String status = text(args.get("status"));
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
        if (status != null) {
            where.append(" AND status = ?");
            params.add(status);
        }
        if (type != null) {
            where.append(" AND change_type LIKE ?");
            params.add("%" + type + "%");
        }
        List<Map<String, Object>> raw = jdbcTemplate.queryForList(
                "SELECT apply_id, stu_id, stu_name, change_type, remark, status, submit_time "
                        + "FROM change_apply" + where + " ORDER BY submit_time DESC NULLS LAST LIMIT " + limit,
                params.toArray());

        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> item : raw) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("applyId", item.get("apply_id"));
            row.put("stuId", item.get("stu_id"));
            row.put("stuName", item.get("stu_name"));
            row.put("changeType", item.get("change_type"));
            row.put("remark", item.get("remark"));
            row.put("status", item.get("status"));
            row.put("submitTime", item.get("submit_time") == null ? "-" : item.get("submit_time").toString());
            rows.add(row);
        }
        List<Map<String, Object>> byStatus = jdbcTemplate.queryForList(
                "SELECT COALESCE(status, '未知') AS name, COUNT(*) AS value FROM change_apply GROUP BY 1 ORDER BY 2 DESC");
        List<Map<String, Object>> byType = jdbcTemplate.queryForList(
                "SELECT change_type AS name, COUNT(*) AS value FROM change_apply GROUP BY 1 ORDER BY 2 DESC");

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("rows", rows);
        data.put("byStatus", byStatus);
        data.put("byType", byType);

        ToolResult result = ToolResult.table(name(), title(),
                rows.isEmpty() ? "未查询到符合条件的学籍异动申请" : "共 " + rows.size() + " 条学籍异动申请",
                Arrays.asList("applyId", "stuId", "stuName", "changeType", "remark", "status", "submitTime"), rows);
        result.setData(data);
        result.setRender(rows.isEmpty() ? "text" : "table");
        for (Map<String, Object> item : byStatus) {
            if ("待审核".equals(String.valueOf(item.get("name")))) {
                result.hints("存在 " + item.get("value") + " 条待审核申请，可提醒教务优先处理");
            }
        }
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
