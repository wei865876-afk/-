package com.school.student_manage.ai.agent;

import com.school.student_manage.ai.tool.ToolResult;

import java.util.LinkedHashMap;
import java.util.Map;

/** Agent 执行步骤（一次工具调用），用于前端"思考过程"可视化 */
public class AgentStep {

    private String id;
    private String tool;
    private String title;
    private Map<String, Object> args;
    /** running / success / failed */
    private String status = "running";
    private String summary;
    private String error;
    private long durationMs;
    private String render;
    private Object data;
    private Map<String, Object> chart;

    public AgentStep(String id, String tool, String title, Map<String, Object> args) {
        this.id = id;
        this.tool = tool;
        this.title = title;
        this.args = args;
    }

    public void complete(ToolResult result) {
        this.status = result.isSuccess() ? "success" : "failed";
        this.summary = result.getSummary();
        this.error = result.getError();
        this.durationMs = result.getDurationMs();
        this.render = result.getRender();
        this.data = result.getData();
        this.chart = result.getChart();
    }

    /** 转成事件负载 */
    public Map<String, Object> toEvent() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("id", id);
        map.put("tool", tool);
        map.put("title", title);
        map.put("args", args);
        map.put("status", status);
        map.put("summary", summary);
        map.put("error", error);
        map.put("durationMs", durationMs);
        map.put("render", render);
        map.put("data", data);
        map.put("chart", chart);
        return map;
    }

    public String getId() {
        return id;
    }

    public String getTool() {
        return tool;
    }

    public String getTitle() {
        return title;
    }

    public Map<String, Object> getArgs() {
        return args;
    }

    public String getStatus() {
        return status;
    }

    public String getSummary() {
        return summary;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public String getRender() {
        return render;
    }

    public Object getData() {
        return data;
    }

    public Map<String, Object> getChart() {
        return chart;
    }
}
