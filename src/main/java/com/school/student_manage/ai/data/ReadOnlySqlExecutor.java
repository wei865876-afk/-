package com.school.student_manage.ai.data;

import com.school.student_manage.ai.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 只读 SQL 执行器（NL2SQL 的安全底座）。
 *
 * <p>安全策略（纵深防御）：</p>
 * <ol>
 *   <li>语句白名单：仅允许单条 SELECT / WITH 查询；</li>
 *   <li>关键字黑名单：写操作、DDL、系统表、文件函数、锁行等一律拒绝；</li>
 *   <li>数据表白名单：仅允许学籍业务相关表与视图；</li>
 *   <li>强约束：外层包裹 LIMIT 限制返回行数，PreparedStatement 设置查询超时与只读事务。</li>
 * </ol>
 * 即便模型被提示注入诱导生成危险 SQL，也无法写库或越权读取。
 */
@Component
public class ReadOnlySqlExecutor {

    private static final Logger log = LoggerFactory.getLogger(ReadOnlySqlExecutor.class);

    /** 允许访问的业务表 / 视图 */
    private static final Set<String> ALLOWED_TABLES = new LinkedHashSet<String>(java.util.Arrays.asList(
            "student", "class", "major", "department", "score", "course",
            "reward_punish", "change_apply", "student_change", "archive", "dict",
            "v_student_full_info"));

    /** 禁止出现在语句中的关键字 / 函数 */
    private static final Pattern FORBIDDEN = Pattern.compile(
            "\\b(insert|update|delete|drop|alter|create|truncate|grant|revoke|copy|vacuum|analyze|reindex|"
                    + "call|do|execute|prepare|deallocate|set|reset|begin|commit|rollback|savepoint|"
                    + "into|pg_sleep|pg_read_file|pg_ls_dir|pg_stat|pg_catalog|information_schema|dblink|"
                    + "lo_import|lo_export|current_setting|set_config|password|for\\s+update|for\\s+share)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SELECT_ONLY = Pattern.compile("^(select|with)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TABLE_REF = Pattern.compile(
            "\\b(?:from|join)\\s+(\"?)([a-zA-Z_][a-zA-Z0-9_]*)\\1", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINE_COMMENT = Pattern.compile("--[^\\n]*");
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private AiProperties props;

    /** 校验语句是否安全，返回错误信息；null 表示通过 */
    public String validate(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return "SQL 不能为空";
        }
        String cleaned = BLOCK_COMMENT.matcher(LINE_COMMENT.matcher(sql).replaceAll(" ")).replaceAll(" ").trim();
        // 允许结尾一个分号
        String body = cleaned.endsWith(";") ? cleaned.substring(0, cleaned.length() - 1).trim() : cleaned;
        if (body.contains(";")) {
            return "只允许执行单条查询语句";
        }
        if (!SELECT_ONLY.matcher(body).find()) {
            return "只允许执行 SELECT 查询";
        }
        java.util.regex.Matcher forbidden = FORBIDDEN.matcher(body);
        if (forbidden.find()) {
            return "语句包含不允许的关键字：" + forbidden.group();
        }
        java.util.regex.Matcher ref = TABLE_REF.matcher(body);
        boolean anyTable = false;
        while (ref.find()) {
            anyTable = true;
            String table = ref.group(2).toLowerCase(Locale.ROOT);
            if (!ALLOWED_TABLES.contains(table)) {
                return "不允许访问数据表：" + table;
            }
        }
        if (!anyTable) {
            return "语句未引用任何允许的数据表";
        }
        return null;
    }

    /** 执行只读查询，返回 {sql, columns, rows, rowCount, truncated} */
    public Map<String, Object> execute(String rawSql) {
        String sql = rawSql == null ? "" : rawSql.trim();
        String error = validate(sql);
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
        String body = sql.endsWith(";") ? sql.substring(0, sql.length() - 1).trim() : sql;
        int maxRows = props.getSqlMaxRows();
        // 外层包裹，强制限制返回行数；同时防止子查询里出现写操作
        String wrapped = "SELECT * FROM (\n" + body + "\n) __ai_result LIMIT " + (maxRows + 1);

        long start = System.currentTimeMillis();
        Map<String, Object> result = jdbcTemplate.execute((ConnectionCallback<Map<String, Object>>) con -> {
            boolean originalReadOnly = con.isReadOnly();
            try {
                con.setReadOnly(true);
            } catch (Exception ignore) {
                // 部分驱动 / 连接池不支持
            }
            try (PreparedStatement ps = con.prepareStatement(wrapped)) {
                ps.setQueryTimeout(Math.max(2, props.getSqlTimeoutSeconds()));
                ps.setFetchSize(500);
                try (ResultSet rs = ps.executeQuery()) {
                    ResultSetMetaData md = rs.getMetaData();
                    int columnCount = md.getColumnCount();
                    List<String> columns = new ArrayList<String>();
                    for (int i = 1; i <= columnCount; i++) {
                        columns.add(md.getColumnLabel(i));
                    }
                    List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
                    boolean truncated = false;
                    while (rs.next()) {
                        if (rows.size() >= maxRows) {
                            truncated = true;
                            break;
                        }
                        Map<String, Object> row = new LinkedHashMap<String, Object>();
                        for (int i = 1; i <= columnCount; i++) {
                            row.put(columns.get(i - 1), normalize(rs.getObject(i)));
                        }
                        rows.add(row);
                    }
                    Map<String, Object> out = new LinkedHashMap<String, Object>();
                    out.put("columns", columns);
                    out.put("rows", rows);
                    out.put("rowCount", rows.size());
                    out.put("truncated", truncated);
                    out.put("sql", body);
                    return out;
                }
            } finally {
                try {
                    con.setReadOnly(originalReadOnly);
                } catch (Exception ignore) {
                    // ignore
                }
            }
        });
        if (result != null) {
            result.put("elapsedMs", System.currentTimeMillis() - start);
        }
        log.debug("只读 SQL 执行完成，耗时 {}ms", System.currentTimeMillis() - start);
        return result == null ? new LinkedHashMap<String, Object>() : result;
    }

    private Object normalize(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp) {
            return value.toString().replace(".0", "");
        }
        if (value instanceof java.sql.Date || value instanceof java.math.BigDecimal
                || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof byte[]) {
            return "[binary]";
        }
        String text = String.valueOf(value);
        return text.length() > 500 ? text.substring(0, 500) + "…" : text;
    }

    public Set<String> allowedTables() {
        return ALLOWED_TABLES;
    }
}
