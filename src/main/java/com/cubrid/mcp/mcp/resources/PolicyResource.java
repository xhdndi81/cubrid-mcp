package com.cubrid.mcp.mcp.resources;

import com.cubrid.mcp.policy.SqlPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PolicyResource implements McpResource {
    private static final Logger logger = LoggerFactory.getLogger(PolicyResource.class);

    private final SqlPolicy sqlPolicy;

    @Autowired
    public PolicyResource(SqlPolicy sqlPolicy) {
        this.sqlPolicy = sqlPolicy;
    }

    @Override
    public String getUri() {
        return "cubrid://docs/policy";
    }

    @Override
    public String getMimeType() {
        return "text/markdown";
    }

    @Override
    public String getDescription() {
        return "서버 정책 문서";
    }

    @Override
    public String getContent() throws Exception {
        StringBuilder sb = new StringBuilder();
        String allowedSchema = sqlPolicy.getAllowedSchema();
        sb.append("# CUBRID MCP 서버 정책\n\n");
        sb.append("## 스키마 제한\n");
        sb.append(String.format("- **허용 스키마**: `%s`만 허용됩니다.\n", allowedSchema));
        sb.append("- 다른 스키마에 대한 접근은 차단됩니다.\n\n");
        
        sb.append("## SQL 문장 제한\n");
        sb.append("- **허용**: `SELECT` 문만 허용됩니다.\n");
        sb.append("  - `WITH` 절을 사용한 CTE(Common Table Expression) 지원\n");
        sb.append("  - `ORDER BY`, `GROUP BY`, `JOIN` 등 SELECT에 부수되는 절 허용\n");
        sb.append("- **차단**: 다음 SQL 문장은 허용되지 않습니다:\n");
        sb.append("  - `INSERT`, `UPDATE`, `DELETE`, `MERGE`\n");
        sb.append("  - `DROP`, `ALTER`, `CREATE`, `TRUNCATE`\n");
        sb.append("  - `GRANT`, `REVOKE`\n");
        sb.append("  - `CALL`, `EXEC`, `EXECUTE`\n");
        sb.append("  - `SET`, `COMMIT`, `ROLLBACK`\n\n");
        
        sb.append("## 다중 문장 제한\n");
        sb.append("- 세미콜론(`;`)으로 구분된 다중 SQL 문은 허용되지 않습니다.\n\n");
        
        sb.append("## 결과 제한\n");
        sb.append("- 기본값: 무제한 (하지만 하드 상한이 적용됩니다)\n");
        sb.append("- **하드 상한** (서버 설정):\n");
        sb.append("  - 최대 행 수: ").append(sqlPolicy.getHardMaxRows()).append(" 행\n");
        sb.append("  - 최대 바이트: ").append(sqlPolicy.getHardMaxBytes() / 1024 / 1024).append(" MB\n");
        sb.append("  - 타임아웃: ").append(sqlPolicy.getHardTimeoutMs() / 1000).append(" 초\n");
        sb.append("- Tool 파라미터로 `maxRows`, `maxBytes`, `timeoutMs`를 지정할 수 있으며,\n");
        sb.append("  서버 하드 상한을 초과할 수 없습니다.\n\n");
        
        sb.append("## 보안 고려사항\n");
        sb.append("- 모든 쿼리는 정책 검사를 거칩니다.\n");
        sb.append(String.format("- %s 스키마가 아닌 테이블 접근은 자동으로 차단됩니다.\n", allowedSchema));
        sb.append("- SQL 인젝션 방지를 위해 PreparedStatement를 사용합니다.\n");
        
        logger.debug("정책 문서 생성 완료");
        return sb.toString();
    }
}
