# 项目结构说明

```
simulator/
├── pom.xml                                    # Maven 项目配置文件
├── README.md                                  # 项目说明文档
├── USAGE_GUIDE.md                            # 使用指南
├── sql-examples.sql                          # SQL 对比示例
├── postman-collection.json                   # Postman API 测试集合
├── .gitignore                                # Git 忽略文件配置
├── start.bat                                 # Windows 启动脚本
├── start.sh                                  # Linux/Mac 启动脚本
│
├── src/
│   ├── main/
│   │   ├── java/com/example/dbvalidator/
│   │   │   ├── DbValidatorApplication.java           # 主程序入口
│   │   │   │
│   │   │   ├── config/                               # 配置类
│   │   │   │   ├── DataSourceConfig.java           # 双数据源配置
│   │   │   │   ├── ValidatorProperties.java        # 验证器配置属性
│   │   │   │   └── JacksonConfig.java              # JSON 序列化配置
│   │   │   │
│   │   │   ├── controller/                          # 控制器层
│   │   │   │   └── ValidationController.java       # REST API 控制器
│   │   │   │
│   │   │   ├── service/                             # 服务层
│   │   │   │   ├── DataComparisonService.java      # 数据对比核心服务
│   │   │   │   └── ReportService.java              # 报告生成服务
│   │   │   │
│   │   │   ├── model/                               # 数据模型
│   │   │   │   ├── ComparisonResult.java           # 对比结果模型
│   │   │   │   ├── FieldDifference.java            # 字段差异模型
│   │   │   │   ├── FieldValuePair.java             # 字段值对模型
│   │   │   │   └── UserInfo.java                   # 用户信息实体（示例）
│   │   │   │
│   │   │   └── runner/                              # 启动运行器
│   │   │       └── AutoValidationRunner.java       # 自动验证运行器
│   │   │
│   │   └── resources/
│   │       ├── application.yml                      # 默认配置文件
│   │       ├── application-dev.yml                  # 开发环境配置
│   │       └── application-prod.yml                 # 生产环境配置
│   │
│   └── test/
│       └── java/com/example/dbvalidator/
│           └── service/
│               └── DataComparisonServiceTest.java   # 服务层单元测试
│
└── target/                                           # Maven 构建输出目录（.gitignore）
    └── db-validator-1.0.0.jar                       # 可执行 JAR 包
```

## 核心文件说明

### 1. 配置文件

#### `pom.xml`
Maven 项目配置文件，定义了项目依赖：
- Spring Boot Starter (Web, JDBC, JPA)
- Oracle JDBC Driver
- PostgreSQL JDBC Driver
- HikariCP 连接池
- Lombok
- Jackson

#### `application.yml`
应用主配置文件，包含：
- 双数据源配置（Oracle 和 PostgreSQL）
- 连接池配置
- 验证器配置（表列表、批量大小、忽略字段等）
- 日志配置

#### `application-dev.yml` / `application-prod.yml`
环境特定配置文件，可通过 `--spring.profiles.active=dev` 激活。

---

### 2. 配置类 (config/)

#### `DataSourceConfig.java`
**职责：** 配置双数据源和 JdbcTemplate
- 创建 Oracle 和 PostgreSQL 两个独立的数据源
- 配置 HikariCP 连接池
- 为每个数据源创建对应的 JdbcTemplate

**关键方法：**
```java
@Bean
public DataSource oracleDataSource()      // Oracle 数据源

@Bean
public DataSource postgresDataSource()    // PostgreSQL 数据源

@Bean
public JdbcTemplate oracleJdbcTemplate()  // Oracle 模板

@Bean
public JdbcTemplate postgresJdbcTemplate() // PostgreSQL 模板
```

#### `ValidatorProperties.java`
**职责：** 绑定配置文件中的验证器参数
- 要验证的表列表
- 批量查询大小
- 主键字段名
- 忽略的字段列表

**使用示例：**
```java
@Autowired
private ValidatorProperties properties;

List<String> tables = properties.getTables();
int batchSize = properties.getBatchSize();
```

#### `JacksonConfig.java`
**职责：** 配置 JSON 序列化
- 启用格式化输出（美化 JSON）

---

### 3. 模型类 (model/)

#### `ComparisonResult.java`
**职责：** 封装单个表的对比结果
- 表名
- Oracle 和 PostgreSQL 的记录数
- 数据一致性标志
- 仅在某一数据库中存在的主键列表
- 字段值差异的详细信息
- 对比耗时和时间

#### `FieldDifference.java`
**职责：** 封装单条记录的字段差异
- 主键值
- Oracle 完整数据
- PostgreSQL 完整数据
- 不一致字段的详细对比

#### `FieldValuePair.java`
**职责：** 封装单个字段的值对比
- 字段名
- Oracle 值
- PostgreSQL 值

#### `UserInfo.java`
**职责：** 示例实体类
- 展示如何定义数据库表实体
- 可根据实际表结构修改或新增

---

### 4. 服务层 (service/)

#### `DataComparisonService.java`
**职责：** 核心数据对比逻辑

**关键方法：**

1. **`compareAllTables()`**
   - 对比所有配置的表
   - 返回所有表的对比结果

2. **`compareTable(String tableName)`**
   - 对比单个表
   - 执行完整的对比流程

3. **`getRecordCount()`**
   - 获取表的记录总数

