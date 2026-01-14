package com.cubrid.mcp.mcp.resources;

import com.cubrid.mcp.policy.SqlPolicy;
import com.cubrid.mcp.service.SchemaIntrospector;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TableResource implements McpResource {
    private static final Logger logger = LoggerFactory.getLogger(TableResource.class);

    private final SchemaIntrospector schemaIntrospector;
    private final SqlPolicy sqlPolicy;
    private final ObjectMapper objectMapper;

    @Autowired
    public TableResource(SchemaIntrospector schemaIntrospector, SqlPolicy sqlPolicy) {
        this.schemaIntrospector = schemaIntrospector;
        this.sqlPolicy = sqlPolicy;
        this.objectMapper = new ObjectMapper();
    }
    
    private Pattern getUriPattern() {
        String allowedSchema = sqlPolicy.getAllowedSchema();
        return Pattern.compile("cubrid://schema/" + Pattern.quote(allowedSchema) + "/(.+)");
    }

    @Override
    public String getUri() {
        return String.format("cubrid://schema/%s/{table}", sqlPolicy.getAllowedSchema());
    }

    @Override
    public String getMimeType() {
        return "application/json";
    }

    @Override
    public String getDescription() {
        return "테이블 스키마 정보 (읽기 전용)";
    }

    @Override
    public String getContent() throws Exception {
        throw new UnsupportedOperationException("테이블명이 필요합니다. getContent(String uri)를 사용하세요.");
    }

    public String getContent(String uri) throws Exception {
        Matcher matcher = getUriPattern().matcher(uri);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("잘못된 URI 형식: " + uri);
        }

        String tableName = matcher.group(1);
        Map<String, Object> tableInfo = schemaIntrospector.describeTable(tableName);
        
        logger.debug("테이블 리소스 생성 완료: {}", tableName);
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(tableInfo);
    }
}
