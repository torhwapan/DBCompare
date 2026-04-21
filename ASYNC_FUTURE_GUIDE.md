# MQ RPC 异步 Future 方案文档

## 📋 方案概述

本方案使用 **CompletableFuture + ConcurrentHashMap** 实现跨集群异步消息收发，适用于跨 vhost/跨集群的 RabbitMQ 通信场景。

### 核心原理

```
发送端流程：
1. 生成唯一 requestId
2. 创建 CompletableFuture 存入 ConcurrentHashMap
3. 发送消息（携带 requestId）到远程集群
4. 立即返回 Future，调用方可以异步等待
5. 收到响应后，通过 requestId 找到对应的 Future 并 complete

接收端流程：
1. 监听远程队列，接收请求
2. 处理业务逻辑
3. 将响应发送回本地队列（携带相同的 requestId）
4. 发送端通过 @RabbitListener 接收响应
5. 通过 requestId 查找 Future 并调用 future.complete(response)
```

### 方案优势

✅ **真正的异步非阻塞** - 不占用线程等待响应  
✅ **支持跨 vhost/跨集群** - 不依赖 Direct Reply-To 机制  
✅ **手动控制超时和重试** - 灵活的超时策略  
✅ **精准匹配** - 通过 requestId 关联请求和响应  
✅ **符合运维监控需求** - 可查询待完成请求数量  

---

## 🏗️ 架构设计

### 组件说明

```
┌─────────────────────────────────────────────────────────────┐
│                     你的服务（调用方）                        │
│                                                             │
│  ┌──────────────┐      ┌──────────────┐      ┌───────────┐ │
│  │  Controller  │─────>│ MQRPCService │─────>│   Map     │ │
│  │              │      │              │      │<requestId,│ │
│  │ HTTP请求     │      │ 发送请求     │      │ Future>   │ │
│  └──────────────┘      └──────┬───────┘      └─────┬─────┘ │
│                               │                     │       │
│                               │ 发送到远程MQ         │       │
│                               │                     │       │
│  ┌──────────────┐      ┌──────▼───────┐      ┌──────▼─────┐ │
│  │  @Rabbit     │<─────│  本地MQ      │<─────│ 远程MQ    │ │
│  │  Listener    │      │  (接收响应)  │      │(发送请求) │ │
│  │  接收响应    │      │              │      │           │ │
│  └──────────────┘      └──────────────┘      └───────────┘ │
└─────────────────────────────────────────────────────────────┘
                                │
                                │ 网络/跨集群
                                ▼
┌─────────────────────────────────────────────────────────────┐
│                   对方服务（被调用方）                        │
│                                                             │
│  ┌──────────────┐      ┌──────────────┐      ┌───────────┐ │
│  │  @Rabbit     │<─────│  远程MQ      │<─────│ 本地MQ    │ │
│  │  Listener    │      │  (接收请求)  │      │(发送响应) │ │
│  │  接收请求    │      │              │      │           │ │
│  └──────┬───────┘      └──────────────┘      └───────────┘ │
│         │                                                   │
│         │ 处理业务逻辑                                       │
│         ▼                                                   │
│  ┌──────────────┐                                          │
│  │  处理请求    │                                          │
│  │  发送响应    │─────────────────────────────────┐        │
│  │  (requestId)│                                 │        │
│  └──────────────┘                                 │        │
└───────────────────────────────────────────────────┼────────┘
                                                    │
                                                    ▼
                                      响应回到你的本地MQ队列
```

---

## 📦 核心代码

### 1. MQRPCService（发送端服务）

**核心字段：**
```java
// 待完成的请求 Future 映射表
private final ConcurrentHashMap<String, CompletableFuture<MQResponse>> pendingRequests 
    = new ConcurrentHashMap<>();
```

**核心方法：**

#### 异步发送（推荐）
```java
/**
 * 异步发送请求并等待响应（非阻塞）
 * 
 * @param request 请求对象
 * @param timeoutMs 超时时间（毫秒）
 * @return CompletableFuture<MQResponse> 异步响应Future
 */
public CompletableFuture<MQResponse> sendAsync(MQRequest request, long timeoutMs)
```

#### 同步发送（阻塞调用）
```java
/**
 * 同步发送请求并等待响应（阻塞调用）
 * 内部调用异步方法，然后通过 Future.get() 阻塞等待
 */
public MQResponse sendAndReceive(MQRequest request, long timeoutMs)
```

#### 响应监听
```java
/**
 * 监听本地响应队列，接收对方的回复
 * 通过 requestId 匹配请求和响应
 */
@RabbitListener(queues = "${mq.local.queue:loadtest.local.queue}")
public void handleResponse(MQResponse response)
```

### 2. MQRPCHandler（接收端服务）

部署在对方服务上，负责：
1. 监听远程队列接收请求
2. 处理业务逻辑
3. 将响应发送回本地队列

```java
@RabbitListener(queues = "${mq.remote.queue:loadtest.remote.queue}")
public void handleRequest(MQRequest request) {
    // 1. 处理业务逻辑
    MQResponse response = processBusinessLogic(request);
    
    // 2. 将响应发送回本地队列
    localRabbitTemplate.convertAndSend(
        rabbitMQConfig.getLocalExchange(),
        rabbitMQConfig.getLocalRoutingKey(),
        response
    );
}
```

