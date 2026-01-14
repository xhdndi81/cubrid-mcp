package com.cubrid.mcp;

import com.cubrid.mcp.mcp.McpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class CubridMcpApplication implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(CubridMcpApplication.class);

    private final McpServer mcpServer;

    @Autowired
    public CubridMcpApplication(McpServer mcpServer) {
        this.mcpServer = mcpServer;
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(CubridMcpApplication.class);
        app.setBannerMode(org.springframework.boot.Banner.Mode.OFF);
        app.run(args);
    }

    @Override
    public void run(String... args) {
        logger.info(">>> CUBRID MCP 서버 구동 (PID: {})", ProcessHandle.current().pid());
        mcpServer.start();
    }
}
