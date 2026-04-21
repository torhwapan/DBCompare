# 跨集群MQ服务使用指南

## 📋 功能简介

这是一个**跨集群MQ消息服务**，实现跨RabbitMQ集群的请求-响应模式通信。

### 核心特性

✅ **双集群独立** - 本地集群接收响应，远程集群发送请求  
✅ **异步等待** - 使用CompletableFuture实现请求-响应匹配  
✅ **手动关联** - 通过requestId自动匹配请求和响应  
✅ **同步/异步支持** - 支持阻塞等待响应或异步发送  
✅ **超时控制** - 可配置请求超时时间  
✅ **类型安全** - 使用JSON序列化，支持复杂对象传输  

---

## 🎯 使用场景

### 场景：跨MQ集群异步通信

```
你的应用（本地集群）                对方服务（远程集群）
       │                                  │
       │  remoteRabbitTemplate           │
       │  发送到远程集群                  │
       ├─────────────────────────────────>│ 收到请求
       │                                  │ 处理业务
       │                                  │
       │                                  │ localRabbitTemplate
       │                                  │ 发送到你的集群
       │<─────────────────────────────────│
       │                                  │
       │  localRabbitTemplate            │
       │  接收响应（匹配requestId）       │
```

**关键说明**：
- 你**发送**到对方的队列（对方集群）
- 对方**发送**到你的队列（你的集群）
- 两个完全独立的RabbitMQ集群
- 通过requestId手动关联请求和响应

**典型应用**：
- 跨机房服务调用
- 跨集群数据同步
- 合作伙伴系统对接
- 微服务跨网络通信

---

## 🚀 快速开始

### 1. 配置双MQ集群

编辑 `application.yml`：

```yaml
mq:
  # 你的本地MQ集群（接收对方的响应）
  local:
    host: localhost                    # 你的MQ地址
    port: 5672
    username: guest
    password: guest
    virtual-host: /
    exchange: my.local.exchange        # 你的交换机
    queue: my.local.queue              # 你的队列（对方发送到这里）
    routing-key: my.local

  # 对方的远程MQ集群（你发送请求到对方）
  remote:
    host: 192.168.1.100               # 对方的MQ地址
    port: 5672
    username: admin
    password: admin123
    virtual-host: /prod
    exchange: partner.remote.exchange # 对方的交换机
    queue: partner.remote.queue       # 对方的队列
    routing-key: partner.remote
```

### 2. 工作流程

```
1. 你的应用调用: mqRPCService.sendAndReceive(request)

2. 发送请求：
   ├─ 生成唯一 requestId
   ├─ 创建 CompletableFuture 并注册到 pendingRequests Map
   ├─ 使用 remoteRabbitTemplate 发送到对方集群
   └─ 阻塞等待响应

3. 对方服务处理：
   ├─ 从对方的队列接收请求
   ├─ 处理业务逻辑
   ├─ 构建 MQResponse（包含相同的requestId）
   └─ 使用对方的 rabbitTemplate 发送到你的集群

4. 你的服务接收响应：
   ├─ @RabbitListener 从你的本地队列接收响应
   ├─ 通过 requestId 从 pendingRequests 找到等待的Future
   ├─ 调用 future.complete(response) 唤醒阻塞的线程
   └─ 返回响应给调用方
```

### 3. 发送请求

**方式一：通过HTTP接口（推荐）**

```bash
curl -X POST http://localhost:8080/api/mq-rpc/send \
  -H "Content-Type: application/json" \
  -d '{
    "requestType": "DATA_QUERY",
    "requestData": {
      "table": "user_info",
      "id": 123
    },
    "timeout": 5000
  }'
```

**方式二：在代码中调用**

