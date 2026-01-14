package com.cubrid.mcp.mcp.tools;

import com.cubrid.mcp.dto.TableInfo;
import com.cubrid.mcp.policy.SqlPolicy;
import com.cubrid.mcp.service.SchemaIntrospector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ListTablesTool implements McpTool {
    private static final Logger logger = LoggerFactory.getLogger(ListTablesTool.class);

    private final SchemaIntrospector schemaIntrospector;
    private final SqlPolicy sqlPolicy;

    @Autowired
    public ListTablesTool(SchemaIntrospector schemaIntrospector, SqlPolicy sqlPolicy) {
        this.schemaIntrospector = schemaIntrospector;
        this.sqlPolicy = sqlPolicy;
    }

    @Override
    public String getName() {
        return "db.listTables";
    }

    @Override
    public String getDescription() {
        return String.format("%s 스키마의 테이블 목록을 조회합니다.", sqlPolicy.getAllowedSchema());
    }

    @Override
    public Map<String, Object> getInputSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        
        Map<String, Object> pattern = new HashMap<>();
        pattern.put("type", "string");
        pattern.put("description", "테이블명 패턴 (LIKE 패턴, 기본값: '%')");
        pattern.put("default", "%");
        properties.put("pattern", pattern);
        
        Map<String, Object> limit = new HashMap<>();
        limit.put("type", "integer");
        limit.put("description", "최대 반환 개수 (기본값: 500)");
        limit.put("default", 500);
        properties.put("limit", limit);
        
        schema.put("properties", properties);
        return schema;
    }

    @Override
    public Object execute(Map<String, Object> params) throws Exception {
        String pattern = (String) params.getOrDefault("pattern", "%");
        Integer limit = null;
        if (params.containsKey("limit")) {
            Object limitObj = params.get("limit");
            if (limitObj instanceof Number) {
                limit = ((Number) limitObj).intValue();
            }
        }
        if (limit == null) {
            limit = 500;
        }

        List<TableInfo> tables = schemaIntrospector.listTables(pattern, limit);
        
        Map<String, Object> result = new HashMap<>();
        result.put("schema", sqlPolicy.getAllowedSchema());
        result.put("tables", tables);
        
        logger.debug("테이블 목록 조회 완료: {}개", tables.size());
        return result;
    }
}
