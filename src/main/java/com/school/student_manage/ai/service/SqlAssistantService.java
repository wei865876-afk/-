package com.school.student_manage.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.student_manage.ai.agent.AgentEngine;
import com.school.student_manage.ai.data.ReadOnlySqlExecutor;
import com.school.student_manage.ai.data.SchemaService;
import com.school.student_manage.ai.llm.LlmClientRouter;
import com.school.student_manage.ai.llm.LlmMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 自然语言取数（NL2SQL 单步演示）：
 * 自然语言 → 大模型生成 PostgreSQL 只读 SQL → 安全校验 → 执行 → 返回 SQL + 结果集。
 * 相比 Agent 问答，这里把"生成的 SQL"作为一等公民展示，便于核对统计口径。
 */
@Service
public class SqlAssistantService {

    private static final Logger log = LoggerFactory.getLogger(SqlAssistantService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private AgentEngine engine;

    @Resource
    private SchemaService schemaService;

    @Resource
    private ReadOnlySqlExecutor executor;

    @Resource
    private LlmClientRouter router;

    public Map<String, Object> ask(String question) {
        if (question == null || question.trim().isEmpty()) {
            throw new IllegalArgumentException("请输入数据需求");
        }
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        String sql = null;
        String explanation = null;
        String mode = router.activeMode();

        if ("remote".equals(mode)) {
            String prompt = "你是 PostgreSQL 数据分析专家。根据下面的库表结构，把用户的数据需求翻译成一条只读 SELECT 查询。\n\n"
                    + "库表结构：\n" + schemaService.schemaPrompt() + "\n\n"
                    + "约束：\n"
                    + "1. 只允许生成单条 SELECT（可用 WITH），严禁任何写操作、DDL、系统表；\n"
                    + "2. 需要 JOIN 时使用 LEFT JOIN，表名 class 必须加双引号；\n"
                    + "3. 结果列请使用中文别名，便于阅读；\n"
                    + "4. 结果行数不超过 100 行（必要时加 LIMIT）；\n"
                    + "5. 只输出 JSON：{\"sql\":\"...\",\"explanation\":\"统计口径说明\"}\n\n"
                    + "用户需求：" + question.trim();
            try {
                var result = engine.chatOnce(java.util.Collections.singletonList(LlmMessage.user(prompt)));
                JsonNode node = parseJson(result.getContent());
                sql = node.path("sql").asText(null);
                explanation = node.path("explanation").asText("");
            } catch (Exception e) {
                log.warn("NL2SQL 生成失败：{}", e.getMessage());
                mode = "local";
            }
        }
        if (sql == null || sql.trim().isEmpty()) {
            mode = "local";
            LocalSql local = LocalSql.match(question);
            if (local == null) {
                response.put("mode", mode);
                response.put("success", false);
                response.put("message", "本地规则引擎未能识别该数据需求，请描述得更具体（例如\"统计各院系学生人数\"），"
                        + "或配置大模型密钥以获得自由取数能力。");
                return response;
            }
            sql = local.sql;
            explanation = local.explanation;
        }

        String validationError = executor.validate(sql);
        if (validationError != null) {
            response.put("mode", mode);
            response.put("success", false);
            response.put("sql", sql);
            response.put("message", "生成的 SQL 未通过安全校验：" + validationError);
            return response;
        }

        Map<String, Object> data = executor.execute(sql);
        response.put("mode", mode);
        response.put("success", true);
        response.put("question", question);
        response.put("sql", data.get("sql"));
        response.put("explanation", explanation == null ? "" : explanation);
        response.put("columns", data.get("columns"));
        response.put("rows", data.get("rows"));
        response.put("total", data.get("rowCount"));
        response.put("truncated", data.get("truncated"));
        response.put("elapsedMs", data.get("elapsedMs"));
        return response;
    }

    private JsonNode parseJson(String text) {
        if (text == null) {
            return MAPPER.createObjectNode();
        }
        String cleaned = text.trim();
        if (cleaned.startsWith("```")) {
            int first = cleaned.indexOf('\n');
            int last = cleaned.lastIndexOf("```");
            if (first > 0 && last > first) {
                cleaned = cleaned.substring(first + 1, last).trim();
            }
        }
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return MAPPER.createObjectNode();
        }
        try {
            return MAPPER.readTree(cleaned.substring(start, end + 1));
        } catch (Exception e) {
            return MAPPER.createObjectNode();
        }
    }

