package com.cubrid.mcp.mcp.tools;

import com.cubrid.mcp.dto.PingResult;
import com.cubrid.mcp.service.QueryExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
public class PingTool implements McpTool {
    private static final Logger logger = LoggerFactory.getLogger(PingTool.class);

    private final DataSource dataSource;
    private final String jdbcUrl;

    @Autowired
    public PingTool(DataSource dataSource, @Value("${cubrid.jdbc.url:}") String jdbcUrl) {
        this.dataSource = dataSource;
        this.jdbcUrl = jdbcUrl;
    }

    @Override
    public String getName() {
        return "db.ping";
    }

    @Override
    public String getDescription() {
        return "데이터베이스 연결 상태를 확인합니다.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return new HashMap<>(); // 입력 파라미터 없음
    }

    @Override
    public Object execute(Map<String, Object> params) throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {
            
            if (rs.next()) {
                // DB 이름 추출: jdbc:cubrid:host:port:dbname:user:password
                String dbName = extractDbNameFromUrl();
                if (dbName == null || dbName.isEmpty()) {
                    dbName = conn.getCatalog(); // fallback
                }
                if (dbName == null || dbName.isEmpty()) {
                    dbName = "unknown";
                }
                
                PingResult result = new PingResult(
                    true,
                    Instant.now().toString(),
                    dbName
                );
                
                logger.debug("Ping 성공: db={}", dbName);
                return result;
            } else {
                throw new Exception("SELECT 1 쿼리가 결과를 반환하지 않았습니다.");
            }
        } catch (Exception e) {
            logger.error("Ping 실패", e);
            throw new Exception("데이터베이스 연결 실패: " + e.getMessage(), e);
        }
    }
    
    private String extractDbNameFromUrl() {
        if (jdbcUrl == null || jdbcUrl.isEmpty()) {
            // 환경변수에서 확인
            String envUrl = System.getenv("CUBRID_JDBC_URL");
            if (envUrl != null && !envUrl.isEmpty()) {
                return parseDbName(envUrl);
            }
            return null;
        }
        return parseDbName(jdbcUrl);
    }
    
    private String parseDbName(String url) {
        // jdbc:cubrid:host:port:dbname:user:password 형식
        try {
            if (url.startsWith("jdbc:cubrid:")) {
                String[] parts = url.substring(12).split(":");
                if (parts.length >= 3) {
                    return parts[2]; // dbname은 세 번째 부분
                }
            }
        } catch (Exception e) {
            logger.debug("JDBC URL에서 DB 이름 추출 실패: {}", e.getMessage());
        }
        return null;
    }
}