---

## 🚀 使用示例

### 示例 1：异步调用（推荐）

```java
@Autowired
private MQRPCService mqRPCService;

public void asyncCall() {
    // 创建请求
    MQRequest request = MQRequest.create("DATA_QUERY", Map.of("table", "user_info", "id", 123));
    
    // 异步发送，立即返回 Future
    CompletableFuture<MQResponse> future = mqRPCService.sendAsync(request, 5000);
    
    // 可以做其他事情...
    System.out.println("请求已发送，等待响应...");
    
    // 等待响应（阻塞）
    try {
        MQResponse response = future.get(5000, TimeUnit.MILLISECONDS);
        System.out.println("收到响应: " + response.getResponseData());
    } catch (Exception e) {
        System.err.println("请求失败: " + e.getMessage());
    }
}
```

### 示例 2：同步调用

```java
public void syncCall() {
    // 创建请求
    MQRequest request = MQRequest.create("HEALTH_CHECK", null);
    
    // 同步发送并等待响应（阻塞）
    MQResponse response = mqRPCService.sendAndReceive(request, 3000);
    
    if (response.getStatusCode() == 200) {
        System.out.println("服务健康: " + response.getResponseData());
    } else {
        System.err.println("服务异常: " + response.getErrorMessage());
    }
}
```

### 示例 3：批量异步调用

```java
public void batchAsyncCall() {
    List<CompletableFuture<MQResponse>> futures = new ArrayList<>();
    
    // 批量发送请求
    for (int i = 0; i < 10; i++) {
        MQRequest request = MQRequest.create("DATA_QUERY", Map.of("id", i));
        CompletableFuture<MQResponse> future = mqRPCService.sendAsync(request, 5000);
        futures.add(future);
    }
    
    System.out.println("已发送 10 个请求");
    
    // 等待所有响应
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
        .thenRun(() -> {
            futures.forEach(future -> {
                try {
                    MQResponse response = future.get();
                    System.out.println("响应: " + response.getResponseData());
                } catch (Exception e) {
                    System.err.println("请求失败: " + e.getMessage());
                }
            });
        });
}
```

### 示例 4：HTTP 接口调用

#### 方式 1：同步接口
```bash
curl -X POST http://localhost:8080/api/mq-rpc/send \
  -H "Content-Type: application/json" \
  -d '{
    "requestType": "DATA_QUERY",
    "requestData": {"table": "user_info", "id": 123},
    "timeout": 5000
  }'
```

#### 方式 2：异步接口（立即返回 requestId）
```bash
curl -X POST http://localhost:8080/api/mq-rpc/send-async \
  -H "Content-Type: application/json" \
  -d '{
    "requestType": "DATA_INSERT",
    "requestData": {"table": "user_info", "data": {"name": "test"}},
    "timeout": 5000
  }'

# 响应：
# {
#   "success": true,
#   "message": "请求已发送",
#   "requestId": "uuid-xxxxx",
#   "timeout": 5000
# }
```

#### 方式 3：Spring 异步接口（DeferredResult）
```bash
curl -X POST http://localhost:8080/api/mq-rpc/send-deferred \
  -H "Content-Type: application/json" \
  -d '{
    "requestType": "HEALTH_CHECK",
    "timeout": 3000
  }'

# 特点：不占用 Servlet 线程，响应就绪后立即返回
```

---

## 🔧 配置说明

### application-mq-rpc.yml

```yaml
mq:
  # 本地MQ（接收响应）
  local:
    host: localhost              # 本地 RabbitMQ 地址
    port: 5672
    username: guest
    password: guest
    virtual-host: /
    exchange: loadtest.local.exchange   # 本地交换机
    queue: loadtest.local.queue         # 本地队列（接收响应）
    routing-key: loadtest.local

  # 远程MQ（发送请求）
  remote:
    host: 192.168.1.100          # 远程 RabbitMQ 地址
    port: 5672
    username: admin
    password: admin123
    virtual-host: /prod
    exchange: loadtest.remote.exchange    # 远程交换机
    queue: loadtest.remote.queue          # 远程队列（发送请求）
    routing-key: loadtest.remote
```

### 配置注意事项

⚠️ **重要说明：**
- `local` 配置是你本地的 RabbitMQ，用于**接收对方的响应**
- `remote` 配置是对方的 RabbitMQ，用于**发送请求到对方**
- 对方服务需要部署 `MQRPCHandler`，监听你的 `remote.queue`
- 对方处理完请求后，需要将响应发送到**你的本地队列**（`local.queue`）

---

## 📊 监控和运维

### 1. 健康检查
```bash
curl http://localhost:8080/api/mq-rpc/health

# 响应：
# {
#   "status": "UP",
#   "service": "MQ-RPC-Service",
#   "pendingRequests": 5,          # 当前待完成的请求数
#   "timestamp": 1234567890
# }
```

