package com.school.student_manage.ai.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 本地规则引擎（离线兜底 / 演示保障）。
 *
 * <p>在没有配置大模型密钥、或远程服务不可用时接管对话：</p>
 * <ol>
 *   <li>意图识别 → 生成等价的 Function Calling 调用（与远程模式走完全相同的工具链路）；</li>
 *   <li>拿到工具结果后，用确定性模板渲染成结构化中文回答；</li>
 *   <li>回答中明确标注"本地推理模式"，不做无依据的编造。</li>
 * </ol>
 * 这保证了"无 Key 也能完整演示、断网也能答辩"，同时避免用假数据冒充大模型输出。
 */
@Component
public class HeuristicClient implements LlmClient {

    private static final Pattern STU_ID = Pattern.compile("\\d{6,}");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private JdbcTemplate jdbcTemplate;

    private volatile List<String> cachedNames;
    private volatile long namesCachedAt;

    @Override
    public String provider() {
        return "本地规则引擎";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public ChatResult chat(List<LlmMessage> messages, List<ToolSpec> tools, StreamHandler handler) {
        LlmMessage lastUser = null;
        List<LlmMessage> toolMessages = new ArrayList<LlmMessage>();
        boolean afterUser = false;
        for (LlmMessage message : messages) {
            if ("user".equals(message.getRole())) {
                lastUser = message;
                toolMessages.clear();
                afterUser = true;
            } else if (afterUser && "tool".equals(message.getRole())) {
                toolMessages.add(message);
            }
        }
        String question = lastUser == null || lastUser.getContent() == null ? "" : lastUser.getContent();

        ChatResult result = new ChatResult();
        result.setModel("local-rule-engine");
        if (toolMessages.isEmpty()) {
            List<LlmToolCall> calls = planTools(question, tools);
            if (calls.isEmpty()) {
                String text = capabilityAnswer(question);
                emit(text, handler);
                result.setContent(text);
                return result;
            }
            result.setToolCalls(calls);
            return result;
        }

        String answer = compose(toolMessages);
        emit(answer, handler);
        result.setContent(answer);
        return result;
    }

    // ------------------------------------------------------------ 意图 -> 工具

    private List<LlmToolCall> planTools(String question, List<ToolSpec> tools) {
        List<LlmToolCall> calls = new ArrayList<LlmToolCall>();
        if (question == null || question.trim().isEmpty()) {
            return calls;
        }
        String text = question.trim();
        List<String> available = new ArrayList<String>();
        for (ToolSpec spec : tools) {
            available.add(spec.getName());
        }

        String stuId = findStuId(text);
        String name = findStudentName(text);

        boolean askRule = containsAny(text, "规定", "制度", "办法", "条例", "流程", "需要什么材料", "要什么材料",
                "怎么办", "如何办理", "能不能", "可以吗", "多久", "几天", "条件", "要求", "政策", "标准", "学分",
                "毕业", "学位", "请假", "考勤", "处分", "申诉", "保留学籍");
        boolean askRisk = containsAny(text, "预警", "风险", "挂科", "不及格", "需要关注", "帮扶", "毕不了业",
                "重点关注", "学业困难", "退学警告");
        boolean askScore = containsAny(text, "成绩", "平均分", "及格率", "优秀率", "排名", "分数", "考试", "功课");
        boolean askStats = containsAny(text, "多少学生", "多少人", "总数", "统计", "概况", "整体", "男女", "性别比例",
                "分布", "规模", "多少人", "几个班", "几个专业", "几门课");
        boolean askReward = containsAny(text, "奖惩", "奖学金", "评优", "处分记录", "获奖");
        boolean askChange = containsAny(text, "异动", "转专业", "休学", "复学", "退学", "申请", "审核", "待审");
        boolean askList = containsAny(text, "名单", "哪些学生", "谁是", "有没有这个人", "查一下", "看看学生", "有哪些学生")
                || (name != null && !askScore && !askRisk);

        if (stuId != null && (containsAny(text, "情况", "画像", "怎么样", "如何", "档案", "了解一下", "分析")
                || (!askScore && !askRisk && !askStats))) {
            calls.add(call("get_student_profile", arg("stuId", stuId)));
            return calls;
        }
        if (name != null && (askRisk || askScore || containsAny(text, "情况", "画像", "怎么样", "档案", "分析"))) {
            if (askScore) {
                calls.add(call("analyze_scores", arg("dimension", "student"), arg("target", name)));
            }
            calls.add(call("get_student_profile", arg("name", name)));
            return calls;
        }
        if (askRisk) {
            if (available.contains("risk_warning")) {
                calls.add(call("risk_warning", arg("level", "all")));
            }
            return calls;
        }
        if (askRule && available.contains("search_knowledge")) {
            calls.add(call("search_knowledge", arg("query", text), arg("topK", 4)));
            return calls;
        }
        if (askScore && available.contains("analyze_scores")) {
            String dimension = "overall";
            if (containsAny(text, "班级", "各班")) {
                dimension = "class";
            } else if (containsAny(text, "课程", "科目", "这门课")) {
                dimension = "course";
            } else if (containsAny(text, "院系", "学院")) {
                dimension = "dept";
            } else if (containsAny(text, "排名", "前十", "学生")) {
                dimension = "student";
            }
            Map<String, Object> args = arg("dimension", dimension);
            String target = extractTarget(text);
            if (target != null) {
                args.put("target", target);
            }
            calls.add(call("analyze_scores", args));
            return calls;
        }
        if (askReward && available.contains("query_reward_punish")) {
            Map<String, Object> args = new LinkedHashMap<String, Object>();
            if (name != null) {
                args.put("keyword", name);
            } else if (containsAny(text, "处分")) {
                args.put("type", "处分");
            } else if (containsAny(text, "奖学金", "奖励", "评优")) {
                args.put("type", "奖励");
            }
            calls.add(call("query_reward_punish", args));
            return calls;
        }
        if (askChange && available.contains("query_change_apply")) {
            Map<String, Object> args = new LinkedHashMap<String, Object>();
            if (containsAny(text, "待审核", "没审", "未审")) {
                args.put("status", "待审核");
            }
            if (name != null) {
                args.put("keyword", name);
            }
            calls.add(call("query_change_apply", args));
            return calls;
        }
        if (askList && available.contains("query_students")) {
            Map<String, Object> args = new LinkedHashMap<String, Object>();
            if (name != null) {
                args.put("keyword", name);
            }
            String className = extractClassName(text);
            if (className != null) {
                args.put("className", className);
            }
            if (containsAny(text, "女")) {
                args.put("gender", "女");
            } else if (containsAny(text, "男")) {
                args.put("gender", "男");
            }
            calls.add(call("query_students", args));
            return calls;
        }
        if (askStats && available.contains("stats_overview")) {
            calls.add(call("stats_overview", new LinkedHashMap<String, Object>()));
            return calls;
        }
        return calls;
    }

    // ------------------------------------------------------------- 结果 -> 回答

    private String compose(List<LlmMessage> toolMessages) {
        StringBuilder sb = new StringBuilder();
        sb.append("> 当前为**本地推理模式**（未配置或未启用大模型密钥）：以下结论由规则引擎基于系统真实数据生成，"
                + "配置密钥后可获得更自然的表达与更复杂的推理。\n\n");
        int index = 0;
        for (LlmMessage message : toolMessages) {
            index++;
            String raw = message.getContent();
            JsonNode node;
            try {
                node = MAPPER.readTree(raw == null ? "{}" : raw);
            } catch (Exception e) {
                continue;
            }
            String title = node.path("title").asText("数据查询");
            String summary = node.path("summary").asText("");
            boolean success = node.path("success").asBoolean(true);
            if (!success) {
                sb.append("**").append(title).append("**：").append(node.path("error").asText("执行失败")).append("\n\n");
                continue;
            }
            if (toolMessages.size() > 1) {
                sb.append("**").append(index).append(". ").append(title).append("**\n");
            }
            if (!summary.isEmpty()) {
                sb.append(summary).append("\n\n");
            }
            String render = node.path("render").asText("text");
            JsonNode data = node.path("data");
            switch (render) {
                case "table":
                case "sql":
                    sb.append(table(data));
                    sb.append(outlierNote(data.path("rows")));
                    if ("sql".equals(render) && data.hasNonNull("sql")) {
                        sb.append("\n```sql\n").append(data.get("sql").asText()).append("\n```\n");
                    }
                    break;
                case "metrics":
                    sb.append(metrics(data.has("metrics") ? data.get("metrics") : data));
                    sb.append(distribution(data));
                    break;
                case "profile":
                    sb.append(profile(data));
                    break;
                case "warning":
                    sb.append(warning(data));
                    sb.append(outlierNote(data.path("rows")));
                    break;
                case "citations":
                    sb.append(citations(data));
                    break;
                default:
                    break;
            }
            if (node.has("hints") && node.get("hints").isArray() && !node.get("hints").isEmpty()) {
                sb.append("\n**后续建议**\n");
                for (JsonNode hint : node.get("hints")) {
                    sb.append("- ").append(hint.asText()).append('\n');
                }
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    private String table(JsonNode data) {
        JsonNode columns = data.path("columns");
        JsonNode rows = data.path("rows");
        if (!columns.isArray() || !rows.isArray() || rows.isEmpty()) {
            return "（无数据行）\n";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("| ");
        List<String> names = new ArrayList<String>();
        for (JsonNode column : columns) {
            names.add(column.asText());
            sb.append(column.asText()).append(" | ");
        }
        sb.append("\n|");
        for (int i = 0; i < names.size(); i++) {
            sb.append(" --- |");
        }
        sb.append('\n');
        int limit = Math.min(rows.size(), 15);
        for (int i = 0; i < limit; i++) {
            JsonNode row = rows.get(i);
            sb.append("| ");
            for (String columnName : names) {
                JsonNode value = row.get(columnName);
                String text = value == null || value.isNull() ? "-"
                        : (value.isValueNode() ? value.asText() : value.toString());
                if (text.length() > 40) {
                    text = text.substring(0, 40) + "…";
                }
                sb.append(text).append(" | ");
            }
            sb.append('\n');
        }
        if (rows.size() > limit) {
            sb.append("\n（仅展示前 ").append(limit).append(" 行，共 ").append(rows.size()).append(" 行）\n");
        }
        return sb.toString();
    }

    /**
     * 数据质量自省：结果集中出现超过 100 分的成绩类数值时主动提醒。
     * 这是"AI 懂业务"的关键细节——不盲目复述脏数据，而是指出异常并说明影响。
     */
    private String outlierNote(JsonNode rows) {
        if (!rows.isArray() || rows.isEmpty()) {
            return "";
        }
        String[] keys = {"avgScore", "maxScore", "minScore", "score", "平均分", "最高分", "最低分", "分数"};
        int count = 0;
        double max = 0;
        for (JsonNode row : rows) {
            for (String key : keys) {
                JsonNode value = row.get(key);
                if (value != null && value.isNumber() && value.asDouble() > 100) {
                    count++;
                    max = Math.max(max, value.asDouble());
                }
            }
        }
        if (count == 0) {
            return "";
        }
        String maxText = new java.math.BigDecimal(String.valueOf(max)).stripTrailingZeros().toPlainString();
        return "\n> ⚠️ **数据质量提醒**：上述结果中有 " + count + " 个成绩数值超过 100 分（最高 " + maxText
                + "），属于录入异常，会直接影响平均分与排名，建议先核对原始数据再采纳结论。\n";
    }

    private String metrics(JsonNode metrics) {
        if (!metrics.isArray() || metrics.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode item : metrics) {
            sb.append("- ").append(item.path("label").asText()).append("：")
                    .append(item.path("value").asText()).append(' ').append(item.path("unit").asText("")).append('\n');
        }
        return sb.toString();
    }

    private String distribution(JsonNode data) {
        StringBuilder sb = new StringBuilder();
        appendDistribution(sb, "院系分布", data.path("byDept"));
        appendDistribution(sb, "专业分布", data.path("byMajor"));
        appendDistribution(sb, "班级分布", data.path("byClass"));
        appendDistribution(sb, "性别分布", data.path("byGender"));
        appendDistribution(sb, "状态分布", data.path("byStatus"));
        return sb.toString();
    }

    private void appendDistribution(StringBuilder sb, String label, JsonNode array) {
        if (!array.isArray() || array.isEmpty()) {
            return;
        }
        sb.append("\n").append(label).append("：");
        List<String> parts = new ArrayList<String>();
        for (JsonNode item : array) {
            parts.add(item.path("name").asText() + " " + item.path("value").asText());
        }
        sb.append(String.join("，", parts)).append('\n');
    }

    private String profile(JsonNode data) {
        StringBuilder sb = new StringBuilder();
        JsonNode basic = data.path("basic");
        sb.append("- 姓名：").append(basic.path("stuName").asText()).append("（")
                .append(basic.path("stuId").asText()).append("）\n")
                .append("- 班级：").append(basic.path("className").asText("-")).append("｜专业：")
                .append(basic.path("major").asText("-")).append("｜院系：").append(basic.path("dept").asText("-")).append('\n')
                .append("- 状态：").append(basic.path("status").asText("-")).append("｜辅导员：")
                .append(basic.path("advisor").asText("-")).append('\n');
        String summary = metrics(data.path("scoreSummary"));
        if (!summary.isEmpty()) {
            sb.append("\n**成绩概览**\n").append(summary);
        }
        JsonNode scores = data.path("scores");
        if (scores.isArray() && !scores.isEmpty()) {
            sb.append("\n**成绩明细**\n");
            int limit = Math.min(scores.size(), 12);
            for (int i = 0; i < limit; i++) {
                JsonNode score = scores.get(i);
                sb.append("- ").append(score.path("course_name").asText())
                        .append("：").append(score.path("score").asText()).append('\n');
            }
        }
        JsonNode rewards = data.path("rewards");
        if (rewards.isArray() && !rewards.isEmpty()) {
            sb.append("\n**奖惩记录**\n");
            for (JsonNode item : rewards) {
                sb.append("- ").append(item.path("rp_type").asText()).append(" ")
                        .append(item.path("level_name").asText("")).append("（")
                        .append(item.path("rp_date").asText("")).append("）\n");
            }
        }
        JsonNode changes = data.path("changes");
        if (changes.isArray() && !changes.isEmpty()) {
            sb.append("\n**学籍异动**\n");
            for (JsonNode item : changes) {
                sb.append("- ").append(item.path("change_type").asText()).append("：")
                        .append(item.path("status").asText("")).append('\n');
            }
        }
        return sb.toString();
    }

    private String warning(JsonNode data) {
        StringBuilder sb = new StringBuilder();
        sb.append(metrics(data.path("metrics")));
        JsonNode rows = data.path("rows");
        if (rows.isArray() && !rows.isEmpty()) {
            sb.append("\n| 学号 | 姓名 | 班级 | 平均分 | 不及格 | 风险 | 原因 | 建议 |\n");
            sb.append("| --- | --- | --- | --- | --- | --- | --- | --- |\n");
            int limit = Math.min(rows.size(), 15);
            for (int i = 0; i < limit; i++) {
                JsonNode row = rows.get(i);
                sb.append("| ").append(row.path("stuId").asText()).append(" | ")
                        .append(row.path("stuName").asText()).append(" | ")
                        .append(row.path("className").asText()).append(" | ")
                        .append(row.path("avgScore").asText()).append(" | ")
                        .append(row.path("failCount").asText()).append(" | ")
                        .append(levelText(row.path("riskLevel").asText())).append(" | ")
                        .append(row.path("reasons").asText()).append(" | ")
                        .append(row.path("suggestion").asText()).append(" |\n");
            }
        }
        return sb.toString();
    }

    private String levelText(String level) {
        if ("high".equals(level)) {
            return "高";
        }
        if ("medium".equals(level)) {
            return "中";
        }
        return "低";
    }

    private String citations(JsonNode data) {
        JsonNode citations = data.path("citations");
        if (!citations.isArray() || citations.isEmpty()) {
            return "未检索到相关制度条款。\n";
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (JsonNode item : citations) {
            String content = item.path("snippet").asText("");
            if (first) {
                sb.append("根据《").append(item.path("title").asText()).append("》的相关规定：\n\n")
                        .append(content).append("\n\n");
                first = false;
            } else {
                sb.append("- 另见《").append(item.path("title").asText()).append("》：")
                        .append(content.length() > 90 ? content.substring(0, 90) + "…" : content).append('\n');
            }
        }
        sb.append("\n> 如需完整条款原文，可在「制度知识库」中查看对应文档。\n");
        return sb.toString();
    }

    private String capabilityAnswer(String question) {
        StringBuilder sb = new StringBuilder();
        sb.append("> 当前为**本地推理模式**（未配置大模型密钥）。\n\n");
        sb.append("我可以基于教务系统的真实数据完成这些分析：\n\n");
        sb.append("- **学生画像**：例如「查一下张三的情况」「2023001 这个学生怎么样」\n");
        sb.append("- **成绩分析**：例如「各班平均分对比」「成绩排名前十」「高数这门课的情况」\n");
        sb.append("- **学业预警**：例如「哪些学生需要重点关注」\n");
        sb.append("- **学籍总览**：例如「学校有多少学生」「各院系人数分布」\n");
        sb.append("- **奖惩与异动**：例如「奖学金名单」「还有多少待审核的转专业申请」\n");
        sb.append("- **制度问答**：例如「转专业需要什么条件」「休学怎么办」\n");
        sb.append("- **自由取数**：例如「统计每个院系女生的平均分」（自动生成只读 SQL）\n\n");
        if (question != null && !question.trim().isEmpty()) {
            sb.append("你的问题是「").append(question.trim())
                    .append("」，本地规则引擎暂时无法直接归类。配置大模型密钥后，这类开放问题也能被理解并给出答案。");
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- 小工具

    private String findStuId(String text) {
        Matcher matcher = STU_ID.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    private String findStudentName(String text) {
        for (String name : studentNames()) {
            if (name != null && name.length() >= 2 && text.contains(name)) {
                return name;
            }
        }
        return null;
    }

    private List<String> studentNames() {
        long now = System.currentTimeMillis();
        if (cachedNames == null || now - namesCachedAt > 120_000L) {
            try {
                cachedNames = jdbcTemplate.queryForList(
                        "SELECT stu_name FROM student WHERE stu_name IS NOT NULL", String.class);
            } catch (Exception e) {
                cachedNames = new ArrayList<String>();
            }
            namesCachedAt = now;
        }
        return cachedNames;
    }

    private String extractClassName(String text) {
        Matcher matcher = Pattern.compile("[\\u4e00-\\u9fa5]{1,6}\\d{2}-\\d{1,2}").matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    private String extractTarget(String text) {
        Matcher matcher = Pattern.compile("[\\u4e00-\\u9fa5]{2,10}(?:专业|课程|这门课|班)").matcher(text);
        if (matcher.find()) {
            String value = matcher.group();
            value = value.replace("专业", "").replace("课程", "").replace("这门课", "").replace("班", "");
            return value.isEmpty() ? null : value;
        }
        return null;
    }

    private boolean containsAny(String text, String... keywords) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> arg(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put(key, value);
        return map;
    }

    @SafeVarargs
    private final LlmToolCall call(String name, Map<String, Object>... argGroups) {
        Map<String, Object> merged = new LinkedHashMap<String, Object>();
        for (Map<String, Object> group : argGroups) {
            if (group != null) {
                merged.putAll(group);
            }
        }
        try {
            return new LlmToolCall("local_" + name + "_" + System.nanoTime(), name,
                    MAPPER.writeValueAsString(merged));
        } catch (Exception e) {
            return new LlmToolCall("local_" + name, name, "{}");
        }
    }

    private void emit(String text, StreamHandler handler) {
        if (handler == null || text == null || text.isEmpty()) {
            return;
        }
        int step = 18;
        for (int i = 0; i < text.length(); i += step) {
            handler.onDelta(text.substring(i, Math.min(text.length(), i + step)));
        }
    }
}
