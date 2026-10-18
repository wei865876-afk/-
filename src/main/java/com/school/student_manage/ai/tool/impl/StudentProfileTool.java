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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学生 360° 画像：学籍信息 + 成绩 + 奖惩 + 学籍异动 + 档案，
 * 是"AI 辅导员"场景的核心数据工具。
 */
@Component
public class StudentProfileTool implements AiTool {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public String name() {
        return "get_student_profile";
    }

    @Override
    public String title() {
        return "学生全景画像";
    }

    @Override
    public String description() {
        return "获取单个学生的完整档案：基本信息、班级/专业/院系、成绩明细与平均分、不及格课程、奖惩记录、学籍异动、档案材料。"
                + "当用户提到某个具体学生（姓名或学号）并想了解情况、给建议、做评价时调用。"
                + "示例：帮我看看张三的情况 -> {name:'张三'}；学号 2023001 的成绩怎么样 -> {stuId:'2023001'}。";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        Map<String, Object> props = new LinkedHashMap<String, Object>();
        props.put("stuId", Schemas.string("学号，精确匹配，优先级最高"));
        props.put("name", Schemas.string("学生姓名，支持模糊匹配"));
        return Schemas.object(props);
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        String stuId = text(args.get("stuId"));
        String name = text(args.get("name"));
        if (stuId == null && name == null) {
            return ToolResult.fail(name(), title(), "需要提供学号或姓名");
        }

        List<Map<String, Object>> candidates;
        if (stuId != null) {
            candidates = jdbcTemplate.queryForList(
                    "SELECT s.stu_id, s.stu_name, s.status, c.class_name, m.major_name, d.dept_name, c.teacher, "
                            + "COALESCE(NULLIF(TRIM(s.gender), ''), s.sex) AS gender, s.birth, s.phone, s.entry_date "
                            + "FROM student s LEFT JOIN \"class\" c ON s.class_id = c.class_id "
                            + "LEFT JOIN major m ON c.major_id = m.major_id "
                            + "LEFT JOIN department d ON m.dept_id = d.dept_id WHERE s.stu_id = ?", stuId);
        } else {
            candidates = jdbcTemplate.queryForList(
                    "SELECT s.stu_id, s.stu_name, s.status, c.class_name, m.major_name, d.dept_name, c.teacher, "
                            + "COALESCE(NULLIF(TRIM(s.gender), ''), s.sex) AS gender, s.birth, s.phone, s.entry_date "
                            + "FROM student s LEFT JOIN \"class\" c ON s.class_id = c.class_id "
                            + "LEFT JOIN major m ON c.major_id = m.major_id "
                            + "LEFT JOIN department d ON m.dept_id = d.dept_id "
                            + "WHERE s.stu_name LIKE ? ORDER BY s.stu_id LIMIT 6", "%" + name + "%");
        }
        if (candidates.isEmpty()) {
            return ToolResult.fail(name(), title(), "未找到该学生，请确认姓名或学号");
        }
        if (candidates.size() > 1) {
            List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
            for (Map<String, Object> item : candidates) {
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                row.put("stuId", item.get("stu_id"));
                row.put("stuName", item.get("stu_name"));
                row.put("className", item.get("class_name"));
                row.put("major", item.get("major_name"));
                row.put("status", item.get("status"));
                rows.add(row);
            }
            ToolResult multiple = ToolResult.table(name(), title(),
                    "匹配到 " + rows.size() + " 名学生，请让用户确认学号",
                    java.util.Arrays.asList("stuId", "stuName", "className", "major", "status"), rows);
            multiple.hints("向用户确认具体是哪一位（提供学号）后再拉取完整画像");
            return multiple;
        }

        Map<String, Object> student = candidates.get(0);
        String id = String.valueOf(student.get("stu_id"));

        List<Map<String, Object>> scores = jdbcTemplate.queryForList(
                "SELECT COALESCE(NULLIF(course_name, ''), '未命名课程') AS course_name, score, school_year "
                        + "FROM score WHERE stu_id = ? ORDER BY score DESC", id);

        BigDecimal avg = null;
        BigDecimal sum = BigDecimal.ZERO;
        int failing = 0;
        BigDecimal highest = null;
        BigDecimal lowest = null;
        for (Map<String, Object> item : scores) {
            Object raw = item.get("score");
            if (!(raw instanceof Number)) {
                continue;
            }
            BigDecimal value = new BigDecimal(raw.toString());
            sum = sum.add(value);
            if (highest == null || value.compareTo(highest) > 0) {
                highest = value;
            }
            if (lowest == null || value.compareTo(lowest) < 0) {
                lowest = value;
            }
            if (value.compareTo(new BigDecimal("60")) < 0) {
                failing++;
            }
        }
        if (!scores.isEmpty()) {
            avg = sum.divide(new BigDecimal(scores.size()), 2, RoundingMode.HALF_UP);
        }

        // 统一把时间字段转成字符串：日期对象直接序列化会变成时间戳，影响阅读与模型理解
        List<Map<String, Object>> scoreRows = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> item : scores) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("course_name", item.get("course_name"));
            row.put("score", item.get("score"));
            row.put("school_year", item.get("school_year") == null ? "-" : String.valueOf(item.get("school_year")));
            scoreRows.add(row);
        }

        List<Map<String, Object>> rewards = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> item : jdbcTemplate.queryForList(
                "SELECT rp_type, level_name, amount, reason, rp_date FROM reward_punish WHERE stu_id = ? "
                        + "ORDER BY rp_date DESC LIMIT 10", id)) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("rp_type", item.get("rp_type"));
            row.put("level_name", item.get("level_name"));
            row.put("amount", item.get("amount"));
            row.put("reason", item.get("reason"));
            row.put("rp_date", item.get("rp_date") == null ? "-" : item.get("rp_date").toString());
            rewards.add(row);
        }

        List<Map<String, Object>> changes = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> item : jdbcTemplate.queryForList(
                "SELECT change_type, remark, status, submit_time FROM change_apply WHERE stu_id = ? "
                        + "ORDER BY submit_time DESC LIMIT 10", id)) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("change_type", item.get("change_type"));
            row.put("remark", item.get("remark"));
            row.put("status", item.get("status"));
            row.put("submit_time", item.get("submit_time") == null ? "-" : item.get("submit_time").toString());
            changes.add(row);
        }

        Integer archiveCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM archive WHERE stu_id = ?", Integer.class, id);

        Map<String, Object> basic = new LinkedHashMap<String, Object>();
        basic.put("stuId", id);
        basic.put("stuName", student.get("stu_name"));
        basic.put("gender", student.get("gender"));
        basic.put("status", student.get("status"));
        basic.put("className", student.get("class_name"));
        basic.put("major", student.get("major_name"));
        basic.put("dept", student.get("dept_name"));
        basic.put("advisor", student.get("teacher"));
        basic.put("birth", student.get("birth") == null ? null : student.get("birth").toString());
        basic.put("phone", student.get("phone"));
        basic.put("entryDate", student.get("entry_date") == null ? null : student.get("entry_date").toString());

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("basic", basic);
        data.put("scoreSummary", metrics(avg, highest, lowest, scores.size(), failing));
        data.put("scores", scoreRows);
        data.put("rewards", rewards);
        data.put("changes", changes);
        data.put("archiveCount", archiveCount == null ? 0 : archiveCount);

        StringBuilder summary = new StringBuilder();
        summary.append(student.get("stu_name")).append("（").append(id).append("）");
        summary.append("，").append(student.get("class_name") == null ? "未分班" : student.get("class_name"));
        summary.append("，共 ").append(scores.size()).append(" 条成绩");
        if (avg != null) {
            summary.append("，平均分 ").append(avg);
        }
        if (failing > 0) {
            summary.append("，不及格 ").append(failing).append(" 门");
        }

        ToolResult result = ToolResult.of(name(), title(), "profile", summary.toString());
        result.setData(data);
        if (failing > 0) {
            result.hints("该生存在不及格课程，建议生成学业帮扶建议并关注其学籍异动");
        }
        if (scores.isEmpty()) {
            result.hints("该生暂无成绩数据，画像结论需说明数据缺失");
        }
        return result;
    }

    private List<Map<String, Object>> metrics(BigDecimal avg, BigDecimal highest, BigDecimal lowest,
                                              int count, int failing) {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        list.add(metric("平均分", avg == null ? "-" : avg.toPlainString(), "分"));
        list.add(metric("最高分", highest == null ? "-" : highest.toPlainString(), "分"));
        list.add(metric("最低分", lowest == null ? "-" : lowest.toPlainString(), "分"));
        list.add(metric("成绩条数", String.valueOf(count), "条"));
        list.add(metric("不及格门数", String.valueOf(failing), "门"));
        return list;
    }

    private Map<String, Object> metric(String label, String value, String unit) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("label", label);
        item.put("value", value);
        item.put("unit", unit);
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