    /** 本地兜底的常用取数模板（无大模型时仍可体验"自然语言取数"） */
    private static final class LocalSql {

        private final String sql;
        private final String explanation;

        private LocalSql(String sql, String explanation) {
            this.sql = sql;
            this.explanation = explanation;
        }

        private static LocalSql match(String question) {
            String q = question.replace(" ", "");
            String from = " FROM student s LEFT JOIN \"class\" c ON s.class_id = c.class_id "
                    + "LEFT JOIN major m ON c.major_id = m.major_id "
                    + "LEFT JOIN department d ON m.dept_id = d.dept_id ";
            if (q.contains("院系") && (q.contains("人数") || q.contains("学生数") || q.contains("多少"))) {
                if (q.contains("女")) {
                    return new LocalSql("SELECT COALESCE(d.dept_name,'未分配') AS \"院系\", COUNT(*) AS \"女生人数\""
                            + from + " WHERE COALESCE(NULLIF(TRIM(s.gender),''), s.sex) = '女' "
                            + "GROUP BY 1 ORDER BY 2 DESC", "按院系统计女生人数");
                }
                return new LocalSql("SELECT COALESCE(d.dept_name,'未分配') AS \"院系\", COUNT(*) AS \"学生人数\""
                        + from + " GROUP BY 1 ORDER BY 2 DESC", "按院系统计学生人数");
            }
            if (q.contains("班级") && (q.contains("人数") || q.contains("多少"))) {
                return new LocalSql("SELECT COALESCE(c.class_name,'未分班') AS \"班级\", COUNT(*) AS \"人数\""
                        + from + " GROUP BY 1 ORDER BY 2 DESC", "按班级统计人数");
            }
            if ((q.contains("班级") || q.contains("各班")) && (q.contains("平均分") || q.contains("成绩"))) {
                return new LocalSql("SELECT COALESCE(c.class_name,'未分班') AS \"班级\", "
                        + "ROUND(AVG(sc.score),2) AS \"平均分\", "
                        + "ROUND(SUM(CASE WHEN sc.score >= 60 THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 2) AS \"及格率\" "
                        + "FROM score sc LEFT JOIN student s ON sc.stu_id = s.stu_id "
                        + "LEFT JOIN \"class\" c ON s.class_id = c.class_id "
                        + "GROUP BY 1 ORDER BY 2 DESC NULLS LAST", "按班级统计平均分与及格率");
            }
            if (q.contains("课程") && (q.contains("平均分") || q.contains("成绩"))) {
                return new LocalSql("SELECT COALESCE(NULLIF(course_name,''),'未命名') AS \"课程\", "
                        + "ROUND(AVG(score),2) AS \"平均分\", COUNT(*) AS \"参考人次\" "
                        + "FROM score GROUP BY 1 ORDER BY 2 DESC NULLS LAST", "按课程统计平均分");
            }
            if (q.contains("性别") || q.contains("男女") || q.contains("男女比例")) {
                return new LocalSql("SELECT COALESCE(NULLIF(TRIM(s.gender),''), s.sex, '未填写') AS \"性别\", "
                        + "COUNT(*) AS \"人数\"" + from + " GROUP BY 1 ORDER BY 2 DESC", "统计学生性别分布");
            }
            if (q.contains("专业") && (q.contains("人数") || q.contains("多少"))) {
                return new LocalSql("SELECT COALESCE(m.major_name,'未分配') AS \"专业\", COUNT(*) AS \"人数\""
                        + from + " GROUP BY 1 ORDER BY 2 DESC", "按专业统计人数");
            }
            if (q.contains("奖学金") || q.contains("奖励")) {
                return new LocalSql("SELECT stu_id AS \"学号\", stu_name AS \"姓名\", level_name AS \"等级\", "
                        + "amount AS \"金额\", rp_date AS \"日期\" FROM reward_punish "
                        + "WHERE TRIM(rp_type) = '奖励' ORDER BY rp_date DESC", "查询奖励发放明细");
            }
            if (q.contains("待审核") || q.contains("未审核")) {
                return new LocalSql("SELECT apply_id AS \"申请号\", stu_id AS \"学号\", stu_name AS \"姓名\", "
                        + "change_type AS \"异动类型\", submit_time AS \"提交时间\" FROM change_apply "
                        + "WHERE status = '待审核' ORDER BY submit_time DESC", "查询待审核学籍异动申请");
            }
            return null;
        }
    }
}
