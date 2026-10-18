package com.school.student_manage.ai.llm;

import com.school.student_manage.ai.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 模型路由与降级：
 * <ol>
 *   <li>配置了可用密钥 → 走远程大模型；</li>
 *   <li>未配置或远程调用失败（网络异常/额度不足）→ 自动降级到本地规则引擎，保证功能不中断；</li>
 *   <li>降级过程对前端透明，仅通过 meta 事件中的 mode 字段告知。</li>
 * </ol>
 */
@Component
@Primary
public class LlmClientRouter implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClientRouter.class);

    private final OpenAiCompatibleClient remote;
    private final HeuristicClient local;
    private final AiProperties props;

    /** 连续失败次数，达到阈值后本进程内不再尝试远程，避免每次请求都等待超时 */
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private static final int FAILURE_THRESHOLD = 3;
    private volatile String lastError;

    public LlmClientRouter(OpenAiCompatibleClient remote, HeuristicClient local, AiProperties props) {
        this.remote = remote;
        this.local = local;
        this.props = props;
    }

    /** 当前生效模式：remote / local */
    public String activeMode() {
        if (!props.isRemote()) {
            return "local";
        }
        return consecutiveFailures.get() >= FAILURE_THRESHOLD ? "local" : "remote";
    }

    public String describe() {
        return "remote".equals(activeMode()) ? remote.provider() : "本地规则引擎（离线兜底）";
    }

    public String lastError() {
        return lastError;
    }

    /** 重置熔断状态（前端可手动重试远程） */
    public void resetBreaker() {
        consecutiveFailures.set(0);
        lastError = null;
    }

    @Override
    public String provider() {
        return describe();
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public ChatResult chat(List<LlmMessage> messages, List<ToolSpec> tools, StreamHandler handler) {
        if (!"remote".equals(activeMode()) || !remote.available()) {
            return local.chat(messages, tools, handler);
        }
        final AtomicBoolean emitted = new AtomicBoolean(false);
        StreamHandler wrapped = text -> {
            emitted.set(true);
            if (handler != null) {
                handler.onDelta(text);
            }
        };
        try {
            ChatResult result = remote.chat(messages, tools, wrapped);
            consecutiveFailures.set(0);
            lastError = null;
            return result;
        } catch (Exception e) {
            consecutiveFailures.incrementAndGet();
            lastError = e.getMessage();
            log.warn("远程模型调用失败（第 {} 次），已降级本地引擎：{}", consecutiveFailures.get(), e.getMessage());
            if (emitted.get()) {
                // 已经输出过内容，无法安全重放，向用户说明
                throw new RuntimeException("大模型调用中断：" + e.getMessage() + "，请稍后重试或切换本地模式");
            }
            return local.chat(messages, tools, handler);
        }
    }

    @Override
    public boolean supportsEmbedding() {
        return remote.supportsEmbedding() && consecutiveFailures.get() < FAILURE_THRESHOLD;
    }

    @Override
    public float[] embed(String text) {
        if (!supportsEmbedding()) {
            return null;
        }
        return remote.embed(text);
    }
}
