package com.cubrid.mcp.service;

import com.cubrid.mcp.dto.ColumnInfo;
import com.cubrid.mcp.dto.QueryResult;
import com.cubrid.mcp.policy.SqlPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Service
public class QueryExecutor {
    private static final Logger logger = LoggerFactory.getLogger(QueryExecutor.class);

    private final DataSource dataSource;
    private final SqlPolicy sqlPolicy;

    @Autowired
    public QueryExecutor(DataSource dataSource, SqlPolicy sqlPolicy) {
        this.dataSource = dataSource;
        this.sqlPolicy = sqlPolicy;
    }

    /**
     * SELECT 쿼리를 실행하고 결과를 반환합니다.
     * 
     * @param sql SQL 쿼리
     * @param maxRows 최대 행 수 (null이면 무제한, 하드 상한 적용)
     * @param maxBytes 최대 바이트 수 (null이면 무제한, 하드 상한 적용)
     * @param timeoutMs 타임아웃 밀리초 (null이면 기본값, 하드 상한 적용)
     * @return 쿼리 결과
     * @throws SQLException SQL 실행 오류
     * @throws SqlPolicy.PolicyViolationException 정책 위반
     */
    public QueryResult executeQuery(String sql, Integer maxRows, Long maxBytes, Long timeoutMs) 
            throws SQLException, SqlPolicy.PolicyViolationException {
        
        // 1. SQL 정책 검사
        sqlPolicy.validate(sql);
        
        // 2. public 스키마 강제 (스키마가 없으면 추가)
        String enforcedSql = sqlPolicy.enforcePublicSchemaPrefix(sql);
        
        // 3. 제한 값 적용 (하드 상한 고려)
        int effectiveMaxRows = (maxRows != null && maxRows > 0) 
            ? Math.min(maxRows, sqlPolicy.getHardMaxRows()) 
            : sqlPolicy.getHardMaxRows();
        
        long effectiveMaxBytes = (maxBytes != null && maxBytes > 0)
            ? Math.min(maxBytes, sqlPolicy.getHardMaxBytes())
            : sqlPolicy.getHardMaxBytes();
        
        long effectiveTimeout = (timeoutMs != null && timeoutMs > 0)
            ? Math.min(timeoutMs, sqlPolicy.getHardTimeoutMs())
            : sqlPolicy.getHardTimeoutMs();

        logger.debug("쿼리 실행: maxRows={}, maxBytes={}, timeoutMs={}", 
                    effectiveMaxRows, effectiveMaxBytes, effectiveTimeout);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(enforcedSql)) {
            
            // 타임아웃 설정
            stmt.setQueryTimeout((int) (effectiveTimeout / 1000));
            
            // 최대 행 수 설정
            if (effectiveMaxRows > 0) {
                stmt.setMaxRows(effectiveMaxRows);
            }
            
            long startTime = System.currentTimeMillis();
            try (ResultSet rs = stmt.executeQuery()) {
                return processResultSet(rs, effectiveMaxRows, effectiveMaxBytes);
            } finally {
                long elapsed = System.currentTimeMillis() - startTime;
                logger.debug("쿼리 실행 완료: {}ms", elapsed);
            }
        }
    }

    /**
     * ResultSet을 처리하여 QueryResult로 변환합니다.
     */
    private QueryResult processResultSet(ResultSet rs, int maxRows, long maxBytes) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        
        // 컬럼 정보 추출
        List<ColumnInfo> columns = new ArrayList<>();
        for (int i = 1; i <= columnCount; i++) {
            String columnName = metaData.getColumnName(i);
            String columnType = metaData.getColumnTypeName(i);
            boolean nullable = (metaData.isNullable(i) == ResultSetMetaData.columnNullable);
            columns.add(new ColumnInfo(columnName, columnType, nullable));
        }
        
        // 행 데이터 추출
        List<List<Object>> rows = new ArrayList<>();
        long totalBytes = 0;
        boolean truncated = false;
        int rowCount = 0;
        
        while (rs.next()) {
            if (maxRows > 0 && rowCount >= maxRows) {
                truncated = true;
                break;
            }
            
            List<Object> row = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                Object value = rs.getObject(i);
                row.add(value);
                
                // 바이트 수 추정 (대략적)
                if (value != null) {
                    totalBytes += estimateBytes(value);
                }
            }
            
            rows.add(row);
            rowCount++;
            
            // 바이트 제한 확인
            if (maxBytes > 0 && totalBytes > maxBytes) {
                truncated = true;
                logger.warn("결과 크기 제한 초과: {} bytes (제한: {} bytes)", totalBytes, maxBytes);
                break;
            }
        }
        
        return new QueryResult(columns, rows, rowCount, truncated);
    }

    /**
     * 객체의 바이트 수를 추정합니다.
     */
    private long estimateBytes(Object value) {
        if (value == null) {
            return 0;
        }
        
        if (value instanceof String) {
            return ((String) value).getBytes().length;
        } else if (value instanceof Number) {
            return 8; // 숫자 타입은 대략 8바이트
        } else if (value instanceof Boolean) {
            return 1;
        } else if (value instanceof Date || value instanceof Time || value instanceof Timestamp) {
            return 8;
        } else {
            // 기타 타입은 toString() 길이로 추정
            return value.toString().getBytes().length;
        }
    }
}
