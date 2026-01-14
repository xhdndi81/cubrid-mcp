package com.cubrid.mcp;

import com.cubrid.mcp.mcp.McpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class CubridMcpApplication implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(CubridMcpApplication.class);
    private static PrintStream mcpOut;

    static {
        // 1. MCP 전용 스트림 확보
        mcpOut = System.out;
        // 2. 다른 모든 stdout 출력을 stderr로 리다이렉트 (로그 오염 방지)
        try {
            System.setOut(new PrintStream(System.err, true, StandardCharsets.UTF_8.name()));
        } catch (Exception e) {
            System.setOut(System.err);
        }
    }

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
        logger.info(">>> CUBRID MCP 서버 구동 시작");
        // MCP 서버에 전용 스트림 전달
        mcpServer.start(mcpOut);
    }
}
