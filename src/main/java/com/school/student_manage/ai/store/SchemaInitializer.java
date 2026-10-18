package com.school.student_manage.ai.store;

import com.school.student_manage.ai.rag.KnowledgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * AI 模块自建表初始化：会话、消息、知识库、知识片段。
 * 使用 CREATE TABLE IF NOT EXISTS，随应用启动幂等执行，不影响既有业务表。
 */
@Component
public class SchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);

    private static final String[] DDL = new String[]{
            "CREATE TABLE IF NOT EXISTS ai_conversation ("
                    + "id SERIAL PRIMARY KEY, "
                    + "title VARCHAR(200), "
                    + "scene VARCHAR(30), "
                    + "user_name VARCHAR(50), "
                    + "message_count INT DEFAULT 0, "
                    + "created_at TIMESTAMP DEFAULT NOW(), "
                    + "updated_at TIMESTAMP DEFAULT NOW())",
            "CREATE TABLE IF NOT EXISTS ai_message ("
                    + "id SERIAL PRIMARY KEY, "
                    + "conversation_id INT NOT NULL REFERENCES ai_conversation(id) ON DELETE CASCADE, "
                    + "role VARCHAR(20) NOT NULL, "
                    + "content TEXT, "
                    + "tool_calls TEXT, "
                    + "tool_results TEXT, "
                    + "citations TEXT, "
                    + "mode VARCHAR(20), "
                    + "model VARCHAR(100), "
                    + "created_at TIMESTAMP DEFAULT NOW())",
            "CREATE INDEX IF NOT EXISTS idx_ai_message_conversation ON ai_message(conversation_id, id)",
            "CREATE TABLE IF NOT EXISTS ai_knowledge ("
                    + "id SERIAL PRIMARY KEY, "
                    + "title VARCHAR(200) NOT NULL, "
                    + "category VARCHAR(50), "
                    + "tags VARCHAR(200), "
                    + "content TEXT NOT NULL, "
                    + "source VARCHAR(200), "
                    + "chunk_count INT DEFAULT 0, "
                    + "indexed_at TIMESTAMP, "
                    + "create_time TIMESTAMP DEFAULT NOW())",
            "CREATE TABLE IF NOT EXISTS ai_knowledge_chunk ("
                    + "id SERIAL PRIMARY KEY, "
                    + "doc_id INT NOT NULL REFERENCES ai_knowledge(id) ON DELETE CASCADE, "
                    + "seq INT NOT NULL, "
                    + "content TEXT NOT NULL, "
                    + "embedding TEXT, "
                    + "create_time TIMESTAMP DEFAULT NOW())",
            "CREATE INDEX IF NOT EXISTS idx_ai_chunk_doc ON ai_knowledge_chunk(doc_id, seq)"
    };

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private KnowledgeService knowledgeService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            for (String ddl : DDL) {
                jdbcTemplate.execute(ddl);
            }
            log.info("AI 模块数据表检查完成");
            int seeded = knowledgeService.seedFromClasspathIfEmpty();
            if (seeded > 0) {
                log.info("已初始化内置制度知识库，共 {} 篇文档", seeded);
            } else {
                knowledgeService.ensureLoaded();
            }
        } catch (Exception e) {
            log.error("AI 模块初始化失败（不影响既有功能）：{}", e.getMessage());
        }
    }
}
