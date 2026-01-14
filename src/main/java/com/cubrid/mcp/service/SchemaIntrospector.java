package com.cubrid.mcp.service;

import com.cubrid.mcp.dto.ColumnInfo;
import com.cubrid.mcp.dto.TableInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SchemaIntrospector {
    private static final Logger logger = LoggerFactory.getLogger(SchemaIntrospector.class);

    private final DataSource dataSource;
    private final com.cubrid.mcp.policy.SqlPolicy sqlPolicy;

    @Autowired
    public SchemaIntrospector(DataSource dataSource, com.cubrid.mcp.policy.SqlPolicy sqlPolicy) {
        this.dataSource = dataSource;
        this.sqlPolicy = sqlPolicy;
    }
    
    private String getAllowedSchema() {
        return sqlPolicy.getAllowedSchema();
    }

    /**
     * 허용된 스키마의 테이블 목록을 조회합니다.
     * 
     * @param pattern 테이블명 패턴 (LIKE 패턴, 예: "%", "user%")
     * @param limit 최대 반환 개수
     * @return 테이블 정보 목록
     */
    public List<TableInfo> listTables(String pattern, Integer limit) throws SQLException {
        List<TableInfo> tables = new ArrayList<>();
        
        if (pattern == null || pattern.isEmpty()) {
            pattern = "%";
        }
        
        int maxResults = (limit != null && limit > 0) ? limit : 500;
        String allowedSchema = getAllowedSchema();

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            
            // 허용된 스키마의 테이블만 조회
            try (ResultSet rs = metaData.getTables(null, allowedSchema, pattern, new String[]{"TABLE", "VIEW"})) {
                int count = 0;
                while (rs.next() && count < maxResults) {
                    String tableName = rs.getString("TABLE_NAME");
                    String tableType = rs.getString("TABLE_TYPE");
                    
                    tables.add(new TableInfo(tableName, tableType));
                    count++;
                }
            }
        }

        logger.debug("테이블 목록 조회 완료: {}개 (pattern={}, limit={})", tables.size(), pattern, limit);
        return tables;
    }

    /**
     * 테이블의 상세 정보를 조회합니다.
     * 
     * @param tableName 테이블명 (스키마 없이)
     * @return 테이블 스키마 정보 (컬럼, PK, 인덱스)
     */
    public Map<String, Object> describeTable(String tableName) throws SQLException {
        if (tableName == null || tableName.isEmpty()) {
            throw new IllegalArgumentException("테이블명이 필요합니다.");
        }

        String allowedSchema = getAllowedSchema();
        Map<String, Object> result = new HashMap<>();
        result.put("schema", allowedSchema);
        result.put("table", tableName);

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            
            // 컬럼 정보 조회
            List<ColumnInfo> columns = getColumns(metaData, tableName);
            result.put("columns", columns);
            
            // Primary Key 조회
            List<String> primaryKeys = getPrimaryKeys(metaData, tableName);
            result.put("primaryKey", primaryKeys);
            
            // 인덱스 정보 조회
            List<Map<String, Object>> indexes = getIndexes(metaData, tableName);
            result.put("indexes", indexes);
        }

        logger.debug("테이블 스키마 조회 완료: {}.{}", allowedSchema, tableName);
        return result;
    }

    /**
     * 테이블의 컬럼 정보를 조회합니다.
     */
    private List<ColumnInfo> getColumns(DatabaseMetaData metaData, String tableName) throws SQLException {
        List<ColumnInfo> columns = new ArrayList<>();
        String allowedSchema = getAllowedSchema();
        
        try (ResultSet rs = metaData.getColumns(null, allowedSchema, tableName, null)) {
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                String typeName = rs.getString("TYPE_NAME");
                int dataType = rs.getInt("DATA_TYPE");
                int nullable = rs.getInt("NULLABLE");
                int columnSize = rs.getInt("COLUMN_SIZE");
                
                // 타입 문자열 구성
                String fullType = typeName;
                if (columnSize > 0 && !isFixedSizeType(dataType)) {
                    fullType += "(" + columnSize + ")";
                }
                
                boolean isNullable = (nullable == DatabaseMetaData.columnNullable);
                
                columns.add(new ColumnInfo(columnName, fullType, isNullable));
            }
        }
        
        return columns;
    }

    /**
     * Primary Key 컬럼을 조회합니다.
     */
    private List<String> getPrimaryKeys(DatabaseMetaData metaData, String tableName) throws SQLException {
        List<String> primaryKeys = new ArrayList<>();
        String allowedSchema = getAllowedSchema();
        
        try (ResultSet rs = metaData.getPrimaryKeys(null, allowedSchema, tableName)) {
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                primaryKeys.add(columnName);
            }
        }
        
        return primaryKeys;
    }

    /**
     * 인덱스 정보를 조회합니다.
     */
    private List<Map<String, Object>> getIndexes(DatabaseMetaData metaData, String tableName) throws SQLException {
        List<Map<String, Object>> indexes = new ArrayList<>();
        Map<String, Map<String, Object>> indexMap = new HashMap<>();
        String allowedSchema = getAllowedSchema();
        
        try (ResultSet rs = metaData.getIndexInfo(null, allowedSchema, tableName, false, false)) {
            while (rs.next()) {
                String indexName = rs.getString("INDEX_NAME");
                if (indexName == null) {
                    continue; // 통계 정보 등은 제외
                }
                
                String columnName = rs.getString("COLUMN_NAME");
                short ordinalPosition = rs.getShort("ORDINAL_POSITION");
                boolean nonUnique = rs.getBoolean("NON_UNIQUE");
                
                if (!indexMap.containsKey(indexName)) {
                    Map<String, Object> indexInfo = new HashMap<>();
                    indexInfo.put("name", indexName);
                    indexInfo.put("unique", !nonUnique);
                    indexInfo.put("columns", new ArrayList<String>());
                    indexMap.put(indexName, indexInfo);
                }
                
                @SuppressWarnings("unchecked")
                List<String> columns = (List<String>) indexMap.get(indexName).get("columns");
                // ORDINAL_POSITION에 따라 정렬된 위치에 추가
                int pos = ordinalPosition - 1;
                while (columns.size() <= pos) {
                    columns.add(null);
                }
                columns.set(pos, columnName);
            }
        }
        
        // null 제거 및 인덱스 정보 구성
        for (Map<String, Object> indexInfo : indexMap.values()) {
            @SuppressWarnings("unchecked")
            List<String> columns = (List<String>) indexInfo.get("columns");
            columns.removeIf(c -> c == null);
            indexes.add(indexInfo);
        }
        
        return indexes;
    }

    /**
     * 고정 크기 타입인지 확인합니다.
     */
    private boolean isFixedSizeType(int dataType) {
        // CUBRID의 고정 크기 타입들
        return dataType == Types.INTEGER || 
               dataType == Types.BIGINT ||
               dataType == Types.SMALLINT ||
               dataType == Types.TINYINT ||
               dataType == Types.DOUBLE ||
               dataType == Types.FLOAT ||
               dataType == Types.DATE ||
               dataType == Types.TIME ||
               dataType == Types.TIMESTAMP ||
               dataType == Types.BOOLEAN;
    }
}
