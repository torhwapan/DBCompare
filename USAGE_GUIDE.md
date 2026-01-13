# 使用指南

## 场景一：首次使用 - 快速开始

### 步骤 1：修改配置文件

编辑 `src/main/resources/application.yml`，配置数据库连接信息：

```yaml
spring:
  datasource:
    oracle:
      jdbc-url: jdbc:oracle:thin:@192.168.1.100:1521:ORCL
      username: myuser
      password: mypassword
    
    postgres:
      jdbc-url: jdbc:postgresql://192.168.1.101:5432/mydb
      username: pguser
      password: pgpassword

validator:
  tables:
    - user_info      # 要验证的表1
    - order_info     # 要验证的表2
  primary-key: id    # 主键字段名
  ignore-fields:
    - updated_at     # 忽略的字段（如时间戳）
```

### 步骤 2：启动应用

**Windows 系统：**
```bash
start.bat
```

**Linux/Mac 系统：**
```bash
chmod +x start.sh
./start.sh
```

### 步骤 3：执行验证

打开浏览器或使用 curl/Postman 调用 API：

```bash
curl -X POST http://localhost:8080/api/validation/compare-all
```

### 步骤 4：查看结果

在浏览器中打开：
```
http://localhost:8080/api/validation/report/text
```

---

## 场景二：命令行直接使用

如果只想快速验证，不需要 REST API：

1. 启用自动验证功能，编辑 `AutoValidationRunner.java`：
```java
@Component  // 取消注释这行
public class AutoValidationRunner implements ApplicationRunner {
    // ...
}
```

2. 运行应用：
```bash
mvn spring-boot:run
```

3. 应用启动后会自动执行验证并生成报告文件：
   - `validation_report.txt` - 文本格式报告
   - `validation_report.json` - JSON 格式报告

---

## 场景三：只使用 SQL 方式对比

如果不想使用 Java 程序，可以直接使用 SQL 脚本。

### 方法 1：在 Oracle 中对比

创建 Database Link 连接到 PostgreSQL：

```sql
-- 1. 创建 DB Link（需要管理员权限）
CREATE DATABASE LINK pg_link
  CONNECT TO postgres_user IDENTIFIED BY postgres_password
  USING '(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=pg_host)(PORT=5432))
          (CONNECT_DATA=(SERVICE_NAME=postgres_db)))';

-- 2. 查询记录数差异
SELECT 
    'user_info' AS table_name,
    (SELECT COUNT(*) FROM user_info) AS oracle_count,
    (SELECT COUNT(*) FROM user_info@pg_link) AS pg_count
FROM DUAL;

-- 3. 找出仅在 Oracle 中存在的记录
SELECT id FROM user_info
MINUS
SELECT id FROM user_info@pg_link;

-- 4. 对比字段值
SELECT 
    o.id,
    o.name AS oracle_name,
    p.name AS pg_name
FROM user_info o
INNER JOIN user_info@pg_link p ON o.id = p.id
WHERE o.name != p.name;
```

### 方法 2：导出数据后对比

```sql
-- Oracle 导出
SELECT id, name, email, status 
FROM user_info 
ORDER BY id;

-- PostgreSQL 导出
SELECT id, name, email, status 
FROM user_info 
ORDER BY id;
```

然后使用文件对比工具（如 Beyond Compare, WinMerge）对比两个文件。

### 方法 3：使用哈希值对比

```sql
-- Oracle
SELECT 
    id,
    STANDARD_HASH(id || '|' || name || '|' || email) AS row_hash
FROM user_info
ORDER BY id;

-- PostgreSQL
SELECT 
    id,
    MD5(id::TEXT || '|' || name || '|' || email) AS row_hash
FROM user_info
ORDER BY id;
```

---

## 场景四：大表对比优化

对于千万级别的大表，需要优化对比策略：

### 策略 1：增加批量大小

```yaml
validator:
  batch-size: 5000  # 增加到 5000
```

### 策略 2：分段对比

只对比指定的 ID 范围：

