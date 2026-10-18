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

/** 学生信息查询（支持姓名/学号、班级、专业、院系、状态、性别组合筛选） */
@Component
public class StudentQueryTool implements AiTool {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public String name() {
        return "query_students";
    }

    @Override
    public String title() {
        return "学生信息查询";
    }

    @Override
    public String description() {
        return "按条件查询学生名单。适用：找某个学生、查某班级/专业/院系有哪些学生、统计某条件下的学生名单。"
                + "参数要么给 keyword（姓名或学号片段），要么给 className/major/dept/status/gender 做筛选。"
                + "示例：班里叫张三的同学 -> {keyword:'张三'}；计算机专业女生 -> {major:'计算机', gender:'女'}。";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        Map<String, Object> props = new LinkedHashMap<String, Object>();
        props.put("keyword", Schemas.string("姓名或学号关键字，支持模糊匹配"));
        props.put("className", Schemas.string("班级名称，如 计算24-1"));
        props.put("major", Schemas.string("专业名称，如 计算机"));
        props.put("dept", Schemas.string("院系名称，如 电子与信息"));
        props.put("status", Schemas.string("学籍状态，如 在读"));
        props.put("gender", Schemas.enumString("性别", "男", "女"));
        props.put("limit", Schemas.integer("返回条数上限，默认 50"));
        return Schemas.object(props);
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        StringBuilder sql = new StringBuilder(
                "SELECT s.stu_id, s.stu_name, COALESCE(NULLIF(TRIM(s.gender), ''), s.sex) AS gender, "
                        + "c.class_name, m.major_name, d.dept_name, s.status, s.entry_date "
                        + "FROM student s "
                        + "LEFT JOIN \"class\" c ON s.class_id = c.class_id "
                        + "LEFT JOIN major m ON c.major_id = m.major_id "
                        + "LEFT JOIN department d ON m.dept_id = d.dept_id WHERE 1 = 1");
        List<Object> params = new ArrayList<Object>();

        String keyword = text(args.get("keyword"));
        if (keyword != null) {
            sql.append(" AND (s.stu_name LIKE ? OR s.stu_id LIKE ?)");
            params.add("%" + keyword + "%");
            params.add("%" + keyword + "%");
        }
        String className = text(args.get("className"));
        if (className != null) {
            sql.append(" AND c.class_name LIKE ?");
            params.add("%" + className + "%");
        }
        String major = text(args.get("major"));
        if (major != null) {
            sql.append(" AND m.major_name LIKE ?");
            params.add("%" + major + "%");
        }
        String dept = text(args.get("dept"));
        if (dept != null) {
            sql.append(" AND d.dept_name LIKE ?");
            params.add("%" + dept + "%");
        }
        String status = text(args.get("status"));
        if (status != null) {
            sql.append(" AND s.status = ?");
            params.add(status);
        }
        String gender = text(args.get("gender"));
        if (gender != null) {
            sql.append(" AND COALESCE(NULLIF(TRIM(s.gender), ''), s.sex) = ?");
            params.add(gender);
        }
        int limit = intValue(args.get("limit"), 50);
        if (limit <= 0 || limit > 200) {
            limit = 50;
        }
        sql.append(" ORDER BY s.stu_id LIMIT ").append(limit);

        List<Map<String, Object>> raw = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> item : raw) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("stuId", item.get("stu_id"));
            row.put("stuName", item.get("stu_name"));
            row.put("gender", item.get("gender"));
            row.put("className", item.get("class_name") == null ? "-" : item.get("class_name"));
            row.put("major", item.get("major_name") == null ? "-" : item.get("major_name"));
            row.put("dept", item.get("dept_name") == null ? "-" : item.get("dept_name"));
            row.put("status", item.get("status"));
            row.put("entryDate", item.get("entry_date") == null ? "-" : item.get("entry_date").toString());
            rows.add(row);
        }

        String summary = rows.isEmpty()
                ? "未查询到符合条件的学生"
                : "命中 " + rows.size() + " 名学生" + (keyword != null ? "（关键字：" + keyword + "）" : "");
        ToolResult result = ToolResult.table(name(), title(), summary,
                Arrays.asList("stuId", "stuName", "gender", "className", "major", "dept", "status", "entryDate"), rows);
        if (rows.isEmpty()) {
            result.hints("可尝试放宽条件：去掉班级/专业限定，或仅用姓名关键字");
        } else if (rows.size() >= limit) {
            result.hints("结果已达到返回上限，可增加筛选条件缩小范围");
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
