# 数据库双写验证工具

## 项目简介

这是一个基于 Spring Boot 的数据库双写验证工具，用于对比 Oracle 和 PostgreSQL 数据库中相同表结构的数据一致性。

## 功能特性

- ✅ **双数据源支持**：同时连接 Oracle 和 PostgreSQL 数据库
- ✅ **自动对比**：对比表记录数、主键、字段值
- ✅ **差异报告**：生成详细的文本和 JSON 格式报告
- ✅ **批量处理**：支持大数据量的批量对比
- ✅ **灵活配置**：可配置要对比的表、忽略的字段等
- ✅ **REST API**：提供 RESTful 接口，方便集成
- ✅ **类型兼容**：自动处理不同数据库的类型差异

## 技术栈

- Java 11
- Spring Boot 2.7.14
- Spring JDBC
- Oracle JDBC Driver
- PostgreSQL JDBC Driver
- HikariCP 连接池
- Lombok
- Jackson

## 快速开始

### 1. 配置数据库连接

编辑 `src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    # Oracle 配置
    oracle:
      jdbc-url: jdbc:oracle:thin:@localhost:1521:ORCL
      username: your_oracle_username
      password: your_oracle_password
      driver-class-name: oracle.jdbc.OracleDriver
    
    # PostgreSQL 配置
    postgres:
      jdbc-url: jdbc:postgresql://localhost:5432/your_db
      username: your_postgres_username
      password: your_postgres_password
      driver-class-name: org.postgresql.Driver

# 验证配置
validator:
  # 要验证的表列表
  tables:
    - user_info
    - order_info
    - product_info
  
  # 批量查询大小
  batch-size: 1000
  
  # 主键字段名
  primary-key: id
  
  # 忽略的字段（如时间戳等）
  ignore-fields:
    - updated_at
    - last_modified
```

### 2. 构建项目

```bash
mvn clean package
```

### 3. 运行应用

```bash
java -jar target/db-validator-1.0.0.jar
```

或者使用 Maven：

```bash
mvn spring-boot:run
```

## API 使用指南

### 1. 验证所有配置的表

**请求：**
```bash
POST http://localhost:8080/api/validation/compare-all
```

**响应示例：**
```json
{
  "timestamp": "2026-01-13T10:30:00",
  "totalTables": 3,
  "consistentTables": 2,
  "results": [
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
}
```

### 2. 验证单个表

**请求：**
```bash
POST http://localhost:8080/api/validation/compare-table/user_info
```

### 3. 获取文本格式报告

**请求：**
```bash
GET http://localhost:8080/api/validation/report/text
```

**响应示例：**
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

### 4. 获取 JSON 格式报告

**请求：**
```bash
GET http://localhost:8080/api/validation/report/json
```

### 5. 下载报告

下载文本报告：
```bash
GET http://localhost:8080/api/validation/report/download/text
```

下载 JSON 报告：
```bash
GET http://localhost:8080/api/validation/report/download/json
```

### 6. 健康检查

**请求：**
```bash
GET http://localhost:8080/api/validation/health
```

## 核心功能说明

### 数据对比逻辑

1. **记录数对比**：统计两个数据库中的记录总数
2. **主键对比**：找出仅在一个数据库中存在的记录
3. **字段值对比**：对比共同存在记录的字段值差异
4. **批量处理**：按配置的批量大小分批处理大数据量
5. **类型标准化**：自动处理数值、字符串、日期时间类型的差异

### 差异报告内容

- **表基本信息**：表名、记录数、对比耗时
- **主键差异**：仅在 Oracle 或 PostgreSQL 中存在的主键列表
- **字段差异**：字段值不一致的详细信息
  - 主键值
  - 字段名
  - Oracle 值
  - PostgreSQL 值

### 自动验证（可选）

如果需要在应用启动时自动执行验证，可以在 `AutoValidationRunner.java` 中取消注释 `@Component` 注解：

