# 快速参考手册

## 一、数据库配置模板

### Oracle 连接配置
```yaml
spring:
  datasource:
    oracle:
      jdbc-url: jdbc:oracle:thin:@<host>:<port>:<sid>
      # 或使用 service name
      # jdbc-url: jdbc:oracle:thin:@//<host>:<port>/<service_name>
      username: <username>
      password: <password>
      driver-class-name: oracle.jdbc.OracleDriver
```

### PostgreSQL 连接配置
```yaml
spring:
  datasource:
    postgres:
      jdbc-url: jdbc:postgresql://<host>:<port>/<database>
      username: <username>
      password: <password>
      driver-class-name: org.postgresql.Driver
```

---

## 二、常用 API 命令

### 使用 curl

```bash
# 1. 健康检查
curl http://localhost:8080/api/validation/health

# 2. 验证所有表
curl -X POST http://localhost:8080/api/validation/compare-all

# 3. 验证单个表
curl -X POST http://localhost:8080/api/validation/compare-table/user_info

# 4. 获取文本报告
curl http://localhost:8080/api/validation/report/text

# 5. 获取 JSON 报告
curl http://localhost:8080/api/validation/report/json

# 6. 下载文本报告
curl -O http://localhost:8080/api/validation/report/download/text

# 7. 下载 JSON 报告
curl -O http://localhost:8080/api/validation/report/download/json
```

### 使用 PowerShell

```powershell
# 1. 验证所有表
Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/validation/compare-all"

# 2. 获取报告
Invoke-RestMethod -Uri "http://localhost:8080/api/validation/report/text"

# 3. 下载报告
Invoke-WebRequest -Uri "http://localhost:8080/api/validation/report/download/text" -OutFile "report.txt"
```

---

## 三、SQL 对比速查

### 1. 记录数对比
```sql
-- Oracle
SELECT COUNT(*) FROM table_name;

-- PostgreSQL
SELECT COUNT(*) FROM table_name;
```

### 2. 找出仅在 Oracle 中存在的记录
```sql
SELECT id FROM oracle_schema.table_name
MINUS
SELECT id FROM postgres_schema.table_name;
```

### 3. 找出仅在 PostgreSQL 中存在的记录
```sql
SELECT id FROM postgres_schema.table_name
EXCEPT
SELECT id FROM oracle_schema.table_name;
```

### 4. 找出字段值不一致的记录
```sql
SELECT 
    o.id,
    o.field1 AS oracle_value,
    p.field1 AS postgres_value
FROM oracle_schema.table_name o
INNER JOIN postgres_schema.table_name p ON o.id = p.id
WHERE o.field1 != p.field1;
```

### 5. 使用哈希值快速对比
```sql
-- Oracle
SELECT id, STANDARD_HASH(id || '|' || name || '|' || email) AS hash
FROM table_name;

-- PostgreSQL
SELECT id, MD5(id::TEXT || '|' || name || '|' || email) AS hash
FROM table_name;
```

---

## 四、配置参数说明

| 参数 | 说明 | 默认值 | 建议值 |
|------|------|--------|--------|
| `validator.tables` | 要验证的表列表 | - | 根据需求配置 |
| `validator.batch-size` | 批量查询大小 | 1000 | 小表: 1000<br>大表: 5000 |
| `validator.primary-key` | 主键字段名 | id | 根据实际情况配置 |
| `validator.ignore-fields` | 忽略的字段列表 | - | updated_at, sync_time 等 |
| `hikari.maximum-pool-size` | 最大连接数 | 10 | 开发: 5<br>生产: 20 |
| `hikari.connection-timeout` | 连接超时(ms) | 30000 | 30000-60000 |

---

## 五、常见问题速查

### 问题 1：连接数据库失败

**症状：**
```
Could not open JDBC Connection
```

**检查清单：**
- [ ] 数据库 IP 和端口是否正确
- [ ] 用户名密码是否正确
- [ ] 数据库服务是否启动
- [ ] 防火墙是否允许连接
- [ ] JDBC URL 格式是否正确

**解决方案：**
```bash
# 测试 Oracle 连接
telnet oracle_host 1521

# 测试 PostgreSQL 连接
telnet postgres_host 5432

# 使用 SQL 客户端测试连接
# Oracle: SQL*Plus, SQL Developer
# PostgreSQL: psql, pgAdmin
```

### 问题 2：内存溢出

**症状：**
```
java.lang.OutOfMemoryError: Java heap space
```

**解决方案：**
```bash
# 方案 1: 增加 JVM 内存
java -Xmx4g -jar db-validator-1.0.0.jar

# 方案 2: 减小批量大小
# 修改 application.yml
validator:
  batch-size: 500
```

### 问题 3：对比速度慢

**原因分析：**
- 表数据量大
- 批量大小不合适
- 网络延迟
- 数据库性能

**优化方案：**
```yaml
# 1. 增加批量大小
validator:
  batch-size: 5000

# 2. 增加连接池
hikari:
  maximum-pool-size: 20

# 3. 分段对比（修改代码）
# 只对比指定 ID 范围

# 4. 使用索引
# 确保主键有索引
```

