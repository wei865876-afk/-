package com.school.student_manage.ai.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具执行结果的统一信封。所有工具都返回该结构，便于：
 * <ul>
 *   <li>回填给大模型作为 tool 消息；</li>
 *   <li>前端按 {@code render} 自动选择渲染方式（表格 / 指标卡 / 图表 / 文本）；</li>
 *   <li>本地兜底模式（无大模型）直接由该结构拼装自然语言答案。</li>
 * </ul>
 */
public class ToolResult {

    /** 工具名 */
    private String tool;
    /** 中文展示名 */
    private String title;
    /** 渲染方式：table | metrics | list | chart | text */
    private String render = "text";
    /** 是否成功 */
    private boolean success = true;
    /** 一句话结论 */
    private String summary;
    /** 主体数据：{columns, rows} 或 {metrics:[{label,value,unit}]} 等 */
    private Object data;
    /** 给模型的补充建议 */
    private List<String> hints;
    /** 图表元数据：{type:'bar', xKey:'className', series:[{key:'avgScore', name:'平均分'}], unit:'分'} */
    private Map<String, Object> chart;
    /** 失败原因 */
    private String error;
    /** 耗时（毫秒） */
    private long durationMs;

    public static ToolResult of(String tool, String title, String render, String summary) {
        ToolResult r = new ToolResult();
        r.tool = tool;
        r.title = title;
        r.render = render;
        r.summary = summary;
        return r;
    }

    public static ToolResult table(String tool, String title, String summary,
                                   List<String> columns, List<Map<String, Object>> rows) {
        ToolResult r = of(tool, title, "table", summary);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("columns", columns);
        data.put("rows", rows);
        data.put("total", rows == null ? 0 : rows.size());
        r.data = data;
        return r;
    }

    public static ToolResult metrics(String tool, String title, String summary, Map<String, Object> metrics) {
        ToolResult r = of(tool, title, "metrics", summary);
        r.data = metrics;
        return r;
    }

    public static ToolResult fail(String tool, String title, String error) {
        ToolResult r = of(tool, title, "text", "执行失败");
        r.success = false;
        r.error = error;
        return r;
    }

    public ToolResult hints(String... items) {
        if (this.hints == null) {
            this.hints = new ArrayList<String>();
        }
        for (String item : items) {
            this.hints.add(item);
        }
        return this;
    }

    public ToolResult chart(Map<String, Object> chart) {
        this.chart = chart;
        return this;
    }

    /** 提供给大模型的压缩文本（避免上下文过长） */
    public String toModelText() {
        StringBuilder sb = new StringBuilder();
        sb.append(success ? "执行成功" : "执行失败");
        if (summary != null) {
            sb.append(" | ").append(summary);
        }
        if (error != null) {
            sb.append(" | 错误：").append(error);
        }
        if (data != null) {
            String json = JsonSupport.toJson(data);
            sb.append("\n数据：").append(json.length() > 4000 ? json.substring(0, 4000) + "…" : json);
        }
        if (hints != null && !hints.isEmpty()) {
            sb.append("\n提示：").append(String.join("；", hints));
        }
        return sb.toString();
    }

    public String getTool() {
        return tool;
    }

    public void setTool(String tool) {
        this.tool = tool;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getRender() {
        return render;
    }

    public void setRender(String render) {
        this.render = render;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public List<String> getHints() {
        return hints;
    }

    public void setHints(List<String> hints) {
        this.hints = hints;
    }

    public Map<String, Object> getChart() {
        return chart;
    }

    public void setChart(Map<String, Object> chart) {
        this.chart = chart;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }
}