```java
@Component  // 启用自动验证
public class AutoValidationRunner implements ApplicationRunner {
    // ...
}
```

## SQL 对比方式

项目提供了 Java 代码方式和纯 SQL 方式两种对比方法。详见 `sql-examples.sql` 文件，包含以下 SQL 对比方式：

1. **UNION/EXCEPT** - 找出差异记录
2. **LEFT JOIN** - 找出主键不匹配
3. **COUNT** - 对比记录总数
4. **FULL OUTER JOIN** - 对比字段值差异
5. **HASH/CHECKSUM** - 使用哈希值对比
6. **Database Link** - 使用数据库链接对比
7. **时间范围对比** - 按时间段对比
8. **批量对比** - 一次性对比多个表

## 项目结构

```
src/main/java/com/example/dbvalidator/
├── DbValidatorApplication.java          # 应用入口
├── config/
│   ├── DataSourceConfig.java           # 双数据源配置
│   ├── ValidatorProperties.java        # 验证器配置属性
│   └── JacksonConfig.java              # JSON 配置
├── controller/
│   └── ValidationController.java       # REST 控制器
├── service/
│   ├── DataComparisonService.java      # 数据对比服务
│   └── ReportService.java              # 报告生成服务
├── model/
│   ├── ComparisonResult.java           # 对比结果模型
│   ├── FieldDifference.java            # 字段差异模型
│   └── FieldValuePair.java             # 字段值对模型
└── runner/
    └── AutoValidationRunner.java       # 自动验证运行器
```

## 使用场景

1. **数据库迁移验证**：从 Oracle 迁移到 PostgreSQL 后验证数据完整性
2. **双写验证**：验证双写方案的数据一致性
3. **数据同步验证**：验证数据同步工具的准确性
4. **定期巡检**：定期检查两个数据库的数据一致性

## 注意事项

1. **性能考虑**：大表对比时注意调整 `batch-size` 参数
2. **数据库权限**：确保数据库用户有 SELECT 权限
3. **字段大小写**：Oracle 默认大写，PostgreSQL 默认小写，代码已自动处理
4. **时间戳字段**：建议将经常变化的时间戳字段加入 `ignore-fields`
5. **网络连接**：确保应用服务器能同时访问两个数据库

## 扩展功能

### 1. 自定义主键字段

如果不同表有不同的主键字段名，可以修改 `DataComparisonService` 添加表级别的主键配置。

### 2. 添加数据修复功能

可以扩展服务添加自动修复功能，将差异数据同步到目标数据库。

### 3. 定时任务

集成 Spring Scheduler 实现定时自动验证：

```java
@Scheduled(cron = "0 0 2 * * ?")  // 每天凌晨2点执行
public void scheduledValidation() {
    // 执行验证逻辑
}
```

## 常见问题

### Q1: 如何处理 CLOB/BLOB 字段？

A: 当前版本将 CLOB/BLOB 作为普通对象对比，如需特殊处理，请修改 `normalizeValue` 方法。

### Q2: 如何对比不同 schema 的表？

A: 在配置文件的表名中加入 schema 前缀，例如：`schema1.user_info`。

### Q3: 内存溢出怎么办？

A: 减小 `batch-size` 参数，或增加 JVM 堆内存：`java -Xmx2g -jar app.jar`

## 新增功能

### 1. 总量对比功能

**接口名称：** 总量对比

**功能描述：** 对比指定时间范围内各表的数据总量

**API 接口：** `POST /api/validation/table-count-comparison`

**输入参数：**
- `tableNames`: 表名称数组
- `startTime`: 开始时间
- `endTime`: 结束时间
- `timeField`: 时间字段名（因为不同表的时间字段命名可能不同）

**输出参数：**
- `tableName`: 表名称
- `startTime`: 开始时间
- `endTime`: 结束时间
- `oracleCount`: Oracle数量
- `postgresCount`: PostgreSQL数量
- `ratio`: 比例（以Oracle表为基准）
- `comparisonTime`: 对比时间

