package com.cubrid.mcp.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {
    private static final Logger logger = LoggerFactory.getLogger(DataSourceConfig.class);

    @Value("${cubrid.jdbc.url:}")
    private String jdbcUrl;

    @Value("${cubrid.user:}")
    private String user;

    @Value("${cubrid.password:}")
    private String password;

    @Value("${cubrid.pool.minimum-idle:2}")
    private int minimumIdle;

    @Value("${cubrid.pool.maximum-pool-size:10}")
    private int maximumPoolSize;

    @Value("${cubrid.pool.connection-timeout:30000}")
    private long connectionTimeout;

    @Bean
    @org.springframework.context.annotation.Lazy
    public DataSource dataSource() {
        // 환경변수에서 우선 읽기
        String url = System.getenv("CUBRID_JDBC_URL");
        String dbUser = System.getenv("CUBRID_USER");
        String dbPassword = System.getenv("CUBRID_PASSWORD");

        if (url == null || url.isEmpty()) {
            url = jdbcUrl;
        }
        if (dbUser == null || dbUser.isEmpty()) {
            dbUser = user;
        }
        if (dbPassword == null || dbPassword.isEmpty()) {
            dbPassword = password;
        }

        if (url == null || url.isEmpty()) {
            throw new IllegalStateException("CUBRID JDBC URL이 설정되지 않았습니다. 환경변수 CUBRID_JDBC_URL 또는 설정 파일의 cubrid.jdbc.url을 설정하세요.");
        }
        if (dbUser == null || dbUser.isEmpty()) {
            throw new IllegalStateException("CUBRID 사용자명이 설정되지 않았습니다. 환경변수 CUBRID_USER 또는 설정 파일의 cubrid.user을 설정하세요.");
        }
        if (dbPassword == null || dbPassword.isEmpty()) {
            throw new IllegalStateException("CUBRID 비밀번호가 설정되지 않았습니다. 환경변수 CUBRID_PASSWORD 또는 설정 파일의 cubrid.password을 설정하세요.");
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(dbUser);
        config.setPassword(dbPassword);
        config.setMinimumIdle(minimumIdle);
        config.setMaximumPoolSize(maximumPoolSize);
        config.setConnectionTimeout(connectionTimeout);
        
        // CUBRID JDBC 드라이버 클래스명
        config.setDriverClassName("cubrid.jdbc.driver.CUBRIDDriver");
        
        // CUBRID 특화 설정
        config.addDataSourceProperty("charSet", "utf-8");
        config.addDataSourceProperty("queryTimeout", "30");
        config.addDataSourceProperty("connectTimeout", "10");
        
        // 시작 시점에 연결을 시도하지 않도록 설정 (실제 사용 시점에 연결)
        // 이렇게 하면 DB가 없어도 MCP 서버가 시작되어 프로토콜 테스트 가능
        config.setInitializationFailTimeout(-1); // -1: 시작 시 연결 시도 안 함
        config.setConnectionTestQuery("SELECT 1"); // 연결 테스트 쿼리

        logger.info("CUBRID 데이터소스 초기화 완료: URL={}, User={}, PoolSize={}-{}", 
                   url.replaceAll(":.*:.*:.*:.*:", ":***:***:***:***:"), 
                   dbUser, minimumIdle, maximumPoolSize);
        logger.info("주의: DB 연결은 실제 사용 시점에 시도됩니다. 시작 시점에는 연결하지 않습니다.");

        return new HikariDataSource(config);
    }
}
