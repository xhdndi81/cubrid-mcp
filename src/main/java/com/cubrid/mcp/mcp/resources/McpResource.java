package com.cubrid.mcp.mcp.resources;

public interface McpResource {
    String getUri();
    String getMimeType();
    String getDescription();
    String getContent() throws Exception;
}
