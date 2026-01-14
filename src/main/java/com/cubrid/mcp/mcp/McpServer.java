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
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class McpServer {
    private static final Logger logger = LoggerFactory.getLogger(McpServer.class);
    private static final Logger stdoutLogger = LoggerFactory.getLogger("STDOUT");

    private final ObjectMapper objectMapper;
    private final List<McpTool> tools;
    private final List<McpResource> resources;

    @Autowired
    public McpServer(ObjectMapper objectMapper, List<McpTool> tools, List<McpResource> resources) {
        this.objectMapper = objectMapper;
        this.tools = tools;
        this.resources = resources;
    }

    public void start() {
        logger.info("MCP 서버 시작 (STDIO 모드)");
        
        // 로깅은 stderr로만 출력 (stdout은 MCP 메시지 전용)
        System.setErr(System.err); // stderr는 로깅용
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, java.nio.charset.StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(new java.io.OutputStreamWriter(System.out, java.nio.charset.StandardCharsets.UTF_8), true)) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    McpMessage request = objectMapper.readValue(line, McpMessage.class);
                    McpMessage response = processMessage(request);
                    
                    if (response != null) {
                        String responseJson = objectMapper.writeValueAsString(response);
                        writer.println(responseJson);
                        writer.flush();
                    }
                } catch (Exception e) {
                    logger.error("메시지 처리 오류", e);
                    // 에러 응답 전송
                    try {
                        McpMessage errorResponse = createErrorResponse(null, -32603, "Internal error: " + e.getMessage());
                        String errorJson = objectMapper.writeValueAsString(errorResponse);
                        writer.println(errorJson);
                        writer.flush();
                    } catch (Exception ex) {
                        logger.error("에러 응답 전송 실패", ex);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("입출력 오류", e);
        }
    }

    public McpMessage processMessage(McpMessage request) {
        String method = request.getMethod();
        
        if (method == null) {
            return createErrorResponse(request.getId(), -32600, "Invalid Request: method is required");
        }

        switch (method) {
            case "initialize":
                return handleInitialize(request);
            case "tools/list":
                return handleToolsList(request);
            case "tools/call":
                return handleToolsCall(request);
            case "resources/list":
                return handleResourcesList(request);
            case "resources/read":
                return handleResourcesRead(request);
            default:
                return createErrorResponse(request.getId(), -32601, "Method not found: " + method);
        }
    }

    private McpMessage handleInitialize(McpMessage request) {
        Map<String, Object> result = new HashMap<>();
        result.put("protocolVersion", "2024-11-05");
        result.put("capabilities", new HashMap<>());
        result.put("serverInfo", Map.of(
            "name", "cubrid-mcp",
            "version", "1.0.0"
        ));
        
        McpMessage response = new McpMessage();
        response.setId(request.getId());
        response.setResult(result);
        return response;
    }

    private McpMessage handleToolsList(McpMessage request) {
        List<Map<String, Object>> toolsList = tools.stream()
            .map(tool -> {
                Map<String, Object> toolInfo = new HashMap<>();
                toolInfo.put("name", tool.getName());
                toolInfo.put("description", tool.getDescription());
                toolInfo.put("inputSchema", tool.getInputSchema());
                return toolInfo;
            })
            .collect(Collectors.toList());
        
        Map<String, Object> result = new HashMap<>();
        result.put("tools", toolsList);
        
        McpMessage response = new McpMessage();
        response.setId(request.getId());
        response.setResult(result);
        return response;
    }

    private McpMessage handleToolsCall(McpMessage request) {
        Map<String, Object> params = request.getParams();
        if (params == null) {
            return createErrorResponse(request.getId(), -32602, "Invalid params");
        }

        String toolName = (String) params.get("name");
        if (toolName == null) {
            return createErrorResponse(request.getId(), -32602, "Tool name is required");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");
        if (arguments == null) {
            arguments = new HashMap<>();
        }

        McpTool tool = tools.stream()
            .filter(t -> t.getName().equals(toolName))
            .findFirst()
            .orElse(null);

        if (tool == null) {
            return createErrorResponse(request.getId(), -32601, "Tool not found: " + toolName);
        }

        try {
            Object result = tool.execute(arguments);
            
            McpMessage response = new McpMessage();
            response.setId(request.getId());
            response.setResult(result);
            return response;
        } catch (Exception e) {
            logger.error("Tool 실행 오류: {}", toolName, e);
            return createErrorResponse(request.getId(), -32603, "Tool execution failed: " + e.getMessage());
        }
    }

    private McpMessage handleResourcesList(McpMessage request) {
        List<Map<String, Object>> resourcesList = resources.stream()
            .map(resource -> {
                Map<String, Object> resourceInfo = new HashMap<>();
                resourceInfo.put("uri", resource.getUri());
                resourceInfo.put("mimeType", resource.getMimeType());
                resourceInfo.put("description", resource.getDescription());
                return resourceInfo;
            })
            .collect(Collectors.toList());
        
        Map<String, Object> result = new HashMap<>();
        result.put("resources", resourcesList);
        
        McpMessage response = new McpMessage();
        response.setId(request.getId());
        response.setResult(result);
        return response;
    }

    private McpMessage handleResourcesRead(McpMessage request) {
        Map<String, Object> params = request.getParams();
        if (params == null) {
            return createErrorResponse(request.getId(), -32602, "Invalid params");
        }

        String uri = (String) params.get("uri");
        if (uri == null) {
            return createErrorResponse(request.getId(), -32602, "URI is required");
        }

        McpResource resource = resources.stream()
            .filter(r -> {
                String resourceUri = r.getUri();
                // 패턴 매칭 (cubrid://schema/{schema}/{table})
                if (resourceUri.contains("{") && uri.startsWith(resourceUri.substring(0, resourceUri.indexOf("{")))) {
                    return true;
                }
                return resourceUri.equals(uri);
            })
            .findFirst()
            .orElse(null);

        if (resource == null) {
            return createErrorResponse(request.getId(), -32601, "Resource not found: " + uri);
        }

        try {
            String content;
            if (resource instanceof com.cubrid.mcp.mcp.resources.TableResource) {
                content = ((com.cubrid.mcp.mcp.resources.TableResource) resource).getContent(uri);
            } else {
                content = resource.getContent();
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("contents", List.of(Map.of(
                "uri", uri,
                "mimeType", resource.getMimeType(),
                "text", content
            )));
            
            McpMessage response = new McpMessage();
            response.setId(request.getId());
            response.setResult(result);
            return response;
        } catch (Exception e) {
            logger.error("Resource 읽기 오류: {}", uri, e);
            return createErrorResponse(request.getId(), -32603, "Resource read failed: " + e.getMessage());
        }
    }

    private McpMessage createErrorResponse(Object id, int code, String message) {
        McpMessage response = new McpMessage();
        response.setId(id);
        McpMessage.McpError error = new McpMessage.McpError(code, message);
        response.setError(error);
        return response;
    }
}
