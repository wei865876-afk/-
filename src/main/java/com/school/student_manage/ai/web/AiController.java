package com.school.student_manage.ai.web;

import com.school.student_manage.ai.config.AiProperties;
import com.school.student_manage.ai.llm.LlmClientRouter;
import com.school.student_manage.ai.rag.KnowledgeService;
import com.school.student_manage.ai.service.AiChatService;
import com.school.student_manage.ai.service.InsightService;
import com.school.student_manage.ai.service.SqlAssistantService;
import com.school.student_manage.ai.store.ConversationStore;
import com.school.student_manage.ai.tool.ToolRegistry;
import com.school.student_manage.ai.tool.ToolResult;
import com.school.student_manage.entity.SysUser;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 能力统一入口。
 *
 * <ul>
 *   <li>GET  /ai/status            运行状态（模式、模型、工具、知识库规模）</li>
 *   <li>POST /ai/chat/stream       Agent 流式对话（SSE）</li>
 *   <li>POST /ai/chat              非流式对话（便于二次开发/接口测试）</li>
 *   <li>POST /ai/insight           看板智能洞察</li>
 *   <li>POST /ai/warning-advice    预警干预建议</li>
 *   <li>POST /ai/sql               自然语言取数（NL2SQL）</li>
 *   <li>CRUD /ai/knowledge*        制度知识库管理</li>
 *   <li>CRUD /ai/conversations*    会话历史</li>
 * </ul>
 */
