# CUBRID MCP Server

CUBRID 데이터베이스에 대한 [Model Context Protocol (MCP)](https://modelcontextprotocol.io) 서버입니다. LLM/에이전트가 CUBRID 데이터베이스의 스키마를 탐색하고 SELECT 쿼리를 안전하게 실행할 수 있도록 지원합니다.

## 📋 목차

- [주요 기능](#주요-기능)
- [시작하기](#시작하기)
  - [요구사항](#요구사항)
  - [설치](#설치)
  - [설정](#설정)
- [사용법](#사용법)
  - [빌드](#빌드)
  - [실행](#실행)
  - [MCP 클라이언트 연동](#mcp-클라이언트-연동)
- [API 문서](#api-문서)
  - [Tools](#tools)
  - [Resources](#resources)
- [보안 정책](#보안-정책)
- [테스트](#테스트)
- [문제 해결](#문제-해결)
- [기여하기](#기여하기)
- [라이선스](#라이선스)

## ✨ 주요 기능

- 🔍 **스키마 탐색**: 테이블 목록 조회, 테이블 스키마 정보 조회
- 📊 **데이터 조회**: SELECT 쿼리 실행 및 결과 반환
- 🔒 **보안 정책**: SELECT만 허용, 지정된 스키마만 접근 가능
- 🚀 **고성능**: HikariCP 연결 풀 사용
- 📝 **로깅**: stderr로 로그 출력 (stdout은 MCP 메시지 전용)

## 🚀 시작하기

### ⚡ 빠른 시작 (컴파일된 JAR 사용) ⭐

컴파일을 직접 할 필요 없이 루트 디렉토리에 포함된 `cubrid-mcp.jar` 파일을 바로 사용할 수 있습니다.

1.  **Java 17 이상 설치** 확인
2.  **데이터베이스 정보 설정** (환경변수 또는 `application.yml`)
3.  **실행**:
    ```bash
    java -Dfile.encoding=UTF-8 -jar cubrid-mcp.jar
    ```

---

### 요구사항

- **Java**: 17 이상
- **Maven**: 3.6 이상
- **CUBRID**: 데이터베이스 서버 (버전 11.x 권장)
- **CUBRID JDBC 드라이버**: 자동으로 Maven 저장소에서 다운로드됩니다

### 설치

1. **저장소 클론**:
   ```bash
   git clone https://github.com/xhdndi81/cubrid-mcp.git
   cd cubrid-mcp
   ```

2. **의존성 확인**:
   ```bash
   mvn dependency:resolve
   ```

   CUBRID JDBC 드라이버는 자동으로 [CUBRID Maven 저장소](https://maven.cubrid.org/)에서 다운로드됩니다.

### 설정

#### 1. 설정 파일 생성

`application.yml.example` 파일을 복사하여 `application.yml`을 생성하세요:

```bash
# Windows
copy src\main\resources\application.yml.example src\main\resources\application.yml

# Linux/Mac
cp src/main/resources/application.yml.example src/main/resources/application.yml
```

#### 2. 데이터베이스 연결 정보 설정

**두 가지 방법 중 하나를 선택하세요:**

##### 방법 1: 환경변수 사용 (권장) ⭐

MCP 클라이언트 설정에서 환경변수로 설정하는 방법입니다. 이 방법을 사용하면 `application.yml`을 수정할 필요가 없습니다.

**장점**:
- 설정 파일에 비밀번호를 저장하지 않아도 됩니다
- 여러 환경(개발/운영)에서 다른 설정을 쉽게 사용할 수 있습니다
- `application.yml`을 수정하지 않아도 되므로 Git에 커밋해도 안전합니다

MCP 클라이언트 설정에서 환경변수를 설정하면 됩니다 (아래 "MCP 클라이언트 연동" 섹션 참조).

##### 방법 2: application.yml 파일 사용

`src/main/resources/application.yml` 파일을 열어 다음 정보를 수정하세요:

```yaml
cubrid:
  jdbc:
    # 형식: jdbc:cubrid:<host>:<port>:<db-name>:<user>:<password>:?charSet=utf-8
    # 예시: jdbc:cubrid:localhost:33000:demodb:dba:password:?charSet=utf-8
    url: jdbc:cubrid:localhost:33000:demodb:dba:your_password:?charSet=utf-8
  user: dba
  password: your_password
```

**JDBC URL 형식 설명**:
- `host`: CUBRID 서버 호스트 주소 (예: `localhost`, `192.168.1.100`)
- `port`: CUBRID 서버 포트 (기본값: `33000`)
- `db-name`: 데이터베이스 이름 (예: `demodb`, `posart`)
- `user`: 데이터베이스 사용자명 (예: `dba`)
- `password`: 데이터베이스 비밀번호

**주의**: 이 방법을 사용하면 `application.yml`에 비밀번호가 저장되므로, Git에 커밋하지 않도록 주의하세요.

#### 3. 스키마 설정

CUBRID의 기본 스키마는 보통 `dba`입니다. 다른 스키마를 사용하는 경우 `application.yml`에서 수정하거나 환경변수 `POLICY_ALLOWED_SCHEMA`를 설정하세요:

```yaml
policy:
  allowed-schema: dba  # 사용할 스키마 이름
```

또는 환경변수:
```bash
export POLICY_ALLOWED_SCHEMA=dba
```

#### 4. 우선순위

설정 값의 우선순위는 다음과 같습니다:
1. **환경변수** (최우선) - `CUBRID_JDBC_URL`, `CUBRID_USER`, `CUBRID_PASSWORD`
2. **application.yml** - 환경변수가 없을 때만 사용

**중요**: MCP 클라이언트 설정에서 환경변수를 설정했다면, `application.yml`에 같은 정보를 입력할 필요가 없습니다. 환경변수가 우선순위가 높아서 `application.yml`의 값은 무시됩니다.

#### 5. 환경변수로 설정 (방법 1 사용 시)

**MCP 클라이언트 설정에서 환경변수 설정** (권장):
MCP 클라이언트 설정 파일의 `env` 섹션에 환경변수를 설정하면 됩니다. 자세한 내용은 아래 "MCP 클라이언트 연동" 섹션을 참조하세요.

**직접 터미널에서 설정** (MCP 클라이언트를 사용하지 않는 경우):

**Windows (CMD)**:
```cmd
set CUBRID_JDBC_URL=jdbc:cubrid:localhost:33000:demodb:dba:password:?charSet=utf-8
set CUBRID_USER=dba
set CUBRID_PASSWORD=your_password
```

**Windows (PowerShell)**:
```powershell
$env:CUBRID_JDBC_URL="jdbc:cubrid:localhost:33000:demodb:dba:password:?charSet=utf-8"
$env:CUBRID_USER="dba"
$env:CUBRID_PASSWORD="your_password"
```

**Linux/Mac**:
```bash
export CUBRID_JDBC_URL="jdbc:cubrid:localhost:33000:demodb:dba:password:?charSet=utf-8"
export CUBRID_USER="dba"
export CUBRID_PASSWORD="your_password"
```

## 📖 사용법

### 빌드

프로젝트를 빌드하여 JAR 파일을 생성합니다:

```bash
mvn clean package
```

빌드가 완료되면 `target/cubrid-mcp-1.0.0-SNAPSHOT.jar` 파일이 생성됩니다.

**테스트를 건너뛰고 빌드하려면**:
```bash
mvn clean package -DskipTests
```

### 실행

#### 기본 실행

```bash
java -jar target/cubrid-mcp-1.0.0-SNAPSHOT.jar
```

서버는 STDIO 모드로 실행되며:
- **stdout**: MCP 프로토콜 메시지 (JSON-RPC)
- **stderr**: 애플리케이션 로그

#### 환경변수와 함께 실행

```bash
# Windows
set CUBRID_JDBC_URL=jdbc:cubrid:localhost:33000:demodb:dba:password:?charSet=utf-8
set CUBRID_USER=dba
set CUBRID_PASSWORD=password
java -jar target/cubrid-mcp-1.0.0-SNAPSHOT.jar

# Linux/Mac
export CUBRID_JDBC_URL="jdbc:cubrid:localhost:33000:demodb:dba:password:?charSet=utf-8"
export CUBRID_USER="dba"
export CUBRID_PASSWORD="password"
java -jar target/cubrid-mcp-1.0.0-SNAPSHOT.jar
```

### MCP 클라이언트 연동

#### Claude Desktop 설정

1. Claude Desktop 설정 파일 위치:
   - **Windows**: `%APPDATA%\Claude\claude_desktop_config.json`
   - **Mac**: `~/Library/Application Support/Claude/claude_desktop_config.json`
   - **Linux**: `~/.config/Claude/claude_desktop_config.json`

2. 설정 파일에 다음을 추가:

```json
{
  "mcpServers": {
    "cubrid": {
      "command": "java",
      "args": [
        "-Dfile.encoding=UTF-8",
        "-jar",
        "C:/path/to/cubrid-mcp/cubrid-mcp.jar"
      ],
      "env": {
        "CUBRID_JDBC_URL": "jdbc:cubrid:localhost:33000:demodb:dba:password:?charSet=utf-8",
        "CUBRID_USER": "dba",
        "CUBRID_PASSWORD": "your_password"
      }
    }
  }
}
```

**⚠️ 중요: 경로에 공백이 있는 경우**

JAR 파일 경로에 공백이 포함되어 있으면 Java가 파일을 찾지 못할 수 있습니다. 공백이 없는 경로로 JAR 파일을 복사하거나 이동하는 것을 권장합니다.

3. Claude Desktop을 재시작합니다.

#### Cursor IDE 설정

1. Cursor 설정에서 MCP 서버 추가
2. 설정 파일 경로:
   - **Windows**: `%APPDATA%\Cursor\User\globalStorage\saoudrizwan.claude-dev\settings\cline_mcp_settings.json`
   - **Mac**: `~/Library/Application Support/Cursor/User/globalStorage/saoudrizwan.claude-dev/settings/cline_mcp_settings.json`

3. 설정 예시:

```json
{
  "mcpServers": {
    "cubrid": {
      "command": "java",
      "args": [
        "-Dfile.encoding=UTF-8",
        "-jar",
        "/absolute/path/to/cubrid-mcp/cubrid-mcp.jar"
      ],
      "env": {
        "CUBRID_JDBC_URL": "jdbc:cubrid:localhost:33000:demodb:dba:password:?charSet=utf-8",
        "CUBRID_USER": "dba",
        "CUBRID_PASSWORD": "your_password"
      }
    }
  }
}
```

**⚠️ 중요: 경로에 공백이 있는 경우**

JAR 파일 경로에 공백이 포함되어 있으면 Java가 파일을 찾지 못할 수 있습니다. 다음 방법 중 하나를 사용하세요:

**방법 1: 공백이 없는 경로로 JAR 파일 복사 (권장)**
```bash
# Windows 예시
copy "target\cubrid-mcp-1.0.0-SNAPSHOT.jar" "C:\cubrid-mcp\cubrid-mcp-1.0.0-SNAPSHOT.jar"
```

그리고 설정에서:
```json
"args": [
  "-jar",
  "C:/cubrid-mcp/cubrid-mcp-1.0.0-SNAPSHOT.jar"
]
```

**방법 2: args 배열에서 따옴표 사용하지 않기**
- ❌ 잘못된 예: `"\"D:/path with spaces/file.jar\""`
- ✅ 올바른 예: `"D:/path with spaces/file.jar"`

JSON 배열의 각 요소는 이미 문자열이므로 따옴표를 추가로 이스케이프하면 안 됩니다.

## 📚 API 문서

### Tools

MCP 서버는 다음 4개의 tool을 제공합니다:

#### 1. `db.ping`

데이터베이스 연결 상태를 확인합니다.

**입력**: 없음

**출력 예시**:
```json
{
  "ok": true,
  "serverTime": "2026-01-14T10:55:45.654032300Z",
  "db": "demodb"
}
```

**사용 예시**:
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "db.ping",
    "arguments": {}
  }
}
```

#### 2. `db.listTables`

지정된 스키마의 테이블 목록을 조회합니다.

**입력**:
- `pattern` (선택): 테이블명 패턴 (LIKE 패턴, 기본값: `"%"`
- `limit` (선택): 최대 반환 개수 (기본값: `500`)

**출력 예시**:
```json
{
  "schema": "dba",
  "tables": [
    {"name": "accept_board_t", "type": "TABLE"},
    {"name": "hbz_admin_t", "type": "TABLE"}
  ]
}
```

**사용 예시**:
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/call",
  "params": {
    "name": "db.listTables",
    "arguments": {
      "pattern": "%",
      "limit": 10
    }
  }
}
```

#### 3. `db.describeTable`

테이블의 스키마 정보(컬럼, 기본키, 인덱스)를 조회합니다.

**입력**:
- `table` (필수): 테이블명 (스키마 없이)

**출력 예시**:
```json
{
  "schema": "dba",
  "table": "accept_board_t",
  "columns": [
    {
      "name": "bd_seq",
      "type": "NUMERIC(15)",
      "nullable": true
    },
    {
      "name": "company_name",
      "type": "VARCHAR(300)",
      "nullable": true
    }
  ],
  "primaryKey": [],
  "indexes": []
}
```

**사용 예시**:
```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "tools/call",
  "params": {
    "name": "db.describeTable",
    "arguments": {
      "table": "accept_board_t"
    }
  }
}
```

#### 4. `db.query`

SELECT 쿼리를 실행하고 결과를 반환합니다.

**입력**:
- `sql` (필수): 실행할 SELECT SQL 쿼리
- `maxRows` (선택): 최대 행 수 (기본값: 없음, 하드 상한 적용)
- `maxBytes` (선택): 최대 바이트 수 (기본값: 없음, 하드 상한 적용)
- `timeoutMs` (선택): 타임아웃 밀리초 (기본값: 없음, 하드 상한 적용)

**출력 예시**:
```json
{
  "columns": [
    {"name": "bd_seq", "type": "NUMERIC", "nullable": true},
    {"name": "company_name", "type": "VARCHAR", "nullable": true}
  ],
  "rows": [
    [0, "test"],
    [1, "test2"]
  ],
  "rowCount": 2,
  "truncated": false
}
```

**사용 예시**:
```json
{
  "jsonrpc": "2.0",
  "id": 4,
  "method": "tools/call",
  "params": {
    "name": "db.query",
    "arguments": {
      "sql": "SELECT * FROM dba.accept_board_t",
      "maxRows": 5
    }
  }
}
```

**주의사항**:
- SQL 쿼리는 반드시 `SELECT`로 시작해야 합니다
- 스키마는 `dba` (또는 설정된 스키마)만 허용됩니다
- `LIMIT` 절을 SQL에 포함하지 않고 `maxRows` 파라미터를 사용하는 것을 권장합니다 (CUBRID 호환성)

### Resources

MCP 서버는 다음 3개의 resource를 제공합니다:

#### 1. `cubrid://schema/summary`

스키마의 테이블 요약 정보를 JSON 형식으로 제공합니다.

**사용 예시**:
```json
{
  "jsonrpc": "2.0",
  "id": 5,
  "method": "resources/read",
  "params": {
    "uri": "cubrid://schema/summary"
  }
}
```

#### 2. `cubrid://schema/dba/{table}`

특정 테이블의 스키마 정보를 JSON 형식으로 제공합니다.

**사용 예시**:
```json
{
  "jsonrpc": "2.0",
  "id": 6,
  "method": "resources/read",
  "params": {
    "uri": "cubrid://schema/dba/accept_board_t"
  }
}
```

#### 3. `cubrid://docs/policy`

서버 정책 문서를 Markdown 형식으로 제공합니다.

**사용 예시**:
```json
{
  "jsonrpc": "2.0",
  "id": 7,
  "method": "resources/read",
  "params": {
    "uri": "cubrid://docs/policy"
  }
}
```

## 🔒 보안 정책

### 스키마 제한

- **허용 스키마**: 설정 파일의 `policy.allowed-schema`에 지정된 스키마만 허용 (기본값: `dba`)
- 다른 스키마 접근 시도는 자동 차단됩니다

### SQL 문장 제한

- **허용**: `SELECT` 문만 허용 (WITH 절 포함한 CTE 지원)
- **차단**: INSERT, UPDATE, DELETE, DROP, ALTER, CREATE 등 모든 변경/관리 문장

### 다중 문장 차단

- 세미콜론(`;`)으로 구분된 다중 SQL 문은 허용되지 않습니다
- 한 번에 하나의 SELECT 문만 실행 가능합니다

### 결과 제한

**하드 상한** (서버 측 강제 제한):
- 최대 행 수: 10,000 행
- 최대 바이트: 20 MB
- 타임아웃: 30 초

**Tool 파라미터**:
- `maxRows`, `maxBytes`, `timeoutMs` 파라미터로 제한을 설정할 수 있지만, 하드 상한을 초과할 수 없습니다

## 🧪 테스트

### Node.js 테스트 스크립트

프로젝트에 포함된 `test-mcp-server.js`를 사용하여 MCP 서버를 테스트할 수 있습니다:

1. **테스트 스크립트 수정**:
   `test-mcp-server.js` 파일을 열어 환경변수를 실제 DB 정보로 수정하세요:
   ```javascript
   process.env.CUBRID_JDBC_URL = 'jdbc:cubrid:localhost:33000:demodb:dba:password:?charSet=utf-8';
   process.env.CUBRID_USER = 'dba';
   process.env.CUBRID_PASSWORD = 'your_password';
   ```

2. **테스트 실행**:
   ```bash
   node test-mcp-server.js
   ```

이 스크립트는 다음을 테스트합니다:
- `initialize` 메시지 처리
- `tools/list` - 4개 tool 확인
- `resources/list` - 3개 resource 확인
- `db.ping` - DB 연결 테스트
- `db.listTables` - 테이블 목록 조회
- `db.describeTable` - 테이블 스키마 조회
- `db.query` - SELECT 쿼리 실행

### Maven 테스트

```bash
# 모든 테스트 실행
mvn test

# 특정 테스트 실행
mvn test -Dtest=McpServerIntegrationTest
mvn test -Dtest=ConnectionTest
mvn test -Dtest=McpToolsTest
```

## 🔧 문제 해결

### 연결 오류

**문제**: `Failed to connect to database server`

**해결 방법**:
1. CUBRID 서버가 실행 중인지 확인:
   ```bash
   cubrid server status
   ```

2. JDBC URL 형식 확인:
   - 형식: `jdbc:cubrid:<host>:<port>:<db-name>:<user>:<password>:?charSet=utf-8`
   - 모든 콜론(`:`)이 올바르게 포함되어 있는지 확인

3. 방화벽 설정 확인:
   - CUBRID 포트(기본값: 33000)가 열려있는지 확인

4. 데이터베이스 이름 확인:
   ```bash
   cubrid listdb
   ```

### 정책 위반 오류

**문제**: `SQL 정책 위반`

**해결 방법**:
- SELECT 문만 사용 가능합니다
- 설정된 스키마(`dba`)만 허용됩니다
- 다중 문장 사용 불가 (세미콜론으로 구분된 여러 문장)

**올바른 예시**:
```sql
SELECT * FROM dba.accept_board_t
SELECT * FROM dba.accept_board_t WHERE bd_seq = 1
```

**잘못된 예시**:
```sql
INSERT INTO dba.accept_board_t VALUES (...)
UPDATE dba.accept_board_t SET ...
SELECT * FROM dba.accept_board_t; SELECT * FROM dba.other_table
```

### 한글 인코딩 문제

**문제**: 한글이 깨져서 표시됨

**해결 방법**:
- JDBC URL에 `charSet=utf-8` 파라미터가 포함되어 있는지 확인:
  ```
  jdbc:cubrid:localhost:33000:demodb:dba:password:?charSet=utf-8
  ```

### 성능 문제

**문제**: 쿼리 실행이 느림

**해결 방법**:
1. 하드 상한 설정 확인 (`application.yml`):
   ```yaml
   policy:
     hard-max-rows: 10000
     hard-timeout-ms: 30000
   ```

2. 커넥션 풀 크기 조정:
   ```yaml
   cubrid:
     pool:
       maximum-pool-size: 10  # 필요시 증가
   ```

3. 쿼리 최적화:
   - 필요한 컬럼만 선택
   - WHERE 절 사용
   - 인덱스 활용

## 🤝 기여하기

버그 리포트, 기능 제안, Pull Request를 환영합니다!

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📄 라이선스

이 프로젝트는 MIT 라이선스를 따릅니다. 자세한 내용은 `LICENSE` 파일을 참조하세요.

## 📞 지원

문제가 발생하거나 질문이 있으시면:
- [Issues](https://github.com/xhdndi81/cubrid-mcp/issues)에 등록해주세요
- 또는 이메일로 문의해주세요

---

**Made with ❤️ for the CUBRID community**
