package com.cubrid.mcp;

import com.cubrid.mcp.mcp.tools.*;
import com.cubrid.mcp.policy.SqlPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MCP Tools 기능 테스트
 * 
 * 실행 방법:
 * mvn test -Dtest=McpToolsTest
 */
@SpringBootTest
@TestPropertySource(properties = {
    "cubrid.jdbc.url=jdbc:cubrid:192.168.24.100:33000:demodb:dba:dba12#:?charSet=utf-8",
    "cubrid.user=dba",
    "cubrid.password=dba12#",
    "policy.allowed-schema=dba"
})
public class McpToolsTest {
    
    @Autowired
    private SqlPolicy sqlPolicy;

    @Autowired
    private PingTool pingTool;

    @Autowired
    private ListTablesTool listTablesTool;

    @Autowired
    private DescribeTableTool describeTableTool;

    @Autowired
    private QueryTool queryTool;

    @Test
    public void testPingTool() throws Exception {
        assertNotNull(pingTool, "PingTool이 null입니다.");
        
        Object result = pingTool.execute(new HashMap<>());
        assertNotNull(result, "Ping 결과가 null입니다.");
        
        System.out.println("Ping 결과: " + result);
    }

    @Test
    public void testListTablesTool() throws Exception {
        assertNotNull(listTablesTool, "ListTablesTool이 null입니다.");
        
        Map<String, Object> params = new HashMap<>();
        params.put("pattern", "%");
        params.put("limit", 10);
        
        Object result = listTablesTool.execute(params);
        assertNotNull(result, "테이블 목록 결과가 null입니다.");
        
        System.out.println("테이블 목록: " + result);
    }

    @Test
    public void testDescribeTableTool() throws Exception {
        assertNotNull(describeTableTool, "DescribeTableTool이 null입니다.");
        
        // 먼저 테이블 목록을 가져와서 첫 번째 테이블을 조회
        Map<String, Object> listParams = new HashMap<>();
        listParams.put("pattern", "%");
        listParams.put("limit", 1);
        
        Object listResult = listTablesTool.execute(listParams);
        if (listResult != null && listResult instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) listResult;
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> tables = (java.util.List<Map<String, Object>>) resultMap.get("tables");
            
            if (tables != null && !tables.isEmpty()) {
                String tableName = (String) tables.get(0).get("name");
                
                Map<String, Object> describeParams = new HashMap<>();
                describeParams.put("table", tableName);
                
                Object result = describeTableTool.execute(describeParams);
                assertNotNull(result, "테이블 스키마 결과가 null입니다.");
                
                System.out.println("테이블 스키마 (" + tableName + "): " + result);
            } else {
                System.out.println("테스트할 테이블이 없습니다.");
            }
        }
    }

    @Test
    public void testQueryTool() throws Exception {
        assertNotNull(queryTool, "QueryTool이 null입니다.");
        
        Map<String, Object> params = new HashMap<>();
        params.put("sql", "SELECT 1 as test_value");
        
        Object result = queryTool.execute(params);
        assertNotNull(result, "쿼리 결과가 null입니다.");
        
        System.out.println("쿼리 결과: " + result);
    }

    @Test
    public void testQueryToolWithPublicSchema() throws Exception {
        // 먼저 테이블 목록을 가져와서 첫 번째 테이블로 쿼리 테스트
        Map<String, Object> listParams = new HashMap<>();
        listParams.put("pattern", "%");
        listParams.put("limit", 1);
        
        Object listResult = listTablesTool.execute(listParams);
        if (listResult != null && listResult instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) listResult;
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> tables = (java.util.List<Map<String, Object>>) resultMap.get("tables");
            
            if (tables != null && !tables.isEmpty()) {
                String tableName = (String) tables.get(0).get("name");
                String allowedSchema = sqlPolicy.getAllowedSchema();
                
                Map<String, Object> queryParams = new HashMap<>();
                queryParams.put("sql", "SELECT * FROM " + allowedSchema + "." + tableName + " LIMIT 5");
                queryParams.put("maxRows", 5);
                
                Object result = queryTool.execute(queryParams);
                assertNotNull(result, "쿼리 결과가 null입니다.");
                
                System.out.println("테이블 쿼리 결과 (" + tableName + "): " + result);
            } else {
                System.out.println("테스트할 테이블이 없습니다.");
            }
        }
    }
}
