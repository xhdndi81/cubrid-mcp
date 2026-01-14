package com.cubrid.mcp.mcp.resources;

import com.cubrid.mcp.policy.SqlPolicy;
import com.cubrid.mcp.service.SchemaIntrospector;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class SchemaSummaryResource implements McpResource {
    private static final Logger logger = LoggerFactory.getLogger(SchemaSummaryResource.class);

    private final SchemaIntrospector schemaIntrospector;
    private final SqlPolicy sqlPolicy;
    private final ObjectMapper objectMapper;

    @Autowired
    public SchemaSummaryResource(SchemaIntrospector schemaIntrospector, SqlPolicy sqlPolicy) {
        this.schemaIntrospector = schemaIntrospector;
        this.sqlPolicy = sqlPolicy;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getUri() {
        return "cubrid://schema/summary";
    }

    @Override
    public String getMimeType() {
        return "application/json";
    }

    @Override
    public String getDescription() {
        return String.format("%s 스키마의 테이블 요약 정보", sqlPolicy.getAllowedSchema());
    }

    @Override
    public String getContent() throws Exception {
        List<com.cubrid.mcp.dto.TableInfo> tables = schemaIntrospector.listTables("%", 100);
        String allowedSchema = sqlPolicy.getAllowedSchema();
        
        Map<String, Object> summary = new HashMap<>();
        summary.put("schema", allowedSchema);
        summary.put("tableCount", tables.size());
        summary.put("tables", tables);
        
        logger.debug("스키마 요약 생성 완료: {}개 테이블", tables.size());
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(summary);
    }
}