```java
@Autowired
private MQRPCService mqRPCService;

// 1. 创建请求
MQRequest request = MQRequest.create(
    "DATA_QUERY",  // 请求类型
    Map.of("table", "user_info", "id", 123)  // 请求数据
);

// 2. 发送并等待响应（同步）
MQResponse response = mqRPCService.sendAndReceive(request, 5000);

// 3. 检查结果
if (response.getStatusCode() == 200) {
    System.out.println("成功: " + response.getResponseData());
} else {
    System.out.println("失败: " + response.getErrorMessage());
}
```

---

## 📖 详细说明

### 工作原理

```
1. 你的应用调用 mqRPCService.sendAndReceive(request)

2. 发送请求到对方集群：
   ├─ 生成唯一 requestId (UUID)
   ├─ 创建 CompletableFuture 并注册到 pendingRequests Map
   ├─ remoteRabbitTemplate.convertAndSend(对方exchange, 对方routingKey, request)
   └─ future.get(timeout) 阻塞等待

3. 对方服务处理请求：
   ├─ 对方从他们的队列接收请求
   ├─ 处理业务逻辑
   ├─ 构建 MQResponse (包含相同的requestId)
   └─ 对方的 rabbitTemplate.send(你的exchange, 你的routingKey, response)

4. 你的服务接收响应：
   ├─ @RabbitListener(queues = "你的队列") 接收响应
   ├─ MQRPCHandler.handleResponse(response) 被调用
   ├─ 通过 requestId 从 pendingRequests 找到等待的Future
   ├─ future.complete(response) 唤醒阻塞线程
   └─ 返回response给调用方
```

### 关键机制：requestId手动匹配

```java
// 发送时：
pendingRequests.put(requestId, future);  // 注册等待
remoteRabbitTemplate.send(...);          // 发送到对方
future.get(timeout);                     // 阻塞等待

// 接收时：
@RabbitListener(queues = "你的队列")
public void handleResponse(MQResponse response) {
    CompletableFuture<MQResponse> future = pendingRequests.get(response.getRequestId());
    future.complete(response);  // 唤醒等待的线程
}
```

**对方需要做的**：
对方只需要在你的本地队列上发送响应消息，包含相同的requestId即可：

```java
// 对方的代码
@RabbitListener(queues = "对方的队列")
public void handleRequest(MQRequest request) {
    // 处理请求
    Object result = process(request);
    
    // 构建响应（必须包含相同的requestId）
    MQResponse response = new MQResponse();
    response.setRequestId(request.getRequestId());  // 关键！
    response.setStatusCode(200);
    response.setResponseData(result);
    
    // 发送到你的集群
    rabbitTemplate.send("你的exchange", "你的routingKey", response);
}
```

---

## 🔧 接口说明

### 1. 同步发送（等待响应）

**HTTP接口**：
```bash
POST /api/mq-rpc/send
```

**请求体**：
```json
{
  "requestType": "DATA_QUERY",       // 必填，请求类型
  "requestData": {                   // 必填，请求数据（任意JSON）
    "key": "value"
  },
  "timeout": 5000                    // 可选，超时时间（毫秒），默认5000
}
```

**响应**：
```json
{
  "success": true,
  "response": {
    "requestId": "uuid",
    "statusCode": 200,
    "responseData": {...},
    "errorMessage": null,
    "responseTime": "2026-04-21T10:30:00",
    "processingTime": 100
  }
}
```

### 2. 异步发送（不等待响应）

**HTTP接口**：
```bash
POST /api/mq-rpc/send-async
```

**请求体**：
```json
{
  "requestType": "DATA_INSERT",
  "requestData": {...}
}
```

**响应**：
```json
{
  "success": true,
  "message": "请求已发送",
  "requestId": "uuid"
}
```

### 3. 健康检查

```bash
GET /api/mq-rpc/health
```

---

## 💡 使用示例

### 示例1：查询数据

```bash
curl -X POST http://localhost:8080/api/mq-rpc/send \
  -H "Content-Type: application/json" \
  -d '{
    "requestType": "DATA_QUERY",
    "requestData": {
      "table": "user_info",
      "conditions": {
        "status": "ACTIVE",
        "age": {"$gt": 18}
      }
    },
    "timeout": 10000
  }'
```

