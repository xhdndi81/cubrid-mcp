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
import java.nio.charset.StandardCharsets;
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

    @Autowired
    public McpServer(ObjectMapper objectMapper, List<McpTool> tools, List<McpResource> resources) {
        this.objectMapper = objectMapper;
        this.tools = tools;
        this.resources = resources;
        logger.info(">>> MCP Server 초기화됨 (도구: {}개)", tools.size());
    }

    public void start() {
        logger.info(">>> MCP 서버 루프 시작");
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                
                try {
                    McpMessage request = objectMapper.readValue(line, McpMessage.class);
                    
                    if (request.getId() == null) {
                        processNotification(request);
                        continue;
                    }
                    
                    McpMessage response = processMessage(request);
                    if (response != null) {
                        sendResponse(response, request.getMethod());
                    }
                } catch (Exception e) {
                    logger.error(">>> 메시지 처리 오류", e);
                }
            }
        } catch (IOException e) {
            logger.error(">>> 입출력 오류", e);
        }
    }

    private void sendResponse(McpMessage response, String method) {
        try {
            byte[] responseBytes = objectMapper.writeValueAsBytes(response);
            synchronized (System.out) {
                System.out.write(responseBytes);
                System.out.write('\n');
                System.out.flush();
            }
            logger.debug(">>> [SEND-OK] id={}, method={}", response.getId(), method);
        } catch (IOException e) {
            logger.error(">>> 응답 전송 실패", e);
        }
    }

    private void processNotification(McpMessage notification) {
        String method = notification.getMethod();
        if ("notifications/initialized".equals(method)) {
            logger.debug("클라이언트 초기화 완료");
        }
    }

    public McpMessage processMessage(McpMessage request) {
        String method = request.getMethod();
        if (method == null) return null;

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
        
        Map<String, Object> capabilities = new HashMap<>();
        capabilities.put("tools", Map.of("listChanged", false));
        capabilities.put("resources", Map.of("subscribe", false, "listChanged", false));
        
        result.put("capabilities", capabilities);
        result.put("serverInfo", Map.of("name", "cubrid-mcp", "version", "1.0.0"));
        
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
                
                Map<String, Object> schema = tool.getInputSchema();
                if (schema == null || schema.isEmpty()) {
                    schema = Map.of("type", "object", "properties", new HashMap<>());
                }
                toolInfo.put("inputSchema", schema);
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
        if (params == null) return createErrorResponse(request.getId(), -32602, "Params required");
        
        String toolName = (String) params.get("name");
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");

        McpTool tool = tools.stream()
            .filter(t -> t.getName().equals(toolName))
            .findFirst()
            .orElse(null);

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
        List<Map<String, Object>> resourcesList = resources.stream()
            .map(resource -> {
                Map<String, Object> info = new HashMap<>();
                info.put("uri", resource.getUri());
                info.put("mimeType", resource.getMimeType());
                info.put("description", resource.getDescription());
                return info;
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
        if (params == null) return createErrorResponse(request.getId(), -32602, "Params required");
        
        String uri = (String) params.get("uri");
        McpResource resource = resources.stream()
            .filter(r -> r.getUri().equals(uri) || (r.getUri().contains("{") && uri.startsWith(r.getUri().substring(0, r.getUri().indexOf("{")))))
            .findFirst()
            .orElse(null);

        if (resource == null) return createErrorResponse(request.getId(), -32601, "Resource not found");

        try {
            String content = (resource instanceof com.cubrid.mcp.mcp.resources.TableResource) ? 
                             ((com.cubrid.mcp.mcp.resources.TableResource) resource).getContent(uri) : resource.getContent();
            
            Map<String, Object> contentMap = new HashMap<>();
            contentMap.put("uri", uri);
            contentMap.put("mimeType", resource.getMimeType());
            contentMap.put("text", content);
            
            Map<String, Object> result = new HashMap<>();
            result.put("contents", List.of(contentMap));
            
            McpMessage response = new McpMessage();
            response.setId(request.getId());
            response.setResult(result);
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