### 2. 统计信息
```bash
curl http://localhost:8080/api/mq-rpc/stats

# 响应：
# {
#   "pendingRequests": 5,
#   "service": "MQ-RPC-Service",
#   "mode": "Async-Future",
#   "timestamp": 1234567890
# }
```

### 3. 日志监控

关键日志关键字：
- `发送异步RPC请求` - 请求发送成功
- `异步RPC请求已发送` - 消息已发送到 MQ
- `收到RPC响应` - 收到对方响应
- `RPC响应已匹配` - 成功匹配请求和响应
- `RPC请求超时` - 请求超时
- `RPC响应无法匹配` - 响应无法找到对应的 Future（可能已超时）

### 4. 待完成请求监控

```java
@Autowired
private MQRPCService mqRPCService;

// 获取当前待完成的请求数量
int pendingCount = mqRPCService.getPendingRequestCount();
System.out.println("当前待完成请求数: " + pendingCount);

// 如果数量持续增长，可能说明对方响应慢或网络有问题
if (pendingCount > 100) {
    log.warn("待完成请求数过多，可能存在性能问题");
}
```

---

## ⚠️ 注意事项

### 1. 超时处理

每个请求都会设置超时定时器，超时后会自动从 Map 中移除并标记 Future 为异常完成。

```java
// 超时后会自动触发
future.completeExceptionally(new TimeoutException("请求超时"));
```

### 2. 内存管理

待完成的请求会存储在内存中（ConcurrentHashMap），如果对方响应慢会导致内存占用增加。

**建议：**
- 设置合理的超时时间（默认 5 秒）
- 监控 `pendingRequests` 数量
- 服务关闭时调用 `clearPendingRequests()` 清理

### 3. 幂等性

由于使用 requestId 匹配，建议：
- 每个请求使用唯一的 requestId（MQRequest.create() 自动生成 UUID）
- 对方服务需要保证不重复处理相同 requestId 的请求

### 4. 错误处理

```java
CompletableFuture<MQResponse> future = mqRPCService.sendAsync(request, 5000);

future.exceptionally(ex -> {
    if (ex.getCause() instanceof TimeoutException) {
        System.err.println("请求超时");
    } else {
        System.err.println("请求失败: " + ex.getMessage());
    }
    return null;
});
```

---

## 🔍 故障排查

### 问题 1：请求超时

**现象：** 日志显示 `RPC请求超时`

**排查步骤：**
1. 检查对方服务是否正常运行
2. 检查远程 MQ 配置是否正确（host/port/username/password）
3. 检查对方是否正确监听 `remote.queue`
4. 检查对方是否正确发送响应到 `local.queue`
5. 检查网络连通性

### 问题 2：响应无法匹配

**现象：** 日志显示 `RPC响应无法匹配`

**可能原因：**
- 请求已超时被移除
- requestId 不一致
- 重复响应（对方发送了两次响应）

**解决方案：**
- 增加超时时间
- 检查 requestId 传递是否正确
- 检查对方是否有重复发送逻辑

### 问题 3：待完成请求数持续增长

**现象：** `getPendingRequestCount()` 返回值持续增长

**可能原因：**
- 对方服务响应慢
- 网络延迟高
- 请求量过大，对方处理能力不足

**解决方案：**
- 优化对方服务处理逻辑
- 增加并发处理能力
- 限流或降级

---

## 📝 最佳实践

### 1. 异步优先

优先使用异步调用，避免阻塞主线程：

```java
// ✅ 推荐：异步调用
CompletableFuture<MQResponse> future = mqRPCService.sendAsync(request, 5000);
future.thenAccept(response -> {
    System.out.println("收到响应: " + response);
});

// ❌ 不推荐：在主线程中同步调用
MQResponse response = mqRPCService.sendAndReceive(request, 5000);
```

### 2. 合理设置超时

根据业务场景设置合适的超时时间：

```java
// 健康检查：短超时
mqRPCService.sendAsync(healthRequest, 3000);

// 复杂查询：长超时
mqRPCService.sendAsync(queryRequest, 10000);
```

### 3. 批量请求优化

批量发送时，使用 `CompletableFuture.allOf()` 等待所有响应：

```java
List<CompletableFuture<MQResponse>> futures = requests.stream()
    .map(req -> mqRPCService.sendAsync(req, 5000))
    .collect(Collectors.toList());

CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
    .thenRun(() -> processAllResponses(futures));
```

### 4. 优雅关闭

服务关闭时清理待完成请求：

```java
@PreDestroy
public void shutdown() {
    mqRPCService.clearPendingRequests();
}
```

---

## 🎯 总结

本方案通过 **CompletableFuture + ConcurrentHashMap + requestId** 实现了跨集群异步 RPC 调用，具有以下特点：

✅ 真正的异步非阻塞  
✅ 支持跨 vhost/跨集群  
✅ 灵活的超时和重试策略  
✅ 精准的请求响应匹配  
✅ 易于监控和运维  

适用于你的运维场景：
- 跨集群服务调用
- 异步消息处理
- 系统监控和排查
- SOP 自动化执行

---

**文档版本：** v1.0  
**更新日期：** 2026-04-21  
**维护者：** 运维团队
