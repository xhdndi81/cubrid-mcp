package com.cubrid.mcp.mcp;

import com.cubrid.mcp.mcp.resources.McpResource;
import com.cubrid.mcp.mcp.tools.McpTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class McpServer {
    private static final Logger logger = LoggerFactory.getLogger(McpServer.class);

    private final ObjectMapper objectMapper;
    private final List<McpTool> tools;
    private final List<McpResource> resources;
    private PrintStream mcpOut;

    @Autowired
    public McpServer(ObjectMapper objectMapper, List<McpTool> tools, List<McpResource> resources) {
        this.objectMapper = objectMapper;
        this.tools = tools;
        this.resources = resources;
    }

    public void start(PrintStream outStream) {
        this.mcpOut = outStream;
        logger.info(">>> MCP 서버 루프 시작 (도구: {}개, 리소스: {}개)", tools.size(), resources.size());
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                
                try {
                    McpMessage request = objectMapper.readValue(line, McpMessage.class);
                    if (request.getId() == null) {
                        continue;
                    }
                    
                    McpMessage response = processMessage(request);
                    if (response != null) {
                        sendResponse(response);
                    }
                } catch (Exception e) {
                    logger.error(">>> 메시지 처리 오류", e);
                }
            }
        } catch (IOException e) {
            logger.error(">>> 입출력 오류", e);
        }
    }

    private void sendResponse(McpMessage response) {
        try {
            byte[] responseBytes = objectMapper.writeValueAsBytes(response);
            synchronized (mcpOut) {
                mcpOut.write(responseBytes);
                mcpOut.write('\n');
                mcpOut.flush();
            }
        } catch (IOException e) {
            logger.error(">>> 응답 전송 실패", e);
        }
    }

    public McpMessage processMessage(McpMessage request) {
        String method = request.getMethod();
        if (method == null) return null;

        switch (method) {
            case "initialize": return handleInitialize(request);
            case "tools/list": return handleToolsList(request);
            case "tools/call": return handleToolsCall(request);
            case "resources/list": return handleResourcesList(request);
            case "resources/read": return handleResourcesRead(request);
            case "resources/templates/list": return handleResourcesTemplatesList(request);
            case "mcp/getInstructions": return handleGetInstructions(request);
            case "prompts/list": return handlePromptsList(request);
            default: return createErrorResponse(request.getId(), -32601, "Method not found: " + method);
        }
    }

    private McpMessage handleInitialize(McpMessage request) {
        Map<String, Object> result = new HashMap<>();
        result.put("protocolVersion", "2024-11-05");
        result.put("capabilities", Map.of(
            "tools", Map.of("listChanged", false),
            "resources", Map.of("subscribe", false, "listChanged", false),
            "prompts", Map.of("listChanged", false)
        ));
        result.put("serverInfo", Map.of("name", "cubrid-mcp", "version", "1.0.0"));
        
        McpMessage response = new McpMessage();
        response.setId(request.getId());
        response.setResult(result);
        return response;
    }

    private McpMessage handleGetInstructions(McpMessage request) {
        String instructions = 
            "당신은 CUBRID 데이터베이스 전문가입니다. 현재 연결된 'cubrid-posart' MCP 서버를 통해 DB를 조작할 수 있습니다.\n" +
            "1. 먼저 `cubrid://schema/summary` 리소스를 읽어 전체 테이블 목록을 파악하세요.\n" +
            "2. 특정 테이블의 구조가 궁금하면 `db.describeTable` 도구를 사용하세요.\n" +
            "3. 데이터를 조회하려면 `db.query` 도구를 사용하세요. 이때 SQL은 반드시 SELECT 문이어야 하며 정책을 준수해야 합니다.\n" +
            "4. 연결 상태를 확인하려면 `db.ping`을 사용하세요.";
        
        McpMessage response = new McpMessage();
        response.setId(request.getId());
        response.setResult(instructions);
        return response;
    }

    private McpMessage handlePromptsList(McpMessage request) {
        List<Map<String, Object>> prompts = List.of(
            Map.of(
                "name", "analyze-database",
                "description", "CUBRID 데이터베이스의 전체 구조를 분석합니다.",
                "arguments", List.of()
            )
        );
        McpMessage response = new McpMessage();
        response.setId(request.getId());
        response.setResult(Map.of("prompts", prompts));
        return response;
    }

    private McpMessage handleToolsList(McpMessage request) {
        List<Map<String, Object>> toolsList = new ArrayList<>();
        for (McpTool tool : tools) {
            Map<String, Object> info = new HashMap<>();
            info.put("name", tool.getName());
            info.put("description", tool.getDescription());
            Map<String, Object> schema = tool.getInputSchema();
            info.put("inputSchema", (schema == null || schema.isEmpty()) ? 
                     Map.of("type", "object", "properties", new HashMap<>()) : schema);
            toolsList.add(info);
        }
        McpMessage response = new McpMessage();
        response.setId(request.getId());
        response.setResult(Map.of("tools", toolsList));
        return response;
    }

    private McpMessage handleToolsCall(McpMessage request) {
        Map<String, Object> params = request.getParams();
        String toolName = (String) params.get("name");
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");

        McpTool tool = tools.stream().filter(t -> t.getName().equals(toolName)).findFirst().orElse(null);
        if (tool == null) return createErrorResponse(request.getId(), -32601, "Tool not found");

        try {
            Object result = tool.execute(arguments != null ? arguments : new HashMap<>());
            McpMessage response = new McpMessage();
            response.setId(request.getId());
            response.setResult(result);
            return response;
        } catch (Exception e) {
            return createErrorResponse(request.getId(), -32603, e.getMessage());
        }
    }

    private McpMessage handleResourcesList(McpMessage request) {
        List<Map<String, Object>> resList = new ArrayList<>();
        for (McpResource res : resources) {
            if (!res.getUri().contains("{")) {
                resList.add(Map.of(
                    "uri", res.getUri(),
                    "name", res.getDescription(),
                    "mimeType", res.getMimeType(),
                    "description", res.getDescription()
                ));
            }
        }
        McpMessage response = new McpMessage();
        response.setId(request.getId());
        response.setResult(Map.of("resources", resList));
        return response;
    }

    private McpMessage handleResourcesTemplatesList(McpMessage request) {
        List<Map<String, Object>> templates = new ArrayList<>();
        for (McpResource res : resources) {
            if (res.getUri().contains("{")) {
                templates.add(Map.of(
                    "uriTemplate", res.getUri(),
                    "name", res.getDescription(),
                    "description", res.getDescription()
                ));
            }
        }
        McpMessage response = new McpMessage();
        response.setId(request.getId());
        response.setResult(Map.of("resourceTemplates", templates));
        return response;
    }

    private McpMessage handleResourcesRead(McpMessage request) {
        String uri = (String) request.getParams().get("uri");
        McpResource resource = resources.stream()
            .filter(r -> r.getUri().equals(uri) || (r.getUri().contains("{") && uri.startsWith(r.getUri().substring(0, r.getUri().indexOf("{")))))
            .findFirst().orElse(null);

        if (resource == null) return createErrorResponse(request.getId(), -32601, "Resource not found");

        try {
            String content = (resource instanceof com.cubrid.mcp.mcp.resources.TableResource) ? 
                             ((com.cubrid.mcp.mcp.resources.TableResource) resource).getContent(uri) : resource.getContent();
            
            McpMessage response = new McpMessage();
            response.setId(request.getId());
            response.setResult(Map.of("contents", List.of(Map.of(
                "uri", uri, "mimeType", resource.getMimeType(), "text", content
            ))));
            return response;
        } catch (Exception e) {
            return createErrorResponse(request.getId(), -32603, e.getMessage());
        }
    }

    private McpMessage createErrorResponse(Object id, int code, String message) {
        McpMessage response = new McpMessage();
        response.setId(id);
        response.setError(new McpMessage.McpError(code, message));
        return response;
    }
}
