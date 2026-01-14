package com.cubrid.mcp.mcp.tools;

import com.cubrid.mcp.policy.SqlPolicy;
import com.cubrid.mcp.service.SchemaIntrospector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class DescribeTableTool implements McpTool {
    private static final Logger logger = LoggerFactory.getLogger(DescribeTableTool.class);

    private final SchemaIntrospector schemaIntrospector;
    private final SqlPolicy sqlPolicy;

    @Autowired
    public DescribeTableTool(SchemaIntrospector schemaIntrospector, SqlPolicy sqlPolicy) {
        this.schemaIntrospector = schemaIntrospector;
        this.sqlPolicy = sqlPolicy;
    }

    @Override
    public String getName() {
        return "db.describeTable";
    }

    @Override
    public String getDescription() {
        return "테이블의 스키마 정보(컬럼, PK, 인덱스)를 조회합니다.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        
        Map<String, Object> table = new HashMap<>();
        table.put("type", "string");
        table.put("description", "테이블명 (스키마 없이)");
        properties.put("table", table);
        
        schema.put("properties", properties);
        schema.put("required", new String[]{"table"});
        return schema;
    }

    @Override
    public Object execute(Map<String, Object> params) throws Exception {
        String tableName = (String) params.get("table");
        if (tableName == null || tableName.isEmpty()) {
            throw new IllegalArgumentException("테이블명이 필요합니다.");
        }

        // 스키마 접두사 제거 (있다면)
        String allowedSchema = sqlPolicy.getAllowedSchema() + ".";
        if (tableName.startsWith(allowedSchema)) {
            tableName = tableName.substring(allowedSchema.length());
        }

        Map<String, Object> result = schemaIntrospector.describeTable(tableName);
        
        logger.debug("테이블 스키마 조회 완료: {}", tableName);
        return result;
    }
}