### 示例2：插入数据

```bash
curl -X POST http://localhost:8080/api/mq-rpc/send \
  -H "Content-Type: application/json" \
  -d '{
    "requestType": "DATA_INSERT",
    "requestData": {
      "table": "order_info",
      "data": {
        "userId": 12345,
        "amount": 99.99,
        "product": "商品A"
      }
    }
  }'
```

### 示例3：批量处理

```bash
curl -X POST http://localhost:8080/api/mq-rpc/send-async \
  -H "Content-Type: application/json" \
  -d '{
    "requestType": "BATCH_PROCESS",
    "requestData": {
      "batchId": "BATCH_20260421_001",
      "records": [
        {"id": 1, "action": "UPDATE"},
        {"id": 2, "action": "UPDATE"},
        {"id": 3, "action": "DELETE"}
      ]
    }
  }'
```

### 示例4：代码中调用

```java
@RestController
public class YourController {
    
    @Autowired
    private MQRPCService mqRPCService;
    
    @PostMapping("/api/process")
    public Result processData(@RequestBody Data data) {
        // 构建请求
        MQRequest request = MQRequest.create(
            "PROCESS_DATA",
            data
        );
        
        // 发送并等待响应
        MQResponse response = mqRPCService.sendAndReceive(request, 10000);
        
        // 处理结果
        if (response.getStatusCode() == 200) {
            return Result.success(response.getResponseData());
        } else {
            return Result.error(response.getErrorMessage());
        }
    }
}
```

---

## ⚙️ 远程服务部署

在远程MQ所在的服务上，需要部署 `MQRPCHandler` 来接收请求：

```java
@Service
public class YourRemoteService {
    
    @RabbitListener(queues = "${mq.remote.queue}")
    public MQResponse handleRequest(MQRequest request) {
        // 处理请求
        Object result = processBusinessLogic(request);
        
        // 返回响应（Spring会自动发送回调用方）
        return MQResponse.success(
            request.getRequestId(),
            result,
            processingTime
        );
    }
    
    private Object processBusinessLogic(MQRequest request) {
        // 你的业务逻辑
        switch (request.getRequestType()) {
            case "DATA_QUERY":
                return queryData(request);
            case "DATA_INSERT":
                return insertData(request);
            // ... 更多类型
        }
    }
}
```

**注意**：示例代码中的 `MQRPCHandler` 已经实现了基本的处理逻辑，你可以直接修改 `processBusinessLogic()` 方法来实现你的业务。

---

## 🔍 常见问题

### Q1: 请求超时怎么办？

**原因**：
1. 远程服务未启动或未监听队列
2. 网络不通
3. 处理时间过长

**解决方案**：
```java
// 增加超时时间
MQResponse response = mqRPCService.sendAndReceive(request, 30000); // 30秒
```

### Q2: 如何确保请求和响应正确匹配？

**答案**：Spring AMQP自动通过 `correlationId` 处理，你不需要手动管理。每次请求都会生成唯一的关联ID，响应会自动匹配到对应的请求。

### Q3: 异步发送如何获取结果？

**方案**：异步发送不等待响应，适用于：
- 不需要结果的操作（如日志记录）
- 通过其他方式获取结果（如回调接口、轮询）

如果需要结果，请使用同步方式。

### Q4: 如何调试MQ消息？

**方法1：查看日志**
```bash
# 开启RabbitMQ调试日志
logging:
  level:
    org.springframework.amqp: DEBUG
```

**方法2：使用RabbitMQ管理界面**
```
http://localhost:15672
用户名: guest
密码: guest
```

在 "Queues" 页面查看队列消息数，在 "Exchanges" 查看交换机路由。

### Q5: 性能如何优化？

