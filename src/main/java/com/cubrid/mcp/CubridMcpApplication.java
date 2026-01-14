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
        // Spring Boot 애플리케이션 초기화
        SpringApplication app = new SpringApplication(CubridMcpApplication.class);
        
        // 배너 비활성화 (stdout은 MCP 메시지 전용이므로 배너 출력 금지)
        app.setBannerMode(org.springframework.boot.Banner.Mode.OFF);
        
        // 로깅 설정: stdout은 MCP 메시지 전용이므로 stderr로만 로깅
        System.setOut(System.out); // stdout은 MCP 메시지용
        System.setErr(System.err); // stderr는 로깅용
        
        app.run(args);
    }

    @Override
    public void run(String... args) {
        logger.info("CUBRID MCP 서버 시작");
        logger.info("STDIO 모드: stdout은 MCP 메시지 전용, stderr는 로깅용");
        
        // MCP 서버 시작 (블로킹)
        mcpServer.start();
    }
}
