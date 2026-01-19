# 新增功能说明

## 需求一：总量对比

### 接口名称
总量对比

### 输入参数
- `tableName`: 表名称（数组）
- `startTime`: 开始时间
- `endTime`: 结束时间
- `timeField`: 时间字段名（因为不同表的时间字段命名可能不同）

### 输出参数
- `tableName`: 表名称
- `startTime`: 开始时间
- `endTime`: 结束时间
- `oracleCount`: Oracle数量
- `postgresCount`: PostgreSQL数量
- `ratio`: 比例（以Oracle表为基准）
- `comparisonTime`: 对比时间

### API 调用示例
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

## 需求二：单个表数据对比

### 接口名称
单个表数据对比

### 输入参数
- `tableName`: 表名称
- `ignoredFields`: 不对比的字段（如时间字段等）
- `startTime`: 开始时间
- `endTime`: 结束时间
- `timeField`: 时间字段名

### 输出参数
- `oracleCount`: Oracle数量
- `postgresCount`: PostgreSQL数量
- `ratio`: 比例
- `onlyInOracle`: 仅在Oracle中存在的记录
- `onlyInPostgres`: 仅在PostgreSQL中存在的记录
- `fieldDifferences`: 字段差异详情

### API 调用示例
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

## 需求三：邮箱仿真服务

### 功能说明
提供邮箱仿真服务，能够接收使用javax.mail基于SMTP发出的邮件发送请求。

### 服务特点
- 启动时自动运行SMTP仿真服务器
- 监听端口 2525
- 接收邮件发送请求后直接返回成功状态
- 记录收到的邮件信息到日志

### 使用方式
1. 服务启动后，SMTP仿真服务器会自动运行
2. 客户端可以使用javax.mail向localhost:2525发送邮件
3. 仿真服务会接受邮件并返回成功状态

### 通过API发送邮件的替代方式
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

### Java客户端示例
```java
import com.example.dbvalidator.util.MailClientExample;

// 向仿真SMTP服务器发送邮件
MailClientExample.sendMailToSimulator(
    "localhost", 
    2525, 
    "sender@example.com", 
    "recipient@example.com", 
    "测试邮件", 
    "邮件内容"
);
```

## 注意事项
1. 时间字段过滤功能适用于那些具有时间字段的表，对于没有时间字段的表，该过滤会被忽略
2. SMTP仿真服务器使用端口2525（非标准SMTP端口），避免需要管理员权限
3. 所有新增功能都与原有的数据对比功能保持兼容