### 问题 4：字段值总是不一致

**原因：** 数据类型差异未正确处理

**解决方案：**
修改 `DataComparisonService.normalizeValue()` 方法：

```java
private Object normalizeValue(Object value) {
    if (value == null) return null;
    
    // 添加你的类型转换逻辑
    if (value instanceof String) {
        return ((String) value).trim();
    }
    
    // 其他类型...
    return value;
}
```

### 问题 5：Oracle 大小写问题

**症状：** Oracle 字段名是大写，PostgreSQL 是小写

**解决方案：** 代码已自动处理，如仍有问题：

```java
// 统一转换为小写
String fieldName = field.toLowerCase();
```

---

## 六、性能基准参考

### 小表（< 1万条）
```yaml
validator:
  batch-size: 1000

hikari:
  maximum-pool-size: 5
```
**预期时间：** < 10秒

### 中等表（1万 - 100万条）
```yaml
validator:
  batch-size: 2000

hikari:
  maximum-pool-size: 10
```
**预期时间：** 1-10分钟

### 大表（> 100万条）
```yaml
validator:
  batch-size: 5000

hikari:
  maximum-pool-size: 20
```
**预期时间：** 10-60分钟

---

## 七、日志级别配置

### 开发环境（详细日志）
```yaml
logging:
  level:
    com.example.dbvalidator: DEBUG
    org.springframework.jdbc: DEBUG
```

### 生产环境（简洁日志）
```yaml
logging:
  level:
    com.example.dbvalidator: INFO
    org.springframework.jdbc: WARN
```

### 只记录错误
```yaml
logging:
  level:
    com.example.dbvalidator: ERROR
```

---

## 八、环境变量使用

### Windows
```batch
set ORACLE_PASSWORD=secret123
set POSTGRES_PASSWORD=secret456
java -jar db-validator-1.0.0.jar
```

### Linux/Mac
```bash
export ORACLE_PASSWORD=secret123
export POSTGRES_PASSWORD=secret456
java -jar db-validator-1.0.0.jar
```

### Docker
```dockerfile
docker run -e ORACLE_PASSWORD=secret123 \
           -e POSTGRES_PASSWORD=secret456 \
           db-validator:latest
```

---

## 九、报告格式示例

### 文本报告格式
```
================================================================================
数据库双写验证报告
生成时间: 2026-01-13 10:30:00
================================================================================

总表数: 3
一致表数: 2
不一致表数: 1

--------------------------------------------------------------------------------
表名: user_info
一致性: ✓ 一致
对比耗时: 1523 ms

Oracle 记录数: 1000
PostgreSQL 记录数: 1000

✓ 该表数据完全一致
```

### JSON 报告格式
```json
[
  {
    "tableName": "user_info",
    "oracleCount": 1000,
    "postgresCount": 1000,
    "isConsistent": true,
    "onlyInOracle": [],
    "onlyInPostgres": [],
    "fieldDifferences": {},
    "durationMs": 1523,
    "comparisonTime": "2026-01-13 10:30:00"
  }
]
```

---

## 十、Maven 命令速查

```bash
# 编译
mvn compile

# 打包
mvn package

# 跳过测试打包
mvn package -DskipTests

# 清理并打包
mvn clean package

# 运行测试
mvn test

# 运行应用
mvn spring-boot:run

# 指定配置文件运行
mvn spring-boot:run -Dspring-boot.run.arguments=--spring.profiles.active=dev

# 查看依赖树
mvn dependency:tree

# 更新依赖
mvn clean install -U
```

---

## 十一、Docker 部署（可选）

### Dockerfile
```dockerfile
FROM openjdk:11-jre-slim
WORKDIR /app
COPY target/db-validator-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 构建镜像
```bash
docker build -t db-validator:1.0.0 .
```

### 运行容器
```bash
docker run -d \
  --name db-validator \
  -p 8080:8080 \
  -e ORACLE_PASSWORD=secret \
  -e POSTGRES_PASSWORD=secret \
  db-validator:1.0.0
```

---

## 十二、快速故障排查流程

```
1. 检查应用是否启动
   → curl http://localhost:8080/api/validation/health

2. 检查数据库连接
   → 查看启动日志是否有连接错误

3. 检查表配置
   → 确认 validator.tables 配置正确

4. 检查主键配置
   → 确认 validator.primary-key 与实际表一致

5. 查看详细日志
   → 设置 logging.level.com.example.dbvalidator=DEBUG

6. 减小数据量测试
   → 先测试小表，确认基本功能正常

7. 检查资源使用
   → 监控内存、CPU、网络使用情况
```

---

## 十三、联系与支持

### 查看日志
- 应用日志：控制台输出
- Spring Boot 日志：`logs/spring.log`（如已配置）

### 获取帮助
1. 查看 README.md
2. 查看 USAGE_GUIDE.md
3. 查看 PROJECT_STRUCTURE.md
4. 查看源代码注释

### 反馈问题
提供以下信息：
- 错误信息和堆栈跟踪
- 配置文件（隐藏敏感信息）
- 表结构和数据量
- 运行环境（OS、Java 版本）
