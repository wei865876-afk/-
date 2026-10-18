package com.school.student_manage.ai.tool.impl;

import com.school.student_manage.ai.data.ReadOnlySqlExecutor;
import com.school.student_manage.ai.tool.AiTool;
import com.school.student_manage.ai.tool.JsonSupport;
import com.school.student_manage.ai.tool.Schemas;
import com.school.student_manage.ai.tool.ToolResult;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自然语言取数（NL2SQL）：由大模型生成只读 SQL，交给 {@link ReadOnlySqlExecutor} 安全执行。
 * 工具会把 SQL 一并返回给前端展示，做到"答案可追溯、口径可核对"。
 */
@Component
public class SqlQueryTool implements AiTool {

    @Resource
    private ReadOnlySqlExecutor executor;

    @Override
    public String name() {
        return "run_sql";
    }

    @Override
    public String title() {
        return "只读数据查询(NL2SQL)";
    }

    @Override
    public String description() {
        return "当固定工具无法满足需求时，生成只读 SELECT 语句查询数据库（PostgreSQL 方言）。"
                + "只允许单条 SELECT，禁止任何写操作、DDL 与系统表；表名需使用白名单内的业务表。"
                + "返回内容包含 columns / rows，可直接用于统计、分组、交叉分析。"
                + "示例：统计每个院系女生人数 -> {sql:'SELECT d.dept_name, COUNT(*) FROM student s ... GROUP BY 1', purpose:'各院系女生人数'}。";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        Map<String, Object> props = new LinkedHashMap<String, Object>();
        props.put("sql", Schemas.string("只读 SELECT 语句（PostgreSQL 语法，不要包含分号之外的多个语句）"));
        props.put("purpose", Schemas.string("本次查询的业务目的，用于向用户解释"));
        return Schemas.object(props, "sql");
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        String sql = args.get("sql") == null ? null : String.valueOf(args.get("sql")).trim();
        String purpose = args.get("purpose") == null ? null : String.valueOf(args.get("purpose")).trim();
        if (sql == null || sql.isEmpty()) {
            return ToolResult.fail(name(), title(), "缺少 sql 参数");
        }
        String error = executor.validate(sql);
        if (error != null) {
            return ToolResult.fail(name(), title(), "SQL 被安全策略拒绝：" + error);
        }
        Map<String, Object> data = executor.execute(sql);
        Object columnsRaw = data.get("columns");
        List<?> columns = columnsRaw instanceof List ? (List<?>) columnsRaw : new ArrayList<Object>();
        Object rowsRaw = data.get("rows");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = rowsRaw instanceof List ? (List<Map<String, Object>>) rowsRaw : new ArrayList<Map<String, Object>>();

        List<String> columnNames = new ArrayList<String>();
        for (Object column : columns) {
            columnNames.add(String.valueOf(column));
        }

        StringBuilder summary = new StringBuilder();
        summary.append(purpose == null ? "数据查询完成" : purpose);
        summary.append("，返回 ").append(data.get("rowCount")).append(" 行");
        if (Boolean.TRUE.equals(data.get("truncated"))) {
            summary.append("（已达返回上限，结果被截断）");
        }

        ToolResult result = ToolResult.table(name(), title(), summary.toString(), columnNames, rows);
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("sql", data.get("sql"));
        payload.put("purpose", purpose);
        payload.put("columns", columnNames);
        payload.put("rows", rows);
        payload.put("total", data.get("rowCount"));
        payload.put("truncated", data.get("truncated"));
        payload.put("elapsedMs", data.get("elapsedMs"));
        result.setData(payload);
        result.setRender("sql");
        result.setChart(autoChart(columnNames, rows));
        if (Boolean.TRUE.equals(data.get("truncated"))) {
            result.hints("结果行数达到上限，可加上 WHERE 条件或聚合函数缩小结果集");
        }
        result.hints("向用户展示生成的 SQL，便于核对统计口径");
        return result;
    }

    /** 结果两列且一列数值时，自动生成图表元数据，实现"查询即可视化" */
    private Map<String, Object> autoChart(List<String> columns, List<Map<String, Object>> rows) {
        if (columns.size() != 2 || rows.isEmpty() || rows.size() > 30) {
            return null;
        }
        String first = columns.get(0);
        String second = columns.get(1);
        Object sample = rows.get(0).get(second);
        if (!(sample instanceof Number)) {
            return null;
        }
        Map<String, Object> chart = new LinkedHashMap<String, Object>();
        chart.put("type", "bar");
        chart.put("xKey", first);
        chart.put("unit", "");
        List<Map<String, Object>> series = new ArrayList<Map<String, Object>>();
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("key", second);
        item.put("name", second);
        series.add(item);
        chart.put("series", series);
        return chart;
    }

    /** 供其它服务复用的执行入口 */
    public Map<String, Object> runRaw(String sql) {
        return JsonSupport.parseObject(JsonSupport.toJson(executor.execute(sql)));
    }
}