```java
// 修改 DataComparisonService.java
private Set<Object> getPrimaryKeys(JdbcTemplate jdbcTemplate, 
                                   String tableName, 
                                   String primaryKey) {
    String sql = String.format(
        "SELECT %s FROM %s WHERE %s BETWEEN ? AND ?", 
        primaryKey, tableName, primaryKey
    );
    List<Object> keys = jdbcTemplate.queryForList(
        sql, Object.class, startId, endId
    );
    return new HashSet<>(keys);
}
```

### 策略 3：按时间段对比

只对比最近的数据：

```sql
-- 只对比最近7天的数据
SELECT * FROM user_info 
WHERE created_at >= SYSDATE - 7;
```

### 策略 4：使用采样对比

随机采样一定比例的数据：

```sql
-- Oracle: 采样 10%
SELECT * FROM user_info SAMPLE(10);

-- PostgreSQL: 采样 10%
SELECT * FROM user_info TABLESAMPLE BERNOULLI(10);
```

---

## 场景五：定时自动验证

### 方法 1：使用 Spring Scheduler

添加定时任务类：

```java
@Component
public class ScheduledValidator {
    
    @Autowired
    private DataComparisonService comparisonService;
    
    @Autowired
    private ReportService reportService;
    
    // 每天凌晨2点执行
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledValidation() {
        log.info("开始定时验证...");
        
        List<ComparisonResult> results = 
            comparisonService.compareAllTables();
        
        String report = reportService.generateTextReport(results);
        reportService.saveReportToFile(
            report, 
            "report_" + LocalDate.now() + ".txt"
        );
    }
}
```

记得在主类上添加 `@EnableScheduling`：

```java
@SpringBootApplication
@EnableScheduling
public class DbValidatorApplication {
    // ...
}
```

### 方法 2：使用操作系统定时任务

**Windows 任务计划程序：**
```batch
schtasks /create /tn "DB Validation" /tr "D:\path\to\start.bat" /sc daily /st 02:00
```

**Linux Cron：**
```bash
# 编辑 crontab
crontab -e

# 添加定时任务（每天凌晨2点）
0 2 * * * cd /path/to/project && ./start.sh
```

---

## 场景六：集成到 CI/CD 流程

### Jenkins Pipeline 示例

```groovy
pipeline {
    agent any
    
    stages {
        stage('Checkout') {
            steps {
                git 'https://your-repo/db-validator.git'
            }
        }
        
        stage('Build') {
            steps {
                sh 'mvn clean package -DskipTests'
            }
        }
        
        stage('DB Validation') {
            steps {
                script {
                    sh '''
                        java -jar target/db-validator-1.0.0.jar &
                        APP_PID=$!
                        sleep 10
                        
                        # 调用验证 API
                        RESPONSE=$(curl -X POST http://localhost:8080/api/validation/compare-all)
                        
                        # 检查结果
                        CONSISTENT=$(echo $RESPONSE | jq '.consistentTables')
                        TOTAL=$(echo $RESPONSE | jq '.totalTables')
                        
                        kill $APP_PID
                        
                        if [ "$CONSISTENT" -eq "$TOTAL" ]; then
                            echo "验证通过：所有表数据一致"
                            exit 0
                        else
                            echo "验证失败：存在数据不一致"
                            exit 1
                        fi
                    '''
                }
            }
        }
    }
    
    post {
        always {
            archiveArtifacts artifacts: 'validation_report*.txt', allowEmptyArchive: true
        }
    }
}
```

### GitLab CI 示例

```yaml
stages:
  - build
  - validate

build:
  stage: build
  script:
    - mvn clean package -DskipTests
  artifacts:
    paths:
      - target/*.jar

validate:
  stage: validate
  script:
    - java -jar target/db-validator-1.0.0.jar &
    - APP_PID=$!
    - sleep 10
    - |
      RESPONSE=$(curl -X POST http://localhost:8080/api/validation/compare-all)
      CONSISTENT=$(echo $RESPONSE | jq '.consistentTables')
      TOTAL=$(echo $RESPONSE | jq '.totalTables')
      kill $APP_PID
      
      if [ "$CONSISTENT" -eq "$TOTAL" ]; then
        echo "验证通过"
      else
        echo "验证失败"
        exit 1
      fi
  artifacts:
    paths:
      - validation_report*.txt
```