**API 调用示例：**
```bash
curl -X POST http://localhost:8080/api/validation/table-count-comparison \
  -H "Content-Type: application/json" \
  -d '{
    "tableNames": ["user_info", "order_info"],
    "startTime": "2023-01-01 00:00:00",
    "endTime": "2023-12-31 23:59:59",
    "timeField": "created_at"
  }'
```

### 2. 单个表数据对比（带过滤条件）

**接口名称：** 单个表数据对比

**功能描述：** 对比单个表的数据，支持忽略字段和时间范围过滤

**API 接口：** `POST /api/validation/table-data-comparison/{tableName}`

**输入参数：**
- `tableName`: 表名称
- `ignoredFields`: 不对比的字段（如时间字段等）
- `startTime`: 开始时间
- `endTime`: 结束时间
- `timeField`: 时间字段名

**输出参数：**
- `oracleCount`: Oracle数量
- `postgresCount`: PostgreSQL数量
- `ratio`: 比例
- `onlyInOracle`: 仅在Oracle中存在的记录
- `onlyInPostgres`: 仅在PostgreSQL中存在的记录
- `fieldDifferences`: 字段差异详情

**API 调用示例：**
```bash
curl -X POST http://localhost:8080/api/validation/table-data-comparison/user_info \
  -H "Content-Type: application/json" \
  -d '{
    "ignoredFields": ["updated_at", "last_modified"],
    "startTime": "2023-01-01 00:00:00",
    "endTime": "2023-12-31 23:59:59",
    "timeField": "created_at"
  }'
```

### 3. 邮箱仿真服务

**功能描述：** 提供邮箱仿真服务，能够接收使用javax.mail基于SMTP发出的邮件发送请求

**服务特点：**
- 启动时自动运行SMTP仿真服务器
- 监听端口 2525
- 接收邮件发送请求后直接返回成功状态
- 记录收到的邮件信息到日志

**API 接口：** `POST /api/mail/send`

**使用方式：**
1. 服务启动后，SMTP仿真服务器会自动运行
2. 客户端可以使用javax.mail向localhost:2525发送邮件
3. 仿真服务会接受邮件并返回成功状态

**通过API发送邮件：**
```bash
curl -X POST http://localhost:8080/api/mail/send \
  -H "Content-Type: application/json" \
  -d '{
    "from": "sender@example.com",
    "to": "recipient@example.com",
    "subject": "测试邮件",
    "body": "这是通过API发送的测试邮件",
    "contentType": "text/plain"
  }'
```

## 贡献

欢迎提交 Issue 和 Pull Request！


用mybatis-plus来做
1,接口名称：总量对比  ;    输入:tableName: 表名称，属性：数组startTime: 开始时间endTime: 结束时间  ;   输出: tableName: 表名称startTime: 开始时间endTime: 结束时间oracleCount:  oracle数量postgresCount: postgres数量ratio:  比例（以oracle表为基准） ;备注:全量查询对比所有的表数据，输出的结果是个数组，展示各个表的数据总量对比情况。 开始时间，结束时间的查询条件，用一个参数传递进去，拼接在sql上最好，因为有些表没有这两个时间字段，有些表这两个时间字段的命名又不一样。

2,接口名称：单个表数据对比  ;    输入:tableName: 表名称，属性：非数组，单个表ignoredField:  不对比的字段。 如时间字段等startTime: 开始时间endTime: 结束时间  ;   输出: oracleCount:  oracle数量postgresCount: postgres数量ratio:  比例,onlyInOracle：onlyInPostgres:fieldDifferences ;备注:ignoredField参数用于控制哪些字段忽略对比。开始时间，结束时间的查询条件，用一个参数传递进去，拼接在sql上最好，因为有些表没有这两个时间字段，有些表这两个时间字段的命名又不一样。





搭建一个springboot 邮箱仿真服务，要求能接收到 请求端用 javax.mail 基于smtp发出的邮件发送请求。











## 许可证

MIT License
