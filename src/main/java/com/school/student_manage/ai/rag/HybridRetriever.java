package com.school.student_manage.ai.rag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 混合检索器：中文二元分词 + IDF 加权关键词打分，可选叠加向量余弦相似度。
 *
 * <p>设计取舍：面向校内制度文档这种中小规模语料（几十~几百篇），
 * 在 Java 进程内做精确打分比引入向量数据库更轻、更快、更好复现；
 * 配置了 embedding-model 时自动升级为"关键词 + 语义"的混合检索。</p>
 */
public class HybridRetriever {

    private final List<Chunk> chunks = new ArrayList<Chunk>();
    private final Map<String, Integer> documentFrequency = new LinkedHashMap<String, Integer>();

    public void index(List<Chunk> newChunks) {
        chunks.addAll(newChunks);
        rebuildStats();
    }

    public void reset() {
        chunks.clear();
        documentFrequency.clear();
    }

    public int size() {
        return chunks.size();
    }

    private void rebuildStats() {
        documentFrequency.clear();
        for (Chunk chunk : chunks) {
            for (String token : tokenize(chunk.content)) {
                Integer count = documentFrequency.get(token);
                documentFrequency.put(token, count == null ? 1 : count + 1);
            }
        }
    }

    /** 关键词打分 */
    public List<Scored> scoreKeyword(String query, int topK) {
        Set<String> queryTokens = new LinkedHashSet<String>(tokenize(query));
        List<Scored> scored = new ArrayList<Scored>();
        if (queryTokens.isEmpty() || chunks.isEmpty()) {
            return scored;
        }
        int total = chunks.size();
        for (Chunk chunk : chunks) {
            Map<String, Integer> tf = termFrequency(chunk.content);
            double score = 0;
            for (String token : queryTokens) {
                Integer count = tf.get(token);
                if (count == null) {
                    continue;
                }
                Integer df = documentFrequency.get(token);
                double idf = Math.log(1 + (double) total / (1 + (df == null ? 0 : df)));
                score += (1 + Math.log(count)) * idf;
            }
            // 标题 / 标签命中的额外加权
            String title = chunk.title == null ? "" : chunk.title;
            for (String token : queryTokens) {
                if (token.length() >= 2 && title.contains(token)) {
                    score += 1.6;
                }
                if (chunk.tags != null && chunk.tags.contains(token)) {
                    score += 0.8;
                }
            }
            if (score > 0) {
                double normalized = score / Math.sqrt(Math.max(20, chunk.content.length()));
                scored.add(new Scored(chunk, normalized));
            }
        }
        scored.sort((a, b) -> Double.compare(b.score, a.score));
        return scored.size() > topK ? new ArrayList<Scored>(scored.subList(0, topK)) : scored;
    }

    public List<Chunk> allChunks() {
        return chunks;
    }

    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            return 0;
        }
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    /** 中文按二元切分 + 英文数字按词切分 */
    public static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<String>();
        if (text == null) {
            return tokens;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        StringBuilder ascii = new StringBuilder();
        StringBuilder chinese = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) {
                if (ascii.length() > 0) {
                    tokens.add(ascii.toString());
                    ascii.setLength(0);
                }
                chinese.append(c);
            } else if (Character.isLetterOrDigit(c)) {
                if (chinese.length() > 0) {
                    flushChinese(chinese, tokens);
                }
                ascii.append(c);
            } else {
                if (ascii.length() > 1) {
                    tokens.add(ascii.toString());
                }
                ascii.setLength(0);
                if (chinese.length() > 0) {
                    flushChinese(chinese, tokens);
                }
            }
        }
        if (ascii.length() > 1) {
            tokens.add(ascii.toString());
        }
        flushChinese(chinese, tokens);
        return tokens;
    }

    private static void flushChinese(StringBuilder buffer, List<String> tokens) {
        String text = buffer.toString();
        buffer.setLength(0);
        if (text.length() == 1) {
            tokens.add(text);
            return;
        }
        for (int i = 0; i + 1 < text.length(); i++) {
            tokens.add(text.substring(i, i + 2));
        }
    }

    private static Map<String, Integer> termFrequency(String text) {
        Map<String, Integer> tf = new LinkedHashMap<String, Integer>();
        for (String token : tokenize(text)) {
            Integer count = tf.get(token);
            tf.put(token, count == null ? 1 : count + 1);
        }
        return tf;
    }

    /** 知识库片段 */
    public static class Chunk {
        public Integer docId;
        public Integer chunkId;
        public String title;
        public String category;
        public String tags;
        public String source;
        public String content;
        public float[] embedding;

        public Chunk() {
        }

        public Chunk(Integer docId, Integer chunkId, String title, String category,
                     String tags, String source, String content, float[] embedding) {
            this.docId = docId;
            this.chunkId = chunkId;
            this.title = title;
            this.category = category;
            this.tags = tags;
            this.source = source;
            this.content = content;
            this.embedding = embedding;
        }
    }

    /** 打分结果 */
    public static class Scored {
        public final Chunk chunk;
        public final double score;

        public Scored(Chunk chunk, double score) {
            this.chunk = chunk;
            this.score = score;
        }
    }
}
