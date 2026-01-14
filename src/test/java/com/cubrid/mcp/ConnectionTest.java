package com.cubrid.mcp;

import com.cubrid.mcp.policy.SqlPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CUBRID 데이터베이스 연결 테스트
 * 
 * 실행 방법:
 * mvn test -Dtest=ConnectionTest
 */
@SpringBootTest
@TestPropertySource(properties = {
    "cubrid.jdbc.url=jdbc:cubrid:192.168.24.100:33000:demodb:dba:dba12#:?charSet=utf-8",
    "cubrid.user=dba",
    "cubrid.password=dba12#",
    "policy.allowed-schema=dba"
})
public class ConnectionTest {

    @Autowired
    private DataSource dataSource;
    
    @Autowired
    private SqlPolicy sqlPolicy;

    @Test
    public void testConnection() throws Exception {
        assertNotNull(dataSource, "DataSource가 null입니다.");
        
        try (Connection conn = dataSource.getConnection()) {
            assertNotNull(conn, "Connection이 null입니다.");
            assertFalse(conn.isClosed(), "Connection이 닫혀있습니다.");
            
            // 데이터베이스 메타데이터 확인
            DatabaseMetaData metaData = conn.getMetaData();
            System.out.println("데이터베이스 제품명: " + metaData.getDatabaseProductName());
            System.out.println("데이터베이스 버전: " + metaData.getDatabaseProductVersion());
            System.out.println("JDBC 드라이버명: " + metaData.getDriverName());
            System.out.println("JDBC 드라이버 버전: " + metaData.getDriverVersion());
            
            // 간단한 쿼리 실행
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 1")) {
                assertTrue(rs.next(), "쿼리 결과가 없습니다.");
                assertEquals(1, rs.getInt(1), "SELECT 1 결과가 1이 아닙니다.");
            }
            
            System.out.println("연결 테스트 성공!");
        }
    }

    @Test
    public void testListTables() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            
            String allowedSchema = sqlPolicy.getAllowedSchema();
            System.out.println("\n=== " + allowedSchema + " 스키마 테이블 목록 ===");
            try (ResultSet rs = metaData.getTables(null, allowedSchema, "%", new String[]{"TABLE", "VIEW"})) {
                int count = 0;
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    String tableType = rs.getString("TABLE_TYPE");
                    System.out.println(String.format("%d. %s (%s)", ++count, tableName, tableType));
                }
                if (count == 0) {
                    System.out.println("테이블이 없습니다.");
                }
            }
        }
    }
}
