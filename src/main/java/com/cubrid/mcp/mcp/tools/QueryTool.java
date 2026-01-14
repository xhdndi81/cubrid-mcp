package com.cubrid.mcp.mcp.tools;

import com.cubrid.mcp.policy.SqlPolicy;
import com.cubrid.mcp.service.QueryExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class QueryTool implements McpTool {
    private static final Logger logger = LoggerFactory.getLogger(QueryTool.class);

    private final QueryExecutor queryExecutor;
    private final SqlPolicy sqlPolicy;

    @Autowired
    public QueryTool(QueryExecutor queryExecutor, SqlPolicy sqlPolicy) {
        this.queryExecutor = queryExecutor;
        this.sqlPolicy = sqlPolicy;
    }

    @Override
    public String getName() {
        return "db.query";
    }

    @Override
    public String getDescription() {
        return String.format("SELECT 쿼리를 실행하고 결과를 반환합니다. %s 스키마만 허용됩니다.", sqlPolicy.getAllowedSchema());
    }

    @Override
    public Map<String, Object> getInputSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        
        Map<String, Object> sql = new HashMap<>();
        sql.put("type", "string");
        sql.put("description", "실행할 SELECT SQL 쿼리");
        properties.put("sql", sql);
        
        Map<String, Object> maxRows = new HashMap<>();
        maxRows.put("type", "integer");
        maxRows.put("description", "최대 행 수 (기본값: 없음, 하드 상한 적용)");
        properties.put("maxRows", maxRows);
        
        Map<String, Object> maxBytes = new HashMap<>();
        maxBytes.put("type", "integer");
        maxBytes.put("description", "최대 바이트 수 (기본값: 없음, 하드 상한 적용)");
        properties.put("maxBytes", maxBytes);
        
        Map<String, Object> timeoutMs = new HashMap<>();
        timeoutMs.put("type", "integer");
        timeoutMs.put("description", "타임아웃 밀리초 (기본값: 없음, 하드 상한 적용)");
        properties.put("timeoutMs", timeoutMs);
        
        schema.put("properties", properties);
        schema.put("required", new String[]{"sql"});
        return schema;
    }

    @Override
    public Object execute(Map<String, Object> params) throws Exception {
        String sql = (String) params.get("sql");
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL 쿼리가 필요합니다.");
        }

        Integer maxRows = null;
        if (params.containsKey("maxRows")) {
            Object maxRowsObj = params.get("maxRows");
            if (maxRowsObj instanceof Number) {
                maxRows = ((Number) maxRowsObj).intValue();
            }
        }

        Long maxBytes = null;
        if (params.containsKey("maxBytes")) {
            Object maxBytesObj = params.get("maxBytes");
            if (maxBytesObj instanceof Number) {
                maxBytes = ((Number) maxBytesObj).longValue();
            }
        }

        Long timeoutMs = null;
        if (params.containsKey("timeoutMs")) {
            Object timeoutMsObj = params.get("timeoutMs");
            if (timeoutMsObj instanceof Number) {
                timeoutMs = ((Number) timeoutMsObj).longValue();
            }
        }

        try {
            return queryExecutor.executeQuery(sql, maxRows, maxBytes, timeoutMs);
        } catch (com.cubrid.mcp.policy.SqlPolicy.PolicyViolationException e) {
            logger.warn("정책 위반: {}", e.getMessage());
            throw new Exception("SQL 정책 위반: " + e.getMessage(), e);
        }
    }
}
