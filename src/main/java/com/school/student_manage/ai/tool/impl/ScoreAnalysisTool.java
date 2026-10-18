package com.school.student_manage.ai.tool.impl;

import com.school.student_manage.ai.tool.AiTool;
import com.school.student_manage.ai.tool.Schemas;
import com.school.student_manage.ai.tool.ToolResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 成绩分析：支持按班级 / 课程 / 学生 / 院系 / 整体多维聚合，
 * 输出平均分、及格率、优秀率、最高最低分及排名，并自带数据质量提示。
 */
@Component
public class ScoreAnalysisTool implements AiTool {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public String name() {
        return "analyze_scores";
    }

    @Override
    public String title() {
        return "成绩多维分析";
    }

    @Override
    public String description() {
        return "对成绩做统计分析，支持维度：class(按班级) / course(按课程) / student(按学生排名) / dept(按院系) / overall(整体)。"
                + "指标包含平均分、及格率、优秀率(>=85)、最高最低分、记录数。"
                + "示例：各班平均分对比 -> {dimension:'class'}；高数这门课情况 -> {dimension:'course',target:'高数'}；"
                + "成绩排名前十 -> {dimension:'student',topN:10}。";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        Map<String, Object> props = new LinkedHashMap<String, Object>();
        props.put("dimension", Schemas.enumString("统计维度",
                "class", "course", "student", "dept", "overall"));
        props.put("target", Schemas.string("可选，维度对应的筛选关键字，如班级名、课程名、学生姓名"));
        props.put("topN", Schemas.integer("返回条数，默认 20"));
        return Schemas.object(props, "dimension");
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        String dimension = text(args.get("dimension"));
        if (dimension == null) {
            dimension = "overall";
        }
        String target = text(args.get("target"));
        int topN = intValue(args.get("topN"), 20);
        if (topN <= 0 || topN > 100) {
            topN = 20;
        }

        String from = " FROM score sc "
                + "LEFT JOIN student s ON sc.stu_id = s.stu_id "
                + "LEFT JOIN \"class\" c ON s.class_id = c.class_id "
                + "LEFT JOIN major m ON c.major_id = m.major_id "
                + "LEFT JOIN department d ON m.dept_id = d.dept_id ";
        String metricSql = " AVG(sc.score) AS avg_score, "
                + "SUM(CASE WHEN sc.score >= 60 THEN 1 ELSE 0 END) * 100.0 / COUNT(*) AS pass_rate, "
                + "SUM(CASE WHEN sc.score >= 85 THEN 1 ELSE 0 END) * 100.0 / COUNT(*) AS excellent_rate, "
                + "MAX(sc.score) AS max_score, MIN(sc.score) AS min_score, COUNT(*) AS records ";

        if ("overall".equals(dimension)) {
            Map<String, Object> row = jdbcTemplate.queryForMap("SELECT" + metricSql + from);
            List<Map<String, Object>> metrics = new ArrayList<Map<String, Object>>();
            metrics.add(metric("成绩记录", row.get("records"), "条"));
            metrics.add(metric("平均分", round(row.get("avg_score")), "分"));
            metrics.add(metric("及格率", round(row.get("pass_rate")), "%"));
            metrics.add(metric("优秀率", round(row.get("excellent_rate")), "%"));
            metrics.add(metric("最高分", row.get("max_score"), "分"));
            metrics.add(metric("最低分", row.get("min_score"), "分"));
            ToolResult result = ToolResult.of(name(), title(), "metrics",
                    "全校成绩：平均分 " + round(row.get("avg_score")) + "，及格率 " + round(row.get("pass_rate")) + "%");
            result.setData(metrics);
            appendQualityHint(result, row.get("max_score"));
            return result;
        }

        String groupKey;
        String label;
        String where = "";
        List<Object> params = new ArrayList<Object>();
        switch (dimension) {
            case "class":
                groupKey = "COALESCE(c.class_name, '未分班')";
                label = "className";
                break;
            case "course":
                groupKey = "COALESCE(NULLIF(sc.course_name, ''), '未命名课程')";
                label = "courseName";
                break;
            case "dept":
                groupKey = "COALESCE(d.dept_name, '未分配院系')";
                label = "deptName";
                break;
            case "student":
                groupKey = "s.stu_id || ' ' || COALESCE(s.stu_name, '')";
                label = "student";
                break;
            default:
                return ToolResult.fail(name(), title(), "不支持的统计维度：" + dimension);
        }
        if (target != null) {
            if ("class".equals(dimension)) {
                where = " WHERE c.class_name LIKE ?";
            } else if ("course".equals(dimension)) {
                where = " WHERE sc.course_name LIKE ?";
            } else if ("dept".equals(dimension)) {
                where = " WHERE d.dept_name LIKE ?";
            } else {
                where = " WHERE (s.stu_name LIKE ? OR s.stu_id LIKE ?)";
                params.add("%" + target + "%");
            }
            params.add("%" + target + "%");
        }

        List<Map<String, Object>> raw = jdbcTemplate.queryForList(
                "SELECT " + groupKey + " AS name," + metricSql + from + where
                        + " GROUP BY 1 ORDER BY avg_score DESC NULLS LAST LIMIT " + topN, params.toArray());

        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> item : raw) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("name", item.get("name"));
            row.put("avgScore", round(item.get("avg_score")));
            row.put("passRate", round(item.get("pass_rate")));
            row.put("excellentRate", round(item.get("excellent_rate")));
            row.put("maxScore", item.get("max_score"));
            row.put("minScore", item.get("min_score"));
            row.put("records", item.get("records"));
            rows.add(row);
        }

        String dimensionName = dimensionName(dimension);
        ToolResult result = ToolResult.table(name(), title(),
                rows.isEmpty() ? dimensionName + "：暂无成绩数据"
                        : dimensionName + "共 " + rows.size() + " 组，最高平均分："
                        + (rows.get(0).get("name") + " " + rows.get(0).get("avgScore") + " 分"),
                Arrays.asList("name", "avgScore", "passRate", "excellentRate", "maxScore", "minScore", "records"),
                rows);
        Map<String, Object> chart = new LinkedHashMap<String, Object>();
        chart.put("type", "bar");
        chart.put("xKey", "name");
        chart.put("unit", "分");
        chart.put("series", Arrays.asList(series("avgScore", "平均分"), series("passRate", "%", "及格率")));
        chart.put("labelKey", label);
        result.setChart(chart);
        if (!raw.isEmpty()) {
            appendQualityHint(result, raw.get(0).get("max_score"));
        }
        return result;
    }

    private void appendQualityHint(ToolResult result, Object maxScore) {
        if (maxScore instanceof Number) {
            BigDecimal max = new BigDecimal(maxScore.toString());
            if (max.compareTo(new BigDecimal("100")) > 0) {
                result.hints("检测到超过 100 分的成绩记录（最高 " + max.stripTrailingZeros().toPlainString()
                        + "），属于数据质量问题，分析时需向用户说明");
            }
        }
    }

    private String dimensionName(String dimension) {
        switch (dimension) {
            case "class":
                return "班级维度";
            case "course":
                return "课程维度";
            case "dept":
                return "院系维度";
            case "student":
                return "学生维度";
            default:
                return "整体";
        }
    }

    private Object round(Object value) {
        if (value instanceof Number) {
            return new BigDecimal(value.toString()).setScale(2, java.math.RoundingMode.HALF_UP)
                    .stripTrailingZeros().toPlainString();
        }
        return value;
    }

    private Map<String, Object> metric(String label, Object value, String unit) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("label", label);
        item.put("value", value == null ? 0 : value);
        item.put("unit", unit);
        return item;
    }

    private Map<String, Object> series(String key, String name) {
        return series(key, name, null);
    }

    private Map<String, Object> series(String key, String name, String unit) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("key", key);
        item.put("name", name);
        if (unit != null) {
            item.put("unit", unit);
        }
        return item;
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
