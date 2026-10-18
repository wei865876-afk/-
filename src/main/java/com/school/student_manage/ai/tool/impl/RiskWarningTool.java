package com.school.student_manage.ai.tool.impl;

import com.school.student_manage.ai.tool.AiTool;
import com.school.student_manage.ai.tool.Schemas;
import com.school.student_manage.ai.tool.ToolResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学业预警规则引擎：把"成绩 + 学籍异动 + 数据完整性"三类信号规则化，
 * 输出可直接驱动干预动作的预警名单，是 AI 从"问答"走向"主动治理"的关键工具。
 */
@Component
public class RiskWarningTool implements AiTool {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public String name() {
        return "risk_warning";
    }

    @Override
    public String title() {
        return "学业风险预警";
    }

    @Override
    public String description() {
        return "识别需要关注的学生并给出风险等级与原因，规则包括：不及格门数、平均分偏低、待审学籍异动、成绩数据缺失。"
                + "适用于：哪些学生需要关注、学业预警名单、谁可能毕不了业、生成帮扶名单等。"
                + "示例：找出需要学业帮扶的学生 -> {}；只要高风险的 -> {level:'high'}。";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        Map<String, Object> props = new LinkedHashMap<String, Object>();
        props.put("level", Schemas.enumString("只返回指定风险等级", "high", "medium", "all"));
        props.put("className", Schemas.string("可选，限定班级"));
        props.put("limit", Schemas.integer("返回条数，默认 30"));
        return Schemas.object(props);
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        String level = text(args.get("level"));
        String className = text(args.get("className"));
        int limit = intValue(args.get("limit"), 30);
        if (limit <= 0 || limit > 200) {
            limit = 30;
        }

        String sql = "SELECT s.stu_id, s.stu_name, c.class_name, "
                + "COUNT(sc.score) AS records, AVG(sc.score) AS avg_score, "
                + "SUM(CASE WHEN sc.score < 60 THEN 1 ELSE 0 END) AS fail_count, "
                + "MAX(sc.score) AS max_score "
                + "FROM student s "
                + "LEFT JOIN \"class\" c ON s.class_id = c.class_id "
                + "LEFT JOIN score sc ON sc.stu_id = s.stu_id "
                + (className != null ? "WHERE c.class_name LIKE ? " : "")
                + "GROUP BY s.stu_id, s.stu_name, c.class_name "
                + "ORDER BY fail_count DESC, avg_score ASC NULLS LAST";
        List<Map<String, Object>> base = className != null
                ? jdbcTemplate.queryForList(sql, "%" + className + "%")
                : jdbcTemplate.queryForList(sql);

        // 待审学籍异动
        List<Map<String, Object>> pendingRows = jdbcTemplate.queryForList(
                "SELECT stu_id, change_type FROM change_apply WHERE status = '待审核'");
        Map<String, String> pendingMap = new LinkedHashMap<String, String>();
        for (Map<String, Object> row : pendingRows) {
            String id = String.valueOf(row.get("stu_id"));
            String type = String.valueOf(row.get("change_type"));
            pendingMap.put(id, pendingMap.containsKey(id) ? pendingMap.get(id) + "、" + type : type);
        }

        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        int high = 0;
        int medium = 0;
        int low = 0;
        int dataIssue = 0;
        int abnormal = 0;
        for (Map<String, Object> item : base) {
            String stuId = String.valueOf(item.get("stu_id"));
            int records = intValue(item.get("records"), 0);
            int failCount = intValue(item.get("fail_count"), 0);
            BigDecimal avg = item.get("avg_score") instanceof Number
                    ? new BigDecimal(item.get("avg_score").toString()).setScale(2, RoundingMode.HALF_UP)
                    : null;
            BigDecimal max = item.get("max_score") instanceof Number
                    ? new BigDecimal(item.get("max_score").toString())
                    : null;

            List<String> reasons = new ArrayList<String>();
            List<String> actions = new ArrayList<String>();
            String riskLevel = null;

            if (failCount >= 2) {
                riskLevel = "high";
                reasons.add("不及格 " + failCount + " 门");
                actions.add("安排一对一学业辅导并制定重修计划");
            } else if (failCount == 1) {
                riskLevel = "medium";
                reasons.add("有 1 门不及格");
                actions.add("提醒学生参加补考并跟踪复习情况");
            }
            if (avg != null && avg.compareTo(new BigDecimal("65")) < 0) {
                riskLevel = riskLevel == null ? "medium" : (riskLevel.equals("high") ? "high" : "medium");
                reasons.add("平均分偏低(" + avg.stripTrailingZeros().toPlainString() + ")");
                actions.add("与辅导员沟通学习方法，关注课堂出勤");
            }
            if (failCount == 0 && (avg == null || avg.compareTo(new BigDecimal("65")) >= 0)) {
                riskLevel = "low";
                reasons.add("无明显学业风险");
            }
            if (records == 0) {
                dataIssue++;
                reasons.add("缺少成绩记录");
                if (riskLevel == null || "low".equals(riskLevel)) {
                    riskLevel = "medium";
                    actions.add("核对成绩数据是否已录入");
                }
            }
            // 数据质量信号：超过 100 分属录入异常，会直接污染平均分与排名
            if (max != null && max.compareTo(new BigDecimal("100")) > 0) {
                abnormal++;
                reasons.add("成绩记录异常(最高 " + max.stripTrailingZeros().toPlainString() + " 分)");
                actions.add("核对成绩录入，修正超过 100 分的异常值");
                if (riskLevel == null || "low".equals(riskLevel)) {
                    riskLevel = "medium";
                }
            }
            String pending = pendingMap.get(stuId);
            if (pending != null) {
                reasons.add("存在待审学籍异动(" + pending + ")");
                actions.add("督促教务尽快审核学籍异动申请");
            }

            if (riskLevel == null) {
                riskLevel = "low";
            }
            if ("high".equals(riskLevel)) {
                high++;
            } else if ("medium".equals(riskLevel)) {
                medium++;
            } else {
                low++;
            }
            if (level != null && !"all".equals(level) && !riskLevel.equals(level)) {
                continue;
            }

            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("stuId", stuId);
            row.put("stuName", item.get("stu_name"));
            row.put("className", item.get("class_name") == null ? "未分班" : item.get("class_name"));
            row.put("avgScore", avg == null ? "-" : avg.stripTrailingZeros().toPlainString());
            row.put("failCount", failCount);
            row.put("riskLevel", riskLevel);
            row.put("reasons", String.join("；", reasons));
            row.put("suggestion", actions.isEmpty() ? "保持关注" : String.join("；", actions));
            rows.add(row);
        }
        if (rows.size() > limit) {
            rows = new ArrayList<Map<String, Object>>(rows.subList(0, limit));
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        List<Map<String, Object>> metrics = new ArrayList<Map<String, Object>>();
        metrics.add(metric("高风险", high, "人"));
        metrics.add(metric("中风险", medium, "人"));
        metrics.add(metric("低风险", low, "人"));
        metrics.add(metric("待审异动学生", pendingMap.size(), "人"));
        if (abnormal > 0) {
            metrics.add(metric("成绩异常学生", abnormal, "人"));
        }
        data.put("metrics", metrics);
        data.put("rows", rows);

        ToolResult result = ToolResult.of(name(), title(), "warning",
                "共扫描 " + base.size() + " 名学生：高风险 " + high + " 人、中风险 " + medium + " 人"
                        + (pendingMap.isEmpty() ? "" : "，另有 " + pendingMap.size() + " 人存在待审学籍异动"));
        result.setData(data);
        Map<String, Object> chart = new LinkedHashMap<String, Object>();
        chart.put("type", "pie");
        chart.put("xKey", "name");
        chart.put("unit", "人");
        chart.put("series", java.util.Collections.singletonList(series("value", "人数")));
        chart.put("staticData", Arrays.asList(
                named("高风险", high), named("中风险", medium), named("低风险", low)));
        result.setChart(chart);
        if (dataIssue > 0) {
            result.hints("有 " + dataIssue + " 名学生尚无成绩记录，预警结论需说明数据缺失情况");
        }
        if (abnormal > 0) {
            result.hints("有 " + abnormal + " 名学生存在超过 100 分的异常成绩，建议先核对录入数据再评估学业水平");
        }
        return result;
    }

    private Map<String, Object> named(String name, int value) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("name", name);
        item.put("value", value);
        return item;
    }

    private Map<String, Object> metric(String label, Object value, String unit) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("label", label);
        item.put("value", value);
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
