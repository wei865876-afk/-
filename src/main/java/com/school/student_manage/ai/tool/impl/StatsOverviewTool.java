package com.school.student_manage.ai.tool.impl;

import com.school.student_manage.ai.tool.AiTool;
import com.school.student_manage.ai.tool.Schemas;
import com.school.student_manage.ai.tool.ToolResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 全校学籍总览：规模、结构、分布，用于"学校/学院整体情况"类问题与数据看板 */
@Component
public class StatsOverviewTool implements AiTool {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public String name() {
        return "stats_overview";
    }

    @Override
    public String title() {
        return "学籍总览统计";
    }

    @Override
    public String description() {
        return "获取全校（或某院系/专业）的规模与结构统计：学生总数、班级/专业/院系数、性别分布、院系分布、班级规模、成绩记录量。"
                + "适用于：有多少学生、各院系人数、男女比例、学校整体情况等宏观问题。";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        Map<String, Object> props = new LinkedHashMap<String, Object>();
        props.put("dept", Schemas.string("可选，限定院系名称关键字"));
        props.put("major", Schemas.string("可选，限定专业名称关键字"));
        return Schemas.object(props);
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> params = new ArrayList<Object>();
        String dept = text(args.get("dept"));
        if (dept != null) {
            where.append(" AND d.dept_name LIKE ?");
            params.add("%" + dept + "%");
        }
        String major = text(args.get("major"));
        if (major != null) {
            where.append(" AND m.major_name LIKE ?");
            params.add("%" + major + "%");
        }
        String scope = "全校";
        if (dept != null) {
            scope = "院系[" + dept + "]";
        } else if (major != null) {
            scope = "专业[" + major + "]";
        }

        String baseFrom = " FROM student s "
                + "LEFT JOIN \"class\" c ON s.class_id = c.class_id "
                + "LEFT JOIN major m ON c.major_id = m.major_id "
                + "LEFT JOIN department d ON m.dept_id = d.dept_id";

        Map<String, Object> totals = jdbcTemplate.queryForMap(
                "SELECT COUNT(*) AS student_count, COUNT(DISTINCT s.class_id) AS class_count, "
                        + "COUNT(DISTINCT c.major_id) AS major_count, COUNT(DISTINCT m.dept_id) AS dept_count"
                        + baseFrom + where, params.toArray());

        Integer courseCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM course", Integer.class);
        Integer scoreCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM score", Integer.class);
        Integer rewardCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reward_punish", Integer.class);
        Integer pendingChange = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM change_apply WHERE status = '待审核'", Integer.class);

        List<Map<String, Object>> byDept = named("SELECT COALESCE(d.dept_name, '未分配院系') AS name, COUNT(*) AS value"
                + baseFrom + where + " GROUP BY 1 ORDER BY value DESC", params);
        List<Map<String, Object>> byMajor = named("SELECT COALESCE(m.major_name, '未分配专业') AS name, COUNT(*) AS value"
                + baseFrom + where + " GROUP BY 1 ORDER BY value DESC", params);
        List<Map<String, Object>> byClass = named("SELECT COALESCE(c.class_name, '未分班') AS name, COUNT(*) AS value"
                + baseFrom + where + " GROUP BY 1 ORDER BY value DESC", params);
        List<Map<String, Object>> byGender = named("SELECT COALESCE(NULLIF(TRIM(s.gender), ''), s.sex, '未填写') AS name, COUNT(*) AS value"
                + baseFrom + where + " GROUP BY 1 ORDER BY value DESC", params);
        List<Map<String, Object>> byStatus = named("SELECT COALESCE(s.status, '未填写') AS name, COUNT(*) AS value"
                + baseFrom + where + " GROUP BY 1 ORDER BY value DESC", params);

        List<Map<String, Object>> metrics = new ArrayList<Map<String, Object>>();
        metrics.add(metric("学生总数", totals.get("student_count"), "人"));
        metrics.add(metric("班级数量", totals.get("class_count"), "个"));
        metrics.add(metric("专业数量", totals.get("major_count"), "个"));
        metrics.add(metric("院系数量", totals.get("dept_count"), "个"));
        metrics.add(metric("课程数量", courseCount, "门"));
        metrics.add(metric("成绩记录", scoreCount, "条"));
        metrics.add(metric("奖惩记录", rewardCount, "条"));
        metrics.add(metric("待审异动", pendingChange, "条"));

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("scope", scope);
        data.put("metrics", metrics);
        data.put("byDept", byDept);
        data.put("byMajor", byMajor);
        data.put("byClass", byClass);
        data.put("byGender", byGender);
        data.put("byStatus", byStatus);

        Map<String, Object> chart = new LinkedHashMap<String, Object>();
        chart.put("type", "bar");
        chart.put("xKey", "name");
        chart.put("unit", "人");
        chart.put("series", java.util.Collections.singletonList(series("value", "学生数")));

        ToolResult result = ToolResult.of(name(), title(), "metrics",
                scope + "共 " + totals.get("student_count") + " 名学生，"
                        + totals.get("class_count") + " 个班级，"
                        + totals.get("major_count") + " 个专业");
        result.setData(data);
        result.setChart(chart);
        if (pendingChange != null && pendingChange > 0) {
            result.hints("存在 " + pendingChange + " 条待审核学籍异动，建议提醒教务处理");
        }
        return result;
    }

    private List<Map<String, Object>> named(String sql, List<Object> params) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("name", row.get("name"));
            item.put("value", row.get("value"));
            list.add(item);
        }
        return list;
    }

    private Map<String, Object> metric(String label, Object value, String unit) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("label", label);
        item.put("value", value == null ? 0 : value);
        item.put("unit", unit);
        return item;
    }

    private Map<String, Object> series(String key, String name) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("key", key);
        item.put("name", name);
        return item;
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }
}
