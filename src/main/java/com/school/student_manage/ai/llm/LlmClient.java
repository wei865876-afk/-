package com.school.student_manage.ai.llm;

import java.util.List;

/**
 * 大模型客户端抽象。实现类需兼容 OpenAI Chat Completions 协议：
 * 远程实现 {@link OpenAiCompatibleClient}，离线兜底实现 {@link HeuristicClient}。
 */
public interface LlmClient {

    /** 客户端标识，例如 deepseek-chat / local-rule-engine */
    String provider();

    /** 当前是否可用 */
    boolean available();

    /**
     * 流式对话。若服务端不支持流式，实现方内部应退化为非流式调用。
     *
     * @param messages 对话上下文（system/user/assistant/tool）
     * @param tools    可用工具声明，可为空列表
     * @param handler  增量文本回调（可传 null）
     */
    ChatResult chat(List<LlmMessage> messages, List<ToolSpec> tools, StreamHandler handler);

    /** 是否支持文本向量 */
    default boolean supportsEmbedding() {
        return false;
    }

    /** 文本向量，不支持时返回 null */
    default float[] embed(String text) {
        return null;
    }

    /** 增量输出回调 */
    interface StreamHandler {
        void onDelta(String text);
    }
}