@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private AiProperties props;

    @Resource
    private LlmClientRouter llmRouter;

    @Resource
    private AiChatService chatService;

    @Resource
    private InsightService insightService;

    @Resource
    private SqlAssistantService sqlAssistantService;

    @Resource
    private KnowledgeService knowledgeService;

    @Resource
    private ToolRegistry toolRegistry;

    @Resource
    private ConversationStore conversationStore;

    @GetMapping("/status")
    public Map<String, Object> status(HttpSession session) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("enabled", props.isEnabled());
        map.put("mode", llmRouter.activeMode());
        map.put("model", llmRouter.describe());
        map.put("baseUrl", props.maskedBaseUrl());
        map.put("embeddingEnabled", llmRouter.supportsEmbedding());
        map.put("localFallback", "local".equals(llmRouter.activeMode()));
        map.put("lastError", llmRouter.lastError());
        map.put("tools", toolRegistry.catalog());
        map.put("knowledgeDocs", knowledgeService.docCount());
        map.put("knowledgeChunks", knowledgeService.chunkCount());
        map.put("stats", conversationStore.stats());
        SysUser user = (SysUser) session.getAttribute("loginUser");
        map.put("user", user == null ? null : user.getRealName() == null ? user.getUsername() : user.getRealName());
        return map;
    }

    /** 手动重试远程模型（清除熔断状态） */
    @PostMapping("/breaker/reset")
    public Map<String, Object> resetBreaker() {
        llmRouter.resetBreaker();
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("mode", llmRouter.activeMode());
        map.put("model", llmRouter.describe());
        return map;
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody AiChatRequest request, HttpSession session) {
        return chatService.chatStream(request, currentUserName(session));
    }

    /** 非流式对话：接口验收 / 自动化测试用 */
    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody AiChatRequest request, HttpSession session) {
        return chatService.chatOnce(request, currentUserName(session));
    }

    @PostMapping("/conversations")
    public Map<String, Object> conversations(@RequestParam(defaultValue = "30") int limit) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("list", conversationStore.list(limit));
        return map;
    }

    @GetMapping("/conversations/{id}")
    public Map<String, Object> conversationDetail(@PathVariable int id) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("conversation", conversationStore.get(id));
        map.put("messages", conversationStore.messages(id));
        return map;
    }

    @DeleteMapping("/conversations/{id}")
    public Map<String, Object> deleteConversation(@PathVariable int id) {
        conversationStore.delete(id);
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("success", true);
        return map;
    }

    @GetMapping("/tools")
    public List<Map<String, Object>> tools() {
        return toolRegistry.catalog();
    }

    @PostMapping("/insight")
    public Map<String, Object> insight() {
        return insightService.dashboardInsight();
    }

    /** 工具直连执行：供前端页面（画像 / 预警 / 取数工作台）复用 Agent 的工具层 */
    @PostMapping("/tool/{name}")
    public ToolResult invokeTool(@PathVariable String name,
                                 @RequestBody(required = false) Map<String, Object> args) {
        return toolRegistry.execute(name, args);
    }

    /** 学生名录查询（等价于 query_students 工具），支持关键字与多条件筛选 */
    @PostMapping("/students")
    public ToolResult students(@RequestBody(required = false) Map<String, Object> body) {
        return toolRegistry.execute("query_students",
                body == null ? new LinkedHashMap<String, Object>() : body);
    }

    /** 学生 360° 画像 + AI 点评（无模型时降级为规则画像） */
    @PostMapping("/students/profile")
    public Map<String, Object> studentProfile(@RequestBody(required = false) Map<String, Object> body) {
        String stuId = body == null || body.get("stuId") == null ? null : String.valueOf(body.get("stuId"));
        String name = body == null || body.get("name") == null ? null : String.valueOf(body.get("name"));
        return insightService.studentPortrait(stuId, name);
    }

    @PostMapping("/warning-advice")
    public Map<String, Object> warningAdvice(@RequestBody(required = false) Map<String, Object> body) {
        String level = body == null || body.get("level") == null ? "all" : String.valueOf(body.get("level"));
        Integer limit = body == null || body.get("limit") == null ? 20 : Integer.valueOf(String.valueOf(body.get("limit")));
        return insightService.warningAdvice(level, limit);
    }

    @PostMapping("/sql")
    public Map<String, Object> sql(@RequestBody Map<String, Object> body) {
        Object question = body == null ? null : body.get("question");
        return sqlAssistantService.ask(question == null ? null : String.valueOf(question));
    }

    // ------------------------------------------------------------- 知识库管理

    @GetMapping("/knowledge")
    public Map<String, Object> knowledgeList() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("list", knowledgeService.list());
        map.put("count", knowledgeService.docCount());
        map.put("chunks", knowledgeService.chunkCount());
        map.put("embeddingEnabled", llmRouter.supportsEmbedding());
        return map;
    }

    @GetMapping("/knowledge/{id}")
    public Map<String, Object> knowledgeDetail(@PathVariable int id) {
        return knowledgeService.get(id);
    }

    @PostMapping("/knowledge")
    public Map<String, Object> knowledgeSave(@RequestBody Map<String, Object> body) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        try {
            int id = knowledgeService.save(body);
            map.put("success", true);
            map.put("id", id);
            map.put("count", knowledgeService.docCount());
        } catch (Exception e) {
            map.put("success", false);
            map.put("message", e.getMessage());
        }
        return map;
    }

    @DeleteMapping("/knowledge/{id}")
    public Map<String, Object> knowledgeDelete(@PathVariable int id) {
        knowledgeService.delete(id);
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("success", true);
        map.put("count", knowledgeService.docCount());
        return map;
    }

    @PostMapping("/knowledge/reindex")
    public Map<String, Object> knowledgeReindex() {
        int count = knowledgeService.reindexAll();
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("success", true);
        map.put("docs", count);
        map.put("chunks", knowledgeService.chunkCount());
        return map;
    }

    @PostMapping("/knowledge/reset")
    public Map<String, Object> knowledgeReset() {
        int count = knowledgeService.resetToDemo();
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("success", true);
        map.put("docs", count);
        map.put("chunks", knowledgeService.chunkCount());
        return map;
    }

    @PostMapping("/knowledge/search")
    public Map<String, Object> knowledgeSearch(@RequestBody Map<String, Object> body) {
        String query = body == null || body.get("query") == null ? "" : String.valueOf(body.get("query"));
        int topK = body == null || body.get("topK") == null ? 5 : Integer.parseInt(String.valueOf(body.get("topK")));
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("query", query);
        map.put("hits", knowledgeService.searchAsMaps(query, topK));
        return map;
    }

    private String currentUserName(HttpSession session) {
        SysUser user = (SysUser) session.getAttribute("loginUser");
        if (user == null) {
            return "访客";
        }
        return user.getRealName() == null || user.getRealName().isEmpty() ? user.getUsername() : user.getRealName();
    }
}
