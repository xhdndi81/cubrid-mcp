package com.cubrid.mcp.policy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class SqlPolicy {
    private static final Logger logger = LoggerFactory.getLogger(SqlPolicy.class);
    
    @Value("${policy.allowed-schema:dba}")
    private String allowedSchema;
    
    // 금지된 SQL 키워드 (대소문자 무시)
    private static final Set<String> FORBIDDEN_KEYWORDS = new HashSet<>(Arrays.asList(
        "INSERT", "UPDATE", "DELETE", "MERGE", "DROP", "ALTER", "CREATE", 
        "TRUNCATE", "GRANT", "REVOKE", "CALL", "EXEC", "EXECUTE", "SET", 
        "COMMIT", "ROLLBACK", "SAVEPOINT", "LOCK", "UNLOCK"
    ));
    
    // SELECT 문 시작 패턴 (대소문자 무시, 앞뒤 공백 허용)
    private static final Pattern SELECT_PATTERN = Pattern.compile(
        "^\\s*SELECT\\s+", Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    // 다중 문장 차단 (세미콜론으로 구분된 문장이 2개 이상인지 확인)
    private static final Pattern MULTI_STATEMENT_PATTERN = Pattern.compile(
        ".*;\\s*[^;]+", Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    // 스키마 참조 패턴 (public. 또는 public 스키마가 아닌 다른 스키마)
    private static final Pattern SCHEMA_PATTERN = Pattern.compile(
        "\\b(\\w+)\\.", Pattern.CASE_INSENSITIVE
    );

    @Value("${policy.hard-max-rows:10000}")
    private int hardMaxRows;

    @Value("${policy.hard-max-bytes:20971520}")
    private long hardMaxBytes; // 20MB

    @Value("${policy.hard-timeout-ms:30000}")
    private long hardTimeoutMs;

    /**
     * SQL 문을 검증합니다.
     * 
     * @param sql 검증할 SQL 문
     * @throws PolicyViolationException 정책 위반 시
     */
    public void validate(String sql) throws PolicyViolationException {
        if (sql == null || sql.trim().isEmpty()) {
            throw new PolicyViolationException("SQL 문이 비어있습니다.");
        }

        String normalizedSql = sql.trim();
        
        // 1. 다중 문장 차단
        if (containsMultipleStatements(normalizedSql)) {
            throw new PolicyViolationException("다중 SQL 문은 허용되지 않습니다.");
        }
        
        // 2. SELECT 문인지 확인
        if (!isSelectStatement(normalizedSql)) {
            throw new PolicyViolationException("SELECT 문만 허용됩니다.");
        }
        
        // 3. 금칙어 검사
        if (containsForbiddenKeywords(normalizedSql)) {
            throw new PolicyViolationException("허용되지 않은 SQL 키워드가 포함되어 있습니다.");
        }
        
        // 4. public 스키마 강제
        enforcePublicSchema(normalizedSql);
        
        logger.debug("SQL 검증 통과: {}", normalizedSql.substring(0, Math.min(100, normalizedSql.length())));
    }

    /**
     * 다중 문장인지 확인합니다.
     */
    private boolean containsMultipleStatements(String sql) {
        // 세미콜론이 있고, 그 뒤에 실제 SQL 키워드가 있는지 확인
        String[] parts = sql.split(";");
        int statementCount = 0;
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty() && 
                (trimmed.toUpperCase().startsWith("SELECT") || 
                 trimmed.toUpperCase().matches("^\\s*WITH\\s+.*"))) {
                statementCount++;
            }
        }
        return statementCount > 1;
    }

    /**
     * SELECT 문인지 확인합니다.
     */
    private boolean isSelectStatement(String sql) {
        // SELECT 또는 WITH로 시작하는지 확인 (CTE 지원)
        String upperSql = sql.toUpperCase().trim();
        return upperSql.startsWith("SELECT") || upperSql.startsWith("WITH");
    }

    /**
     * 금지된 키워드가 포함되어 있는지 확인합니다.
     */
    private boolean containsForbiddenKeywords(String sql) {
        String upperSql = sql.toUpperCase();
        for (String keyword : FORBIDDEN_KEYWORDS) {
            // 단어 경계를 고려한 검색
            Pattern pattern = Pattern.compile("\\b" + keyword + "\\b", Pattern.CASE_INSENSITIVE);
            if (pattern.matcher(sql).find()) {
                logger.warn("금지된 키워드 발견: {}", keyword);
                return true;
            }
        }
        return false;
    }

    /**
     * 허용된 스키마를 강제합니다.
     * 다른 스키마가 명시되어 있으면 예외를 발생시킵니다.
     */
    public void enforcePublicSchema(String sql) throws PolicyViolationException {
        java.util.regex.Matcher matcher = SCHEMA_PATTERN.matcher(sql);
        while (matcher.find()) {
            String schema = matcher.group(1);
            if (!allowedSchema.equalsIgnoreCase(schema)) {
                throw new PolicyViolationException(
                    String.format("'%s' 스키마는 허용되지 않습니다. '%s' 스키마만 사용할 수 있습니다.", schema, allowedSchema)
                );
            }
        }
        
        // 스키마가 명시되지 않은 경우, 테이블명 앞에 허용된 스키마를 추가하도록 안내
        // (실제 강제는 쿼리 실행 시점에 처리)
    }

    /**
     * 테이블명에 허용된 스키마를 강제로 추가합니다.
     */
    public String enforcePublicSchemaPrefix(String sql) {
        // 간단한 정규식으로 테이블명 앞에 허용된 스키마 추가
        // FROM, JOIN 등의 뒤에 오는 테이블명 처리
        String result = sql;
        
        // FROM schema.table 또는 FROM table 패턴 처리
        result = Pattern.compile("\\bFROM\\s+([a-zA-Z_][a-zA-Z0-9_]*)", Pattern.CASE_INSENSITIVE)
            .matcher(result)
            .replaceAll(m -> {
                String tableName = m.group(1);
                if (!tableName.equalsIgnoreCase(allowedSchema)) {
                    return "FROM " + allowedSchema + "." + tableName;
                }
                return m.group(0);
            });
        
        // JOIN schema.table 또는 JOIN table 패턴 처리
        result = Pattern.compile("\\bJOIN\\s+([a-zA-Z_][a-zA-Z0-9_]*)", Pattern.CASE_INSENSITIVE)
            .matcher(result)
            .replaceAll(m -> {
                String tableName = m.group(1);
                if (!tableName.equalsIgnoreCase(allowedSchema)) {
                    return "JOIN " + allowedSchema + "." + tableName;
                }
                return m.group(0);
            });
        
        return result;
    }
    
    public String getAllowedSchema() {
        return allowedSchema;
    }

    public int getHardMaxRows() {
        return hardMaxRows;
    }

    public long getHardMaxBytes() {
        return hardMaxBytes;
    }

    public long getHardTimeoutMs() {
        return hardTimeoutMs;
    }

    /**
     * 정책 위반 예외 클래스
     */
    public static class PolicyViolationException extends Exception {
        public PolicyViolationException(String message) {
            super(message);
        }
    }
}
