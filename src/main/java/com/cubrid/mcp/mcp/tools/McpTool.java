package com.cubrid.mcp.mcp.tools;

import java.util.Map;

public interface McpTool {
    String getName();
    String getDescription();
    Map<String, Object> getInputSchema();
    Object execute(Map<String, Object> params) throws Exception;
}