---

## 场景七：自定义验证逻辑

### 添加自定义字段对比规则

编辑 `DataComparisonService.java`，修改 `normalizeValue` 方法：

```java
private Object normalizeValue(Object value) {
    if (value == null) {
        return null;
    }
    
    // 数值类型：统一为 Double
    if (value instanceof Number) {
        return ((Number) value).doubleValue();
    }
    
    // 字符串：去除首尾空格并转小写
    if (value instanceof String) {
        return ((String) value).trim().toLowerCase();
    }
    
    // 日期时间：统一为时间戳
    if (value instanceof java.sql.Timestamp) {
        return ((java.sql.Timestamp) value).getTime();
    }
    
    // 布尔值：统一为 true/false
    if (value instanceof Boolean) {
        return value;
    }
    
    // 自定义：金额字段保留两位小数
    if (value instanceof BigDecimal) {
        return ((BigDecimal) value)
            .setScale(2, RoundingMode.HALF_UP)
            .doubleValue();
    }
    
    return value;
}
```

### 添加表级别的自定义规则

```java
// 为不同表指定不同的主键
Map<String, String> tablePrimaryKeys = Map.of(
    "user_info", "user_id",
    "order_info", "order_id",
    "product_info", "product_id"
);

// 为不同表指定不同的忽略字段
Map<String, List<String>> tableIgnoreFields = Map.of(
    "user_info", List.of("updated_at", "last_login_time"),
    "order_info", List.of("updated_at", "sync_time")
);
```

---

## 常见问题排查

### Q1: 连接超时

**症状：**
```
java.sql.SQLTimeoutException: Connection timed out
```

**解决方案：**
1. 检查网络连接
2. 增加连接超时时间：
```yaml
hikari:
  connection-timeout: 60000  # 增加到60秒
```

### Q2: 内存溢出

**症状：**
```
java.lang.OutOfMemoryError: Java heap space
```

**解决方案：**
1. 减小批量大小：
```yaml
validator:
  batch-size: 500  # 减小到500
```

2. 增加 JVM 内存：
```bash
java -Xmx4g -jar db-validator-1.0.0.jar
```

### Q3: 字段大小写问题

**症状：** 对比结果显示所有字段都不一致

**解决方案：** 代码已自动处理大小写，如仍有问题，检查 `compareRow` 方法。

### Q4: 数据类型不匹配

**症状：** 相同的值被判断为不一致

**解决方案：** 在 `normalizeValue` 方法中添加类型转换逻辑。

---

## 性能参考

| 表大小 | 批量大小 | 内存使用 | 对比时间 |
|--------|----------|----------|----------|
| 1万条  | 1000     | ~200MB   | ~5秒     |
| 10万条 | 2000     | ~500MB   | ~30秒    |
| 100万条| 5000     | ~1GB     | ~5分钟   |
| 1000万条| 10000   | ~2GB     | ~50分钟  |

*以上数据基于双核4GB内存服务器测试*

---

## 最佳实践

1. **首次验证**：先验证小表，确认配置正确
2. **大表优化**：使用分段对比或采样对比
3. **定时任务**：避开业务高峰期（如凌晨）
4. **报告保存**：定期归档验证报告
5. **监控告警**：集成到监控系统，发现不一致立即告警
6. **权限控制**：数据库用户只需 SELECT 权限
7. **网络优化**：应用部署在靠近数据库的服务器上

---

## 扩展阅读

- [Oracle Database Link 文档](https://docs.oracle.com/en/database/oracle/oracle-database/)
- [PostgreSQL Foreign Data Wrapper](https://www.postgresql.org/docs/current/postgres-fdw.html)
- [Spring Boot 多数据源配置](https://spring.io/guides/gs/accessing-data-jpa/)
