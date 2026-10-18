package com.school.student_manage.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.student_manage.ai.agent.AgentEngine;
import com.school.student_manage.ai.config.AiProperties;
import com.school.student_manage.ai.llm.LlmClientRouter;
import com.school.student_manage.ai.llm.LlmMessage;
import com.school.student_manage.ai.prompt.Prompts;
import com.school.student_manage.ai.tool.JsonSupport;
import com.school.student_manage.ai.tool.ToolRegistry;
import com.school.student_manage.ai.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能洞察与干预建议服务。
 *
 * <p>与"问答"不同，这里体现 AI 的主动性：系统先自己跑一遍数据分析工具，
 * 再把结构化结果交给模型生成管理洞察；无模型时用规则模板兜底，保证始终有产出。</p>
 */
@Service
public class InsightService {

    private static final Logger log = LoggerFactory.getLogger(InsightService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private ToolRegistry registry;

    @Resource
    private AgentEngine engine;

    @Resource
    private LlmClientRouter router;

    @Resource
    private AiProperties props;

    @Resource
    private JdbcTemplate jdbcTemplate;

    /** 看板智能洞察 */
    public Map<String, Object> dashboardInsight() {
        Map<String, Object> facts = new LinkedHashMap<String, Object>();

        ToolResult stats = registry.execute("stats_overview", new LinkedHashMap<String, Object>());
        ToolResult scores = registry.execute("analyze_scores", args("dimension", "class"));
        ToolResult risk = registry.execute("risk_warning", args("level", "all", "limit", 50));

        if (stats.isSuccess() && stats.getData() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) stats.getData();
            facts.put("metrics", data.get("metrics"));
            facts.put("byDept", data.get("byDept"));
            facts.put("byGender", data.get("byGender"));
        }
        if (scores.isSuccess() && scores.getData() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) scores.getData();
            facts.put("classScores", data.get("rows"));
        }
        if (risk.isSuccess() && risk.getData() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) risk.getData();
            facts.put("riskMetrics", data.get("metrics"));
            facts.put("riskRows", data.get("rows"));
        }

        // 数据质量：成绩覆盖率
        Integer studentTotal = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM student", Integer.class);
        Integer withScore = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT stu_id) FROM score", Integer.class);
        Integer pending = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM change_apply WHERE status = '待审核'", Integer.class);
        Integer overHundred = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM score WHERE score > 100", Integer.class);
        facts.put("studentTotal", studentTotal);
        facts.put("studentsWithScore", withScore);
        facts.put("pendingChange", pending);
        facts.put("abnormalScore", overHundred);

        List<Map<String, Object>> points;
        String overall;
        String mode = router.activeMode();
        if ("remote".equals(mode)) {
            try {
                String prompt = Prompts.insight(props, JsonSupport.toJson(facts));
                var result = engine.chatOnce(java.util.Collections.singletonList(LlmMessage.user(prompt)));
                JsonNode node = parseJson(result.getContent());
                points = toPoints(node.path("points"));
                overall = node.path("summary").asText("");
                if (points.isEmpty()) {
                    points = ruleBasedPoints(facts);
                    overall = ruleBasedOverall(facts);
                }
            } catch (Exception e) {
                log.warn("生成洞察失败，使用规则兜底：{}", e.getMessage());
                mode = "local";
                points = ruleBasedPoints(facts);
                overall = ruleBasedOverall(facts);
            }
        } else {
            points = ruleBasedPoints(facts);
            overall = ruleBasedOverall(facts);
        }

        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("mode", mode);
        response.put("model", router.describe());
        response.put("overall", overall);
        response.put("points", points);
        response.put("facts", facts);
        response.put("generatedAt", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()));
        return response;
    }

    /** 学业预警干预建议 */
    public Map<String, Object> warningAdvice(String level, Integer limit) {
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        args.put("level", level == null ? "all" : level);
        args.put("limit", limit == null ? 20 : limit);
        ToolResult risk = registry.execute("risk_warning", args);

        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        if (risk.getData() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) risk.getData();
            Object raw = data.get("rows");
            if (raw instanceof List) {
                for (Object item : (List<?>) raw) {
                    if (item instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> map = (Map<String, Object>) item;
                        rows.add(map);
                    }
                }
            }
        }

        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        String overall = "";
        String mode = router.activeMode();
        boolean remoteOk = "remote".equals(mode) && !rows.isEmpty();
        if (remoteOk) {
            try {
                var result = engine.chatOnce(java.util.Collections.singletonList(
                        LlmMessage.user(Prompts.warningAdvice(JsonSupport.toJson(rows)))));
                JsonNode node = parseJson(result.getContent());
                JsonNode nodeItems = node.path("items");
                if (nodeItems.isArray()) {
                    for (JsonNode item : nodeItems) {
                        Map<String, Object> map = new LinkedHashMap<String, Object>();
                        map.put("stuId", item.path("stuId").asText());
                        map.put("level", item.path("level").asText());
                        map.put("advice", item.path("advice").asText());
                        items.add(map);
                    }
                }
                overall = node.path("overall").asText("");
                if (items.isEmpty()) {
                    remoteOk = false;
                }
            } catch (Exception e) {
                log.warn("生成干预建议失败，使用规则兜底：{}", e.getMessage());
                remoteOk = false;
            }
        }
        if (!remoteOk) {
            mode = "local";
            for (Map<String, Object> row : rows) {
                Map<String, Object> map = new LinkedHashMap<String, Object>();
                map.put("stuId", row.get("stuId"));
                map.put("stuName", row.get("stuName"));
                map.put("level", row.get("riskLevel"));
                map.put("advice", String.valueOf(row.get("suggestion")));
                map.put("reasons", row.get("reasons"));
                items.add(map);
            }
            overall = "建议按风险等级分层处置：高风险学生 3 个工作日内由辅导员完成约谈并制定帮扶计划，"
                    + "中风险学生 2 周内完成学业跟踪，缺成绩记录的学生先核实数据。";
        }

        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("mode", mode);
        response.put("items", items);
        response.put("overall", overall);
        response.put("rows", rows);
        return response;
    }

    /**
     * 学生个体 360° 画像点评：先调用画像工具取真实数据，再让模型生成「结论 + 建议」；
     * 无模型时用规则模板兜底，保证画像页面始终有可读结论。
     */
    public Map<String, Object> studentPortrait(String stuId, String name) {
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        if (stuId != null && !stuId.trim().isEmpty()) {
            args.put("stuId", stuId.trim());
        }
        if (name != null && !name.trim().isEmpty()) {
            args.put("name", name.trim());
        }

        ToolResult profile = registry.execute("get_student_profile", args);
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("mode", router.activeMode());
        response.put("model", router.describe());
        response.put("success", profile.isSuccess());
        response.put("summary", profile.getSummary());
        response.put("error", profile.getError());
        response.put("profile", profile);
        response.put("generatedAt", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()));

        if (!profile.isSuccess() || !(profile.getData() instanceof Map)) {
            response.put("comment", profile.getError() == null ? "未能获取该学生的画像数据" : profile.getError());
            response.put("suggestions", new ArrayList<String>());
            response.put("riskLevel", null);
            return response;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) profile.getData();
        Map<String, Object> riskRow = findRiskRow(String.valueOf(data.get("basic") instanceof Map
                ? ((Map<?, ?>) data.get("basic")).get("stuId") : ""));
        response.put("riskLevel", riskRow == null ? null : riskRow.get("riskLevel"));
        response.put("riskReasons", riskRow == null ? null : riskRow.get("reasons"));
        response.put("riskSuggestion", riskRow == null ? null : riskRow.get("suggestion"));

        String comment;
        List<String> suggestions = new ArrayList<String>();
        String mode = router.activeMode();
        boolean remoteOk = "remote".equals(mode);
        if (remoteOk) {
            try {
                var result = engine.chatOnce(java.util.Collections.singletonList(
                        LlmMessage.user(Prompts.studentPortrait(props, JsonSupport.toJson(data)))));
                JsonNode node = parseJson(result.getContent());
                comment = node.path("comment").asText("");
                JsonNode nodeSuggestions = node.path("suggestions");
                if (nodeSuggestions.isArray()) {
                    for (JsonNode item : nodeSuggestions) {
                        String text = item.asText("");
                        if (!text.isEmpty()) {
                            suggestions.add(text);
                        }
                    }
                }
                if (comment.isEmpty()) {
                    remoteOk = false;
                }
            } catch (Exception e) {
                log.warn("生成学生画像点评失败，使用规则兜底：{}", e.getMessage());
                remoteOk = false;
                comment = "";
            }
        } else {
            comment = "";
        }
        if (!remoteOk) {
            mode = "local";
            comment = ruleBasedPortrait(data, riskRow);
            suggestions = ruleBasedSuggestions(data, riskRow);
        }

        response.put("mode", mode);
        response.put("comment", comment);
        response.put("suggestions", suggestions);
        return response;
    }

    /** 从预警名单中定位单个学生的风险行 */
    private Map<String, Object> findRiskRow(String stuId) {
        if (stuId == null || stuId.isEmpty() || "null".equals(stuId)) {
            return null;
        }
        ToolResult risk = registry.execute("risk_warning", args("level", "all", "limit", 200));
        if (!(risk.getData() instanceof Map)) {
            return null;
        }
        Object raw = ((Map<?, ?>) risk.getData()).get("rows");
        if (!(raw instanceof List)) {
            return null;
        }
        for (Object item : (List<?>) raw) {
            if (item instanceof Map && stuId.equals(String.valueOf(((Map<?, ?>) item).get("stuId")))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> row = (Map<String, Object>) item;
                return row;
            }
        }
        return null;
    }

    private String ruleBasedPortrait(Map<String, Object> data, Map<String, Object> riskRow) {
        Map<?, ?> basic = data.get("basic") instanceof Map ? (Map<?, ?>) data.get("basic") : null;
        StringBuilder sb = new StringBuilder();
        if (basic != null) {
            sb.append(String.valueOf(basic.get("stuName"))).append("（").append(basic.get("stuId")).append("），")
                    .append(basic.get("className") == null ? "未分班" : basic.get("className")).append("，")
                    .append(basic.get("major") == null ? "-" : basic.get("major")).append("。");
        }
        String level = riskRow == null ? null : String.valueOf(riskRow.get("riskLevel"));
        if ("high".equals(level)) {
            sb.append("系统判定为**需重点帮扶**：");
        } else if ("medium".equals(level)) {
            sb.append("系统判定为**需关注**：");
        } else if ("low".equals(level)) {
            sb.append("学业状态**良好**：");
        } else {
            sb.append("学业状态：");
        }
        sb.append(riskRow == null || riskRow.get("reasons") == null
                ? "暂无预警规则命中" : String.valueOf(riskRow.get("reasons")));
        sb.append("。");

        Object metrics = data.get("scoreSummary");
        if (metrics instanceof List) {
            sb.append("画像指标：");
            List<String> parts = new ArrayList<String>();
            for (Object item : (List<?>) metrics) {
                if (item instanceof Map) {
                    Map<?, ?> metric = (Map<?, ?>) item;
                    parts.add(metric.get("label") + " " + metric.get("value")
                            + (metric.get("unit") == null ? "" : metric.get("unit")));
                }
            }
            sb.append(String.join("、", parts)).append("。");
        }
        int rewardCount = sizeOf(data.get("rewards"));
        int changeCount = sizeOf(data.get("changes"));
        sb.append("在校期间奖惩记录 ").append(rewardCount).append(" 条、学籍异动 ")
                .append(changeCount).append(" 条、档案材料 ").append(intOf(data.get("archiveCount"))).append(" 份。");
        if (intOf(data.get("archiveCount")) == 0) {
            sb.append("档案材料为空，建议核实材料是否已上传。");
        }
        return sb.toString();
    }

    private List<String> ruleBasedSuggestions(Map<String, Object> data, Map<String, Object> riskRow) {
        List<String> suggestions = new ArrayList<String>();
        if (riskRow != null && riskRow.get("suggestion") != null) {
            suggestions.add(String.valueOf(riskRow.get("suggestion")));
        }
        if (sizeOf(data.get("rewards")) == 0 && sizeOf(data.get("scores")) == 0) {
            suggestions.add("该生成绩与奖惩记录均为空，建议先由教务在 1 周内核实数据补录情况");
        } else {
            suggestions.add("建议辅导员在本周内与学生完成一次学业情况沟通，并记录帮扶台账");
        }
        if (intOf(data.get("archiveCount")) == 0) {
            suggestions.add("建议学籍管理员核对档案材料是否缺失，缺失的于 2 周内补齐");
        }
        if (suggestions.size() > 3) {
            return new ArrayList<String>(suggestions.subList(0, 3));
        }
        return suggestions;
    }

    private int sizeOf(Object value) {
        return value instanceof List ? ((List<?>) value).size() : 0;
    }

    // ------------------------------------------------------------------ 兜底

    private List<Map<String, Object>> ruleBasedPoints(Map<String, Object> facts) {
        List<Map<String, Object>> points = new ArrayList<Map<String, Object>>();
        int studentTotal = intOf(facts.get("studentTotal"));
        int withScore = intOf(facts.get("studentsWithScore"));
        int pending = intOf(facts.get("pendingChange"));
        int abnormal = intOf(facts.get("abnormalScore"));
        int high = metricOf(facts.get("riskMetrics"), "高风险");
        int medium = metricOf(facts.get("riskMetrics"), "中风险");

        p(points, "学籍规模与结构",
                "当前管理 " + studentTotal + " 名学生、" + metricOf(facts.get("metrics"), "班级数量") + " 个班级、"
                        + metricOf(facts.get("metrics"), "专业数量") + " 个专业，覆盖 "
                        + metricOf(facts.get("metrics"), "院系数量") + " 个院系。",
                "info", "学生总数 " + studentTotal);
        p(points, "成绩数据覆盖",
                withScore + "/" + studentTotal + " 名学生存在成绩记录，覆盖率 "
                        + percent(withScore, studentTotal) + "，其余学生画像与预警结论需谨慎使用。",
                withScore < studentTotal ? "warning" : "info",
                "有成绩学生 " + withScore + " / 总数 " + studentTotal);
        if (high > 0 || medium > 0) {
            p(points, "学业风险分布",
                    "高风险 " + high + " 人、中风险 " + medium + " 人，建议优先处置高风险学生的帮扶方案。",
                    high > 0 ? "danger" : "warning", "高风险 " + high + " 人");
        }
        if (pending > 0) {
            p(points, "学籍异动积压",
                    pending + " 条学籍异动申请处于待审核状态，建议督促教务及时处理，避免影响学生学籍状态。",
                    "warning", "待审异动 " + pending + " 条");
        }
        if (abnormal > 0) {
            p(points, "成绩数据质量",
                    "检测到 " + abnormal + " 条超过 100 分的成绩记录，属于录入异常，会直接影响平均分与排名，建议核对。",
                    "warning", "异常成绩 " + abnormal + " 条");
        }
        Object classScores = facts.get("classScores");
        if (classScores instanceof List && !((List<?>) classScores).isEmpty()) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) classScores;
            Map<String, Object> top = rows.get(0);
            Map<String, Object> bottom = rows.get(rows.size() - 1);
            p(points, "班级成绩对比",
                    "平均分最高：" + top.get("name") + "（" + top.get("avgScore") + " 分）；最低："
                            + bottom.get("name") + "（" + bottom.get("avgScore") + " 分），建议复盘教学差异。",
                    "info", "最高 " + top.get("name") + " / 最低 " + bottom.get("name"));
        }
        if (points.size() > 5) {
            return new ArrayList<Map<String, Object>>(points.subList(0, 5));
        }
        return points;
    }

    private String ruleBasedOverall(Map<String, Object> facts) {
        int high = metricOf(facts.get("riskMetrics"), "高风险");
        int pending = intOf(facts.get("pendingChange"));
        if (high > 0) {
            return "整体平稳，但存在 " + high + " 名高风险学生需立即介入，另有数据完整性问题影响分析精度。";
        }
        if (pending > 0) {
            return "学籍数据整体平稳，" + pending + " 条待审异动是当前主要待办事项。";
        }
        return "学籍与成绩数据整体平稳，暂未发现需要紧急干预的异常信号。";
    }

    private void p(List<Map<String, Object>> points, String title, String detail, String level, String evidence) {
        Map<String, Object> point = new LinkedHashMap<String, Object>();
        point.put("title", title);
        point.put("detail", detail);
        point.put("level", level);
        point.put("evidence", evidence);
        points.add(point);
    }

    private List<Map<String, Object>> toPoints(JsonNode node) {
        List<Map<String, Object>> points = new ArrayList<Map<String, Object>>();
        if (node == null || !node.isArray()) {
            return points;
        }
        for (JsonNode item : node) {
            Map<String, Object> point = new LinkedHashMap<String, Object>();
            point.put("title", item.path("title").asText());
            point.put("detail", item.path("detail").asText());
            String level = item.path("level").asText("info");
            point.put("level", level.isEmpty() ? "info" : level);
            point.put("evidence", item.path("evidence").asText(""));
            if (!String.valueOf(point.get("title")).isEmpty()) {
                points.add(point);
            }
        }
        return points;
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

    private int metricOf(Object metrics, String label) {
        if (!(metrics instanceof List)) {
            return 0;
        }
        for (Object item : (List<?>) metrics) {
            if (item instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) item;
                if (label.equals(String.valueOf(map.get("label")))) {
                    return intOf(map.get("value"));
                }
            }
        }
        return 0;
    }

    private int intOf(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return new BigDecimal(String.valueOf(value)).intValue();
        } catch (Exception e) {
            return 0;
        }
    }

    private String percent(int part, int total) {
        if (total <= 0) {
            return "0%";
        }
        return new BigDecimal(part * 100.0 / total).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%";
    }

    private Map<String, Object> args(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }
}
