package com.school.student_manage.ai.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.student_manage.ai.config.AiProperties;
import com.school.student_manage.ai.llm.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 制度知识库服务（RAG）：
 * 文档入库 → 智能切片 → 关键词/语义索引 → 检索召回 → 作为上下文注入大模型并回带引用。
 */
@Service
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    private static final int CHUNK_SIZE = 420;
    private static final int CHUNK_OVERLAP = 60;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private AiProperties props;

    @Resource
    private LlmClient llmClient;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HybridRetriever retriever = new HybridRetriever();
    private volatile boolean loaded = false;

    /** 启动后按需加载索引 */
    public synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        reload();
        loaded = true;
    }

    /** 重建内存索引 */
    public synchronized void reload() {
        retriever.reset();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT c.id AS chunk_id, c.doc_id, c.content, c.embedding, "
                        + "k.title, k.category, k.tags, k.source "
                        + "FROM ai_knowledge_chunk c JOIN ai_knowledge k ON c.doc_id = k.id "
                        + "ORDER BY c.doc_id, c.seq");
        List<HybridRetriever.Chunk> chunks = new ArrayList<HybridRetriever.Chunk>();
        for (Map<String, Object> row : rows) {
            HybridRetriever.Chunk chunk = new HybridRetriever.Chunk(
                    (Integer) row.get("doc_id"),
                    (Integer) row.get("chunk_id"),
                    (String) row.get("title"),
                    (String) row.get("category"),
                    (String) row.get("tags"),
                    (String) row.get("source"),
                    (String) row.get("content"),
                    parseEmbedding((String) row.get("embedding")));
            chunks.add(chunk);
        }
        retriever.index(chunks);
        log.info("知识库索引完成：{} 个片段", chunks.size());
    }

    public int docCount() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_knowledge", Integer.class);
        return count == null ? 0 : count;
    }

    public int chunkCount() {
        return retriever.size();
    }

    public List<Map<String, Object>> list() {
        ensureLoaded();
        return jdbcTemplate.queryForList(
                "SELECT id, title, category, tags, source, chunk_count, create_time, "
                        + "LEFT(content, 120) AS preview FROM ai_knowledge ORDER BY id DESC");
    }

    public Map<String, Object> get(int id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, title, category, tags, source, content, chunk_count, create_time "
                        + "FROM ai_knowledge WHERE id = ?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 新增或更新文档，并重建该文档的索引 */
    public int save(Map<String, Object> payload) {
        String title = text(payload.get("title"));
        String content = text(payload.get("content"));
        if ((content == null || content.isEmpty()) && payload.get("paragraphs") instanceof List) {
            StringBuilder sb = new StringBuilder();
            for (Object paragraph : (List<?>) payload.get("paragraphs")) {
                if (paragraph != null && !String.valueOf(paragraph).trim().isEmpty()) {
                    if (sb.length() > 0) {
                        sb.append("\n\n");
                    }
                    sb.append(String.valueOf(paragraph).trim());
                }
            }
            content = sb.toString();
        }
        if (title == null || title.isEmpty()) {
            title = "未命名文档";
        }
        if (content == null || content.isEmpty()) {
            throw new IllegalArgumentException("文档内容不能为空");
        }
        String category = text(payload.get("category"));
        String tags = text(payload.get("tags"));
        String source = text(payload.get("source"));
        Integer id = payload.get("id") == null ? null : intValue(payload.get("id"));

        if (id == null) {
            jdbcTemplate.update("INSERT INTO ai_knowledge (title, category, tags, content, source) VALUES (?,?,?,?,?)",
                    title, category, tags, content, source);
            id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM ai_knowledge", Integer.class);
        } else {
            jdbcTemplate.update("UPDATE ai_knowledge SET title=?, category=?, tags=?, content=?, source=? WHERE id=?",
                    title, category, tags, content, source, id);
        }
        reindex(id);
        return id;
    }

    public void delete(int id) {
        jdbcTemplate.update("DELETE FROM ai_knowledge WHERE id = ?", id);
        reload();
    }

    /** 重新切片并计算向量（如启用） */
    public void reindex(int docId) {
        Map<String, Object> doc = get(docId);
        if (doc == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM ai_knowledge_chunk WHERE doc_id = ?", docId);
        String content = String.valueOf(doc.get("content"));
        List<String> chunks = TextChunker.chunk(content, CHUNK_SIZE, CHUNK_OVERLAP);
        int seq = 0;
        for (String chunk : chunks) {
            String embedding = null;
            if (llmClient.supportsEmbedding()) {
                float[] vector = llmClient.embed(chunk);
                embedding = vector == null ? null : toJson(vector);
            }
            jdbcTemplate.update(
                    "INSERT INTO ai_knowledge_chunk (doc_id, seq, content, embedding) VALUES (?,?,?,?)",
                    docId, seq++, chunk, embedding);
        }
        jdbcTemplate.update("UPDATE ai_knowledge SET chunk_count = ?, indexed_at = NOW() WHERE id = ?",
                chunks.size(), docId);
        reload();
    }

    /** 全文重建索引（切换向量模型后使用） */
    public int reindexAll() {
        List<Integer> ids = jdbcTemplate.queryForList("SELECT id FROM ai_knowledge ORDER BY id", Integer.class);
        for (Integer id : ids) {
            reindex(id);
        }
        reload();
        return ids.size();
    }

    /** 混合检索 */
    public List<RetrievalHit> search(String query, Integer topK) {
        ensureLoaded();
        int k = topK == null || topK <= 0 ? props.getRagTopK() : topK;
        List<HybridRetriever.Scored> keywordHits = retriever.scoreKeyword(query, Math.max(k * 3, 12));
        Map<Integer, Double> semanticScores = new LinkedHashMap<Integer, Double>();
        if (llmClient.supportsEmbedding()) {
            float[] queryVector = llmClient.embed(query);
            if (queryVector != null) {
                for (HybridRetriever.Chunk chunk : retriever.allChunks()) {
                    if (chunk.embedding != null) {
                        semanticScores.put(chunk.chunkId, HybridRetriever.cosine(queryVector, chunk.embedding));
                    }
                }
            }
        }

        double maxKeyword = 0;
        for (HybridRetriever.Scored scored : keywordHits) {
            maxKeyword = Math.max(maxKeyword, scored.score);
        }

        Map<Integer, RetrievalHit> merged = new LinkedHashMap<Integer, RetrievalHit>();
        for (HybridRetriever.Scored scored : keywordHits) {
            RetrievalHit hit = toHit(scored.chunk);
            hit.setKeywordScore(maxKeyword == 0 ? 0 : scored.score / maxKeyword);
            hit.setScore(hit.getKeywordScore());
            merged.put(scored.chunk.chunkId, hit);
        }
        if (!semanticScores.isEmpty()) {
            for (HybridRetriever.Chunk chunk : retriever.allChunks()) {
                Double semantic = semanticScores.get(chunk.chunkId);
                if (semantic == null) {
                    continue;
                }
                RetrievalHit hit = merged.get(chunk.chunkId);
                if (hit == null) {
                    hit = toHit(chunk);
                    merged.put(chunk.chunkId, hit);
                }
                hit.setSemanticScore(semantic);
            }
            for (RetrievalHit hit : merged.values()) {
                hit.setScore(hit.getSemanticScore() * 0.6 + hit.getKeywordScore() * 0.4);
            }
        }
        List<RetrievalHit> hits = new ArrayList<RetrievalHit>(merged.values());
        hits.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        if (hits.size() > k) {
            hits = new ArrayList<RetrievalHit>(hits.subList(0, k));
        }
        return hits;
    }

    /** 拼装注入模型的带引用上下文 */
    public String buildContext(List<RetrievalHit> hits) {
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (RetrievalHit hit : hits) {
            sb.append("【片段").append(index++).append("】来源：").append(hit.getTitle());
            if (hit.getCategory() != null) {
                sb.append("（").append(hit.getCategory()).append("）");
            }
            sb.append('\n').append(hit.getContent()).append("\n\n");
        }
        return sb.toString().trim();
    }

    /** 首次启动时导入内置制度文档 */
    public int seedFromClasspathIfEmpty() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_knowledge", Integer.class);
        if (count != null && count > 0) {
            return 0;
        }
        return resetToDemo();
    }

    /** 重置为内置示例语料 */
    public int resetToDemo() {
        try {
            ClassPathResource resource = new ClassPathResource("ai/knowledge-seed.json");
            try (InputStream in = resource.getInputStream()) {
                List<Map<String, Object>> docs = mapper.readValue(in, new TypeReference<List<Map<String, Object>>>() {
                });
                jdbcTemplate.update("DELETE FROM ai_knowledge_chunk");
                jdbcTemplate.update("DELETE FROM ai_knowledge");
                for (Map<String, Object> doc : docs) {
                    save(doc);
                }
                log.info("已导入内置制度知识库，共 {} 篇", docs.size());
                return docs.size();
            }
        } catch (Exception e) {
            log.warn("导入内置知识库失败：{}", e.getMessage());
            return 0;
        }
    }

    /** 关键词命中检索（供本地兜底模式复用） */
    public List<Map<String, Object>> searchAsMaps(String query, int topK) {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        for (RetrievalHit hit : search(query, topK)) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("docId", hit.getDocId());
            item.put("title", hit.getTitle());
            item.put("category", hit.getCategory());
            item.put("source", hit.getSource());
            item.put("content", hit.getContent());
            item.put("score", Math.round(hit.getScore() * 1000) / 1000.0);
            list.add(item);
        }
        return list;
    }

    private RetrievalHit toHit(HybridRetriever.Chunk chunk) {
        RetrievalHit hit = new RetrievalHit();
        hit.setChunkId(chunk.chunkId);
        hit.setDocId(chunk.docId);
        hit.setTitle(chunk.title);
        hit.setCategory(chunk.category);
        hit.setSource(chunk.source);
        hit.setContent(chunk.content);
        return hit;
    }

    private String toJson(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }

    private float[] parseEmbedding(String text) {
        if (text == null || text.length() < 3) {
            return null;
        }
        try {
            String body = text.trim();
            body = body.substring(1, body.length() - 1);
            String[] parts = body.split(",");
            float[] vector = new float[parts.length];
            for (int i = 0; i < parts.length; i++) {
                vector[i] = Float.parseFloat(parts[i].trim());
            }
            return vector;
        } catch (Exception e) {
            return null;
        }
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private Integer intValue(Object value) {
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (Exception e) {
            return null;
        }
    }
}