4. **`getPrimaryKeys()`**
   - 获取表的所有主键

5. **`compareRecords()`**
   - 对比共同存在的记录
   - 批量处理大数据量

6. **`compareRow()`**
   - 对比单行数据的所有字段
   - 处理字段大小写差异

7. **`normalizeValue()`**
   - 标准化不同类型的值
   - 处理数值、字符串、日期时间等类型差异

**对比流程：**
```
1. 查询两个数据库的记录总数
2. 获取所有主键列表
3. 找出仅在某一数据库中存在的主键
4. 对比共同存在记录的字段值
5. 批量处理（分批查询和对比）
6. 返回完整的对比结果
```

#### `ReportService.java`
**职责：** 生成和导出对比报告

**关键方法：**

1. **`generateTextReport()`**
   - 生成文本格式的报告
   - 包含详细的差异信息

2. **`generateJsonReport()`**
   - 生成 JSON 格式的报告
   - 便于程序化处理

3. **`saveReportToFile()`**
   - 保存报告到文件

4. **`printSummary()`**
   - 打印简要摘要到日志

---

### 5. 控制器层 (controller/)

#### `ValidationController.java`
**职责：** 提供 REST API 接口

**API 端点：**

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/validation/compare-all` | 验证所有表 |
| POST | `/api/validation/compare-table/{tableName}` | 验证单个表 |
| GET  | `/api/validation/report/text` | 获取文本报告 |
| GET  | `/api/validation/report/json` | 获取 JSON 报告 |
| GET  | `/api/validation/report/download/text` | 下载文本报告 |
| GET  | `/api/validation/report/download/json` | 下载 JSON 报告 |
| GET  | `/api/validation/health` | 健康检查 |

---

### 6. 启动运行器 (runner/)

#### `AutoValidationRunner.java`
**职责：** 应用启动时自动执行验证（可选）

**启用方式：** 取消注释 `@Component` 注解

**功能：**
- 应用启动后自动执行所有表的验证
- 生成并保存文本和 JSON 报告
- 打印验证摘要到日志

---

### 7. 辅助文件

#### `sql-examples.sql`
SQL 对比方式示例，包含 10 种常见的 SQL 对比方法：
- UNION/EXCEPT
- LEFT JOIN
- FULL OUTER JOIN
- HASH/CHECKSUM
- Database Link
- 批量对比
- 时间范围对比
- 导出差异

#### `postman-collection.json`
Postman API 测试集合，包含所有 REST API 的示例请求。

#### `start.bat` / `start.sh`
一键启动脚本：
- 检查 Java 和 Maven 环境
- 编译打包项目
- 启动应用

---

## 数据流转图

```
┌─────────────────┐
│ REST API 请求   │
└────────┬────────┘
         │
         ▼
┌─────────────────────┐
│ ValidationController│  ← 接收请求
└────────┬────────────┘
         │
         ▼
┌────────────────────────┐
│ DataComparisonService  │  ← 执行对比逻辑
│  ├─ compareTable()     │
│  ├─ getRecordCount()   │
│  ├─ getPrimaryKeys()   │
│  └─ compareRecords()   │
└───┬──────────────┬─────┘
    │              │
    ▼              ▼
┌─────────┐  ┌──────────┐
│ Oracle  │  │PostgreSQL│  ← 查询数据
│   DB    │  │    DB    │
└─────────┘  └──────────┘
    │              │
    └──────┬───────┘
           │
           ▼
    ┌─────────────┐
    │ 对比结果     │
    └──────┬──────┘
           │
           ▼
    ┌─────────────┐
    │ReportService│  ← 生成报告
    └──────┬──────┘
           │
           ▼
    ┌─────────────┐
    │ 返回/保存   │  ← 输出结果
    └─────────────┘
```

---

## 配置优先级

```
命令行参数 > 环境变量 > application-{profile}.yml > application.yml
```

**示例：**
```bash
# 使用生产环境配置
java -jar app.jar --spring.profiles.active=prod

# 覆盖数据库密码
java -jar app.jar --spring.datasource.oracle.password=newpassword

# 使用环境变量
export ORACLE_PASSWORD=secret
java -jar app.jar
```

---

## 扩展点

### 1. 添加新的数据源
修改 `DataSourceConfig.java`，添加第三个数据源配置。

### 2. 自定义对比逻辑
修改 `DataComparisonService.java` 中的 `normalizeValue()` 或 `compareRow()` 方法。

### 3. 添加邮件通知
创建 `NotificationService.java`，在发现数据不一致时发送邮件。

### 4. 添加 Web UI
引入前端框架（如 Thymeleaf、Vue.js），提供可视化界面。

### 5. 集成定时任务
使用 Spring Scheduler 或 Quartz，实现定时自动验证。

---

## 依赖关系

```
DbValidatorApplication
    └── ValidationController
            └── DataComparisonService
                    ├── oracleJdbcTemplate
                    ├── postgresJdbcTemplate
                    └── ValidatorProperties
            └── ReportService
                    └── ObjectMapper
```

---

## 性能优化建议

1. **批量大小调优：** 根据表大小和内存情况调整 `batch-size`
2. **连接池配置：** 调整 HikariCP 参数（最大连接数、超时时间等）
3. **索引优化：** 确保主键字段有索引
4. **分段对比：** 对超大表进行分段处理
5. **并行处理：** 使用多线程并行对比多个表
