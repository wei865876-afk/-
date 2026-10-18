package com.school.student_manage.ai.tool.impl;

import com.school.student_manage.ai.rag.KnowledgeService;
import com.school.student_manage.ai.rag.RetrievalHit;
import com.school.student_manage.ai.tool.AiTool;
import com.school.student_manage.ai.tool.Schemas;
import com.school.student_manage.ai.tool.ToolResult;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 制度知识库检索（RAG 召回工具）。
 * 当问题涉及校规校纪、办事流程、政策口径时调用，返回带出处的片段供模型组织答案。
 */
@Component
public class KnowledgeSearchTool implements AiTool {

    @Resource
    private KnowledgeService knowledgeService;

    @Override
    public String name() {
        return "search_knowledge";
    }

    @Override
    public String title() {
        return "制度知识库检索";
    }

    @Override
    public String description() {
        return "检索学校规章制度知识库（学籍管理、转专业、休复学、成绩考核、学业预警、奖学金、处分、毕业要求、请假考勤等）。"
                + "凡涉及'规定是什么/怎么办/需要什么材料/多久内办理/能不能'这类制度性问题，必须先调用本工具再作答，并引用出处。"
                + "示例：转专业需要什么条件 -> {query:'转专业条件 流程'}。";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        Map<String, Object> props = new LinkedHashMap<String, Object>();
        props.put("query", Schemas.string("检索关键词，尽量包含制度名称与诉求，如 '休学 办理 材料 期限'"));
        props.put("topK", Schemas.integer("返回片段数，默认 4"));
        return Schemas.object(props, "query");
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        Object query = args.get("query");
        if (query == null || String.valueOf(query).trim().isEmpty()) {
            return ToolResult.fail(name(), title(), "缺少 query 参数");
        }
        int topK = args.get("topK") == null ? 4 : parse(args.get("topK"), 4);
        List<RetrievalHit> hits = knowledgeService.search(String.valueOf(query), topK);
        if (hits.isEmpty()) {
            ToolResult result = ToolResult.of(name(), title(), "text", "知识库中未找到相关制度条款");
            result.hints("告知用户知识库暂无该条目，建议咨询教务处，不要编造制度内容");
            return result;
        }
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> citations = new ArrayList<Map<String, Object>>();
        for (RetrievalHit hit : hits) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("title", hit.getTitle());
            row.put("category", hit.getCategory());
            row.put("content", hit.getContent());
            row.put("score", Math.round(hit.getScore() * 1000) / 1000.0);
            rows.add(row);

            Map<String, Object> citation = new LinkedHashMap<String, Object>();
            citation.put("docId", hit.getDocId());
            citation.put("title", hit.getTitle());
            citation.put("category", hit.getCategory());
            citation.put("source", hit.getSource());
            citation.put("snippet", hit.getContent().length() > 160
                    ? hit.getContent().substring(0, 160) + "…" : hit.getContent());
            citation.put("score", Math.round(hit.getScore() * 1000) / 1000.0);
            citations.add(citation);
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("citations", citations);
        data.put("chunks", rows);

        ToolResult result = ToolResult.of(name(), title(), "citations",
                "命中 " + hits.size() + " 个制度片段，涉及《" + hits.get(0).getTitle() + "》等");
        result.setData(data);
        result.hints("回答时必须标注来源文件名称，禁止脱离检索结果编造制度条款");
        return result;
    }

    private int parse(Object value, int defaultValue) {
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