**建议**：
1. **连接池**：Spring AMQP默认使用连接池，无需额外配置
2. **并发消费**：在远程服务上配置多个消费者
   ```yaml
   spring:
     rabbitmq:
       listener:
         simple:
           concurrency: 5      # 最小消费者数
           max-concurrency: 10 # 最大消费者数
   ```
3. **消息确认**：启用手动ACK确保消息不丢失
   ```yaml
   spring:
     rabbitmq:
       listener:
         simple:
           acknowledge-mode: manual
   ```

---

## 📊 架构说明

### 组件关系

```
┌─────────────────────────────────────────────────────────┐
│  你的应用（本地集群）                                    │
│                                                          │
│  ┌──────────────┐                                        │
│  │ HTTP接口     │                                        │
│  │ (Controller) │                                        │
│  └──────┬───────┘                                        │
│         │                                                │
│         ▼                                                │
│  ┌──────────────┐                                        │
│  │ MQRPCService │                                        │
│  │ (发送+匹配)  │                                        │
│  └──────┬───────┘                                        │
│         │                                                │
│         ├── 1. remoteRabbitTemplate ────> 发送到对方集群  │
│         │                                                │
│         └── 3. localRabbitTemplate <──── 接收对方响应     │
│                (通过@RabbitListener)                      │
└─────────────────────────────────────────────────────────┘
         │                                        ▲
         │ 发送请求到对方队列                      │ 对方发送响应到你的队列
         ▼                                        │
┌─────────────────────────────────────────────────────────┐
│  对方服务（远程集群）                                    │
│                                                          │
│         ┌───────────────────────┐                        │
│         │ 对方接收你的请求      │                        │
│         │ @RabbitListener       │                        │
│         │ (对方的队列)          │                        │
│         └───────────┬───────────┘                        │
│                     │                                    │
│                     ▼                                    │
│         ┌───────────────────────┐                        │
│         │ 对方处理业务逻辑      │                        │
│         └───────────┬───────────┘                        │
│                     │                                    │
│                     ▼                                    │
│         ┌───────────────────────┐                        │
│         │ 对方发送响应到你的集群│                        │
│         │ rabbitTemplate.send() │───────────────────────>│
│         └───────────────────────┘                        │
└─────────────────────────────────────────────────────────┘
```

---

## 🎯 总结

### 核心流程：

1. **你发送请求** → 发送到对方的MQ集群
2. **对方处理** → 对方从他们的队列接收并处理
3. **对方回复** → 对方发送到你的MQ集群
4. **你接收响应** → 你从本地队列接收并匹配requestId

### 关键技术：

- ✅ **remoteRabbitTemplate** - 发送到对方集群
- ✅ **localRabbitTemplate** - 从本地集群接收
- ✅ **CompletableFuture** - 实现异步等待
- ✅ **pendingRequests Map** - 通过requestId匹配请求和响应
- ✅ **@RabbitListener** - 监听本地队列接收响应

### 你只需要做3件事：

1. **配置双MQ** - 本地集群 + 远程集群
2. **发送请求** - `mqRPCService.sendAndReceive(request)`
3. **对方配合** - 对方需要在响应中包含相同的requestId

### 对方需要做的：

```java
// 1. 监听他们的队列接收你的请求
@RabbitListener(queues = "对方的队列")
public void handleRequest(MQRequest request) {
    // 2. 处理业务
    Object result = process(request);
    
    // 3. 构建响应（必须包含requestId）
    MQResponse response = new MQResponse();
    response.setRequestId(request.getRequestId());  // ← 关键！
    response.setResponseData(result);
    
    // 4. 发送到你的集群
    rabbitTemplate.send("你的exchange", "你的routingKey", response);
}
```

---

## 📞 技术支持

如有问题，请查看：
1. 应用日志（重点看 MQRPCService 和 MQRPCHandler 的日志）
2. RabbitMQ管理界面（http://localhost:15672）
3. 网络连接（telnet测试MQ端口）
