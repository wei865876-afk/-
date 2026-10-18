package com.school.student_manage.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 能力配置。
 *
 * <p>兼容所有 OpenAI Chat Completions 协议的服务：DeepSeek、通义千问(DashScope 兼容模式)、
 * 智谱 GLM、Moonshot、OpenAI、以及本地 vLLM / Ollama(/v1) 等，只需改 base-url + model。</p>
 *
 * <p>当 api-key 为空或 mode=local 时，自动退化为内置的本地规则引擎（{@code HeuristicClient}），
 * 保证离线情况下全部功能仍可演示。</p>
 */
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /** 是否启用 AI 模块 */
    private boolean enabled = true;

    /** auto: 有 key 走远程，无 key 走本地 | remote: 强制远程 | local: 强制本地 */
    private String mode = "auto";

    /** OpenAI 兼容服务地址，需带版本号前缀，例如 https://api.deepseek.com/v1 */
    private String baseUrl = "https://api.deepseek.com/v1";

    /** 密钥，建议通过环境变量 AI_API_KEY 注入，不要提交到仓库 */
    private String apiKey = "";

    /** 对话模型 */
    private String model = "deepseek-chat";

    /** 向量模型（可选，用于知识库语义检索）；为空则知识库使用关键词+IDF 检索 */
    private String embeddingModel = "";

    /** 采样温度 */
    private double temperature = 0.3;

    /** 单次回答最大 token */
    private int maxTokens = 1600;

    /** 单次 HTTP 请求超时（秒） */
    private int timeoutSeconds = 90;

    /** Agent 最多工具调用轮次 */
    private int maxToolRounds = 5;

    /** 只读 SQL 查询最大返回行数 */
    private int sqlMaxRows = 200;

    /** 只读 SQL 查询超时（秒） */
    private int sqlTimeoutSeconds = 8;

    /** 知识库检索返回片段数 */
    private int ragTopK = 4;

    /** 组织名（用于系统提示词） */
    private String orgName = "示例大学";

    /** 是否为远程模式 */
    public boolean isRemote() {
        if (!enabled) {
            return false;
        }
        if ("remote".equalsIgnoreCase(mode)) {
            return true;
        }
        if ("local".equalsIgnoreCase(mode)) {
            return false;
        }
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    /** 是否具备向量能力 */
    public boolean isEmbeddingEnabled() {
        return isRemote() && embeddingModel != null && !embeddingModel.trim().isEmpty();
    }

    /** 不含密钥的展示用地址 */
    public String maskedBaseUrl() {
        return baseUrl;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxToolRounds() {
        return maxToolRounds;
    }

    public void setMaxToolRounds(int maxToolRounds) {
        this.maxToolRounds = maxToolRounds;
    }

    public int getSqlMaxRows() {
        return sqlMaxRows;
    }

    public void setSqlMaxRows(int sqlMaxRows) {
        this.sqlMaxRows = sqlMaxRows;
    }

    public int getSqlTimeoutSeconds() {
        return sqlTimeoutSeconds;
    }

    public void setSqlTimeoutSeconds(int sqlTimeoutSeconds) {
        this.sqlTimeoutSeconds = sqlTimeoutSeconds;
    }

    public int getRagTopK() {
        return ragTopK;
    }

    public void setRagTopK(int ragTopK) {
        this.ragTopK = ragTopK;
    }

    public String getOrgName() {
        return orgName;
    }

    public void setOrgName(String orgName) {
        this.orgName = orgName;
    }
}
