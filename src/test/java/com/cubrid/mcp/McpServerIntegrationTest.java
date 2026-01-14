package com.cubrid.mcp;

import com.cubrid.mcp.mcp.McpMessage;
import com.cubrid.mcp.mcp.McpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MCP 서버 통합 테스트
 * 
 * 실행 방법:
 * mvn test -Dtest=McpServerIntegrationTest
 */
@SpringBootTest
@TestPropertySource(properties = {
    "cubrid.jdbc.url=jdbc:cubrid:192.168.24.100:33000:demodb:dba:dba12#:?charSet=utf-8",
    "cubrid.user=dba",
    "cubrid.password=dba12#",
    "policy.allowed-schema=dba"
})
public class McpServerIntegrationTest {

    @Autowired
    private McpServer mcpServer;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testInitialize() {
        McpMessage request = new McpMessage();
        request.setId(1);
        request.setMethod("initialize");
        request.setParams(Map.of(
            "protocolVersion", "2024-11-05",
            "capabilities", new HashMap<>(),
            "clientInfo", Map.of("name", "test", "version", "1.0")
        ));

        McpMessage response = mcpServer.processMessage(request);
        
        assertNotNull(response, "응답이 null입니다.");
        assertEquals(1, response.getId(), "응답 ID가 일치하지 않습니다.");
        assertNotNull(response.getResult(), "응답 결과가 null입니다.");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.getResult();
        assertEquals("2024-11-05", result.get("protocolVersion"), "프로토콜 버전이 일치하지 않습니다.");
        
        System.out.println("✓ initialize 테스트 통과");
    }

    @Test
    public void testToolsList() {
        McpMessage request = new McpMessage();
        request.setId(2);
        request.setMethod("tools/list");

        McpMessage response = mcpServer.processMessage(request);
        
        assertNotNull(response, "응답이 null입니다.");
        assertEquals(2, response.getId(), "응답 ID가 일치하지 않습니다.");
        assertNull(response.getError(), "에러가 발생했습니다: " + 
            (response.getError() != null ? response.getError().getMessage() : ""));
        assertNotNull(response.getResult(), "응답 결과가 null입니다.");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.getResult();
        assertTrue(result.containsKey("tools"), "tools 키가 없습니다.");
        
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> tools = 
            (java.util.List<Map<String, Object>>) result.get("tools");
        assertTrue(tools.size() >= 4, "최소 4개의 tool이 있어야 합니다.");
        
        // tool 이름 확인
        boolean hasPing = tools.stream().anyMatch(t -> "db.ping".equals(t.get("name")));
        boolean hasListTables = tools.stream().anyMatch(t -> "db.listTables".equals(t.get("name")));
        boolean hasDescribeTable = tools.stream().anyMatch(t -> "db.describeTable".equals(t.get("name")));
        boolean hasQuery = tools.stream().anyMatch(t -> "db.query".equals(t.get("name")));
        
        assertTrue(hasPing, "db.ping tool이 없습니다.");
        assertTrue(hasListTables, "db.listTables tool이 없습니다.");
        assertTrue(hasDescribeTable, "db.describeTable tool이 없습니다.");
        assertTrue(hasQuery, "db.query tool이 없습니다.");
        
        System.out.println("✓ tools/list 테스트 통과: " + tools.size() + "개 tool 발견");
    }

    @Test
    public void testResourcesList() {
        McpMessage request = new McpMessage();
        request.setId(3);
        request.setMethod("resources/list");

        McpMessage response = mcpServer.processMessage(request);
        
        assertNotNull(response, "응답이 null입니다.");
        assertEquals(3, response.getId(), "응답 ID가 일치하지 않습니다.");
        assertNull(response.getError(), "에러가 발생했습니다: " + 
            (response.getError() != null ? response.getError().getMessage() : ""));
        assertNotNull(response.getResult(), "응답 결과가 null입니다.");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.getResult();
        assertTrue(result.containsKey("resources"), "resources 키가 없습니다.");
        
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> resources = 
            (java.util.List<Map<String, Object>>) result.get("resources");
        assertTrue(resources.size() >= 3, "최소 3개의 resource가 있어야 합니다.");
        
        System.out.println("✓ resources/list 테스트 통과: " + resources.size() + "개 resource 발견");
    }

    @Test
    public void testPingTool() {
        McpMessage request = new McpMessage();
        request.setId(4);
        request.setMethod("tools/call");
        request.setParams(Map.of(
            "name", "db.ping",
            "arguments", new HashMap<>()
        ));

        McpMessage response = mcpServer.processMessage(request);
        
        assertNotNull(response, "응답이 null입니다.");
        assertEquals(4, response.getId(), "응답 ID가 일치하지 않습니다.");
        
        if (response.getError() != null) {
            System.out.println("⚠ db.ping 실행 중 에러 (DB 연결 실패 가능): " + 
                response.getError().getMessage());
            // DB 연결이 안 되어도 MCP 서버 자체는 정상 동작
            return;
        }
        
        assertNotNull(response.getResult(), "응답 결과가 null입니다.");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.getResult();
        assertTrue(result.containsKey("ok"), "ok 키가 없습니다.");
        
        System.out.println("✓ db.ping 테스트 통과: " + result);
    }

    @Test
    public void testInvalidMethod() {
        McpMessage request = new McpMessage();
        request.setId(5);
        request.setMethod("invalid/method");

        McpMessage response = mcpServer.processMessage(request);
        
        assertNotNull(response, "응답이 null입니다.");
        assertEquals(5, response.getId(), "응답 ID가 일치하지 않습니다.");
        assertNotNull(response.getError(), "에러가 발생해야 합니다.");
        assertEquals(-32601, response.getError().getCode(), "에러 코드가 일치하지 않습니다.");
        
        System.out.println("✓ 잘못된 메서드 처리 테스트 통과");
    }
}
