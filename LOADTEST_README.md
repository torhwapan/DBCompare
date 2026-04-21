# 性能压测模块使用指南

## 📋 目录

- [模块简介](#模块简介)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [流量分布策略](#流量分布策略)
- [API接口说明](#api接口说明)
- [使用示例](#使用示例)
- [监控与报告](#监控与报告)
- [常见问题](#常见问题)

---

## 模块简介

性能压测模块是Simulator项目的核心功能之一，专门用于对**MQ消息队列**和**HTTP接口**进行性能压测。

### 核心特性

✅ **精准速率控制** - 基于令牌桶算法，支持11.6条/秒到116条/秒的精准控制  
✅ **多种压测场景** - 支持1倍到10倍压测（100万~1000万条/天）  
✅ **流量分布策略** - 均匀分布、峰值模拟、阶梯递增  
✅ **实时监控** - QPS、响应时间、成功率、P95/P99延迟  
✅ **批量发送** - 支持批量组装和异步发送，提高吞吐量  
✅ **任务管理** - 支持启动、暂停、恢复、停止、动态调速  

### 基准换算

| 压测倍数 | 日数据量 | 平均速率 |
|---------|---------|---------|
| 1x | 100万条 | ~11.6条/秒 |
| 2x | 200万条 | ~23.1条/秒 |
| 5x | 500万条 | ~57.9条/秒 |
| 10x | 1000万条 | ~115.7条/秒 |

---

## 快速开始

### 1. 配置目标服务

编辑 `application.yml`，配置你的MQ或HTTP目标服务：

```yaml
# MQ压测配置
loadtest:
  mq:
    broker-url: tcp://localhost:61616  # MQ地址
    queue-name: test.queue             # 队列名称
    
# HTTP压测配置
  http:
    base-url: http://localhost:8080    # 目标服务地址
    endpoint: /api/data/receive        # 目标接口
```

### 2. 启动压测（HTTP示例）

```bash
curl -X POST http://localhost:8080/api/loadtest/http/start \
  -H "Content-Type: application/json" \
  -d '{
    "multiplier": 3,
    "taskName": "HTTP压测-3倍",
    "durationSeconds": 3600,
    "targetUrl": "http://localhost:8080/api/data/receive"
  }'
```

### 3. 查看实时指标

```bash
curl http://localhost:8080/api/loadtest/{taskId}/metrics
```

### 4. 停止压测

```bash
curl -X POST http://localhost:8080/api/loadtest/{taskId}/stop
```

---

## 配置说明

### 完整配置示例

```yaml
loadtest:
  # MQ配置
  mq:
    broker-url: tcp://localhost:61616    # MQ Broker地址
    queue-name: test.queue               # 队列名称
    concurrent-producers: 10             # 并发生产者数量
    batch-size: 100                      # 批量发送大小
    persistent: true                     # 是否启用持久化

  # HTTP配置
  http:
    base-url: http://localhost:8080      # 目标服务地址
    endpoint: /api/data/receive          # 目标接口路径
    method: POST                         # HTTP方法
    concurrent-threads: 20               # 并发线程数
    connect-timeout: 5000                # 连接超时（毫秒）
    read-timeout: 10000                  # 读取超时（毫秒）

  # 压测任务默认配置
  task:
    base-rate-per-second: 11.57          # 基础速率（100万/天）
    multiplier: 1                        # 压测倍数（1-10）
    duration-seconds: 86400              # 持续时间（秒）
    batch-size: 100                      # 批量大小
    ramp-up-seconds: 300                 # 预热时间（秒）
    traffic-strategy: uniform            # 流量分布策略
```

---

## 流量分布策略

Simulator支持三种流量分布策略，模拟不同的真实场景。

### 策略一：均匀分布（uniform）

**适用场景**：系统全天负载相对稳定，没有明显的高峰低谷。

**特点**：
- 24小时内均匀发送消息
- 速率恒定 = 基础速率 × 倍数
- 最简单、最基础的压测模式

**配置示例**：
```yaml
loadtest:
  task:
    traffic-strategy: uniform
    base-rate-per-second: 11.57  # 基础速率
    multiplier: 5                # 5倍压测

# 实际效果：
# 全天恒定速率 = 11.57 × 5 = 57.85 条/秒
# 日发送量 = 57.85 × 86400 ≈ 500万条
```

**速率曲线**：
```
速率(条/秒)
60 |━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ 57.85
   |
   | 00:00                        12:00              24:00
```

---

### 策略二：峰值模拟（peak）

**适用场景**：系统有明显的高峰和低谷，如：
- 办公系统：工作时间（9-18点）为高峰，夜间为低谷
- 电商系统：早晚为高峰，凌晨为低谷
- 报表系统：上午为高峰，下午为低谷

**特点**：
- 峰值时段：高速率发送
- 谷值时段：低速率发送
- 更贴近真实业务场景

**配置示例**：
```yaml
loadtest:
  task:
    traffic-strategy: peak
    base-rate-per-second: 11.57    # 基础速率
    multiplier: 5                  # 5倍压测
    peak-config:
      peak-start-hour: 9           # 峰值开始时间：9点
      peak-end-hour: 18            # 峰值结束时间：18点
      peak-multiplier: 3.0         # 峰值时段：3倍速率
      valley-multiplier: 0.3       # 谷值时段：0.3倍速率

# 实际效果：
# 峰值时段（9-18点）：57.85 × 3.0 = 173.55 条/秒
# 谷值时段（0-9点, 18-24点）：57.85 × 0.3 = 17.36 条/秒
```

**速率曲线**：
```
速率(条/秒)
180 |          ┌─────────────────────────┐ 173.55（峰值）
    |          │                         │
 60 |──────────┘                         └────────── 57.85（平均）
    |
 20 |─────┘                             └───── 17.36（谷值）
    |
    | 00:00        09:00               18:00        24:00
    |◄────谷值────►◄──────峰值─────────►◄────谷值───►
```

**完整配置示例**：
```bash
curl -X POST http://localhost:8080/api/loadtest/http/start \
  -H "Content-Type: application/json" \
  -d '{
    "multiplier": 5,
    "taskName": "HTTP压测-峰值模拟",
    "durationSeconds": 86400,
    "targetUrl": "http://localhost:8080/api/data/receive"
  }'
```

---

### 策略三：阶梯递增（staircase）

**适用场景**：评估系统在不同负载下的性能拐点，找出系统的极限。

**特点**：
- 从低倍数开始（如1x）
- 每隔一段时间逐步增加倍数（1x → 2x → 3x → ... → 10x）
- 观察系统何时出现性能瓶颈

**配置示例**：
```yaml
loadtest:
  task:
    traffic-strategy: staircase
    base-rate-per-second: 11.57    # 基础速率
    multiplier: 10                 # 最终达到10倍
    duration-seconds: 86400        # 总持续时间
    ramp-up-seconds: 300           # 每次阶梯的预热时间

# 实际效果（假设分10个阶梯，每个阶梯持续2小时）：
# 0-2小时：  1倍  = 11.57 条/秒
# 2-4小时：  2倍  = 23.14 条/秒
# 4-6小时：  3倍  = 34.71 条/秒
# 6-8小时：  4倍  = 46.28 条/秒
# 8-10小时： 5倍  = 57.85 条/秒
# 10-12小时：6倍  = 69.42 条/秒
# 12-14小时：7倍  = 80.99 条/秒
# 14-16小时：8倍  = 92.56 条/秒
# 16-18小时：9倍  = 104.13 条/秒
# 18-20小时：10倍 = 115.70 条/秒
```

**速率曲线**：
```
速率(条/秒)
120 |                                              ┌──── 115.7
    |                                      ┌───────┘
100 |                              ┌───────┘
    |                      ┌───────┘
 80 |              ┌───────┘
    |      ┌───────┘
 60 |      └─── 57.85
    |  ┌───┘
 40 |--┘
    |
 20 |
    |
    | 2h     4h     6h     8h     10h    12h    14h    16h    18h    20h
    |◄1x►◄2x►◄3x►◄4x►◄5x►◄6x►◄7x►◄8x►◄9x►◄10x►
```

**使用示例**：
```bash
# 启动阶梯压测（手动调整速率）
curl -X POST http://localhost:8080/api/loadtest/http/start \
  -H "Content-Type: application/json" \
  -d '{
    "multiplier": 1,
    "taskName": "HTTP压测-阶梯递增",
    "durationSeconds": 72000,
    "targetUrl": "http://localhost:8080/api/data/receive"
  }'

# 然后每隔一段时间手动调整速率
curl -X POST http://localhost:8080/api/loadtest/{taskId}/adjust-rate \
  -H "Content-Type: application/json" \
  -d '{"newRate": 23.14}'  # 2倍

# 2小时后调整为3倍
curl -X POST http://localhost:8080/api/loadtest/{taskId}/adjust-rate \
  -H "Content-Type: application/json" \
  -d '{"newRate": 34.71}'  # 3倍
```

---

## API接口说明

### 1. 启动MQ压测

**接口**：`POST /api/loadtest/mq/start`

**请求体**：
```json
{
  "multiplier": 5,              // 压测倍数（1-10）
  "taskName": "MQ压测-5倍",     // 任务名称
  "durationSeconds": 86400,     // 持续时间（秒），可选
  "queueName": "test.queue"     // 队列名称，可选
}
```

**响应**：
```json
{
  "success": true,
  "message": "MQ压测已启动",
  "task": {
    "taskId": "lt_1714521600000_a1b2c3d4",
    "testType": "MQ",
    "taskName": "MQ压测-5倍",
    "multiplier": 5,
    "targetRate": 57.85,
    "totalMessages": 5000000,
    "status": "RUNNING",
    "startTime": "2026-04-21T00:00:00"
  }
}
```

### 2. 启动HTTP压测

**接口**：`POST /api/loadtest/http/start`

**请求体**：
```json
{
  "multiplier": 3,                          // 压测倍数（1-10）
  "taskName": "HTTP压测-3倍",               // 任务名称
  "durationSeconds": 3600,                  // 持续时间（秒），可选
  "targetUrl": "http://localhost:8080/api/data/receive"  // 目标URL（必填）
}
```

### 3. 停止压测

**接口**：`POST /api/loadtest/{taskId}/stop`

### 4. 暂停压测

**接口**：`POST /api/loadtest/{taskId}/pause`

### 5. 恢复压测

**接口**：`POST /api/loadtest/{taskId}/resume`

### 6. 查询任务状态

**接口**：`GET /api/loadtest/{taskId}/status`

**响应**：
```json
{
  "success": true,
  "task": {
    "taskId": "lt_1714521600000_a1b2c3d4",
    "status": "RUNNING",
    "sentMessages": 125000,
    "totalMessages": 5000000,
    "successMessages": 124950,
    "failedMessages": 50
  }
}
```

### 7. 查询实时指标

**接口**：`GET /api/loadtest/{taskId}/metrics`

**响应**：
```json
{
  "success": true,
  "metrics": {
    "taskId": "lt_1714521600000_a1b2c3d4",
    "currentQPS": 57.85,
    "avgQPS": 57.20,
    "avgRT": 12.5,
    "p95RT": 28.3,
    "p99RT": 45.7,
    "totalSuccess": 124950,
    "totalFailed": 50,
    "successRate": 99.96,
    "elapsedSeconds": 2173,
    "progress": 2.5
  }
}
```

### 8. 获取压测报告

**接口**：`GET /api/loadtest/{taskId}/report`

### 9. 查询所有任务

**接口**：`GET /api/loadtest/tasks`

### 10. 动态调整速率

**接口**：`POST /api/loadtest/{taskId}/adjust-rate`

**请求体**：
```json
{
  "newRate": 50.0  // 新的速率（条/秒）
}
```

---

## 使用示例

### 示例一：基础HTTP压测（1倍）

测试目标系统处理100万条/天的能力：

```bash
curl -X POST http://localhost:8080/api/loadtest/http/start \
  -H "Content-Type: application/json" \
  -d '{
    "multiplier": 1,
    "taskName": "HTTP压测-基准测试",
    "durationSeconds": 86400,
    "targetUrl": "http://localhost:8080/api/data/receive"
  }'
```

### 示例二：MQ峰值压测（5倍）

模拟工作日高峰时段的消息量：

```bash
curl -X POST http://localhost:8080/api/loadtest/mq/start \
  -H "Content-Type: application/json" \
  -d '{
    "multiplier": 5,
    "taskName": "MQ压测-工作日峰值",
    "durationSeconds": 86400,
    "queueName": "business.queue"
  }'
```

### 示例三：监控压测进度

```bash
# 查看任务状态
curl http://localhost:8080/api/loadtest/lt_1714521600000_a1b2c3d4/status

# 查看实时指标
curl http://localhost:8080/api/loadtest/lt_1714521600000_a1b2c3d4/metrics
```

### 示例四：中途停止压测

```bash
curl -X POST http://localhost:8080/api/loadtest/lt_1714521600000_a1b2c3d4/stop
```

### 示例五：动态调整速率

压测过程中发现系统压力大，降低速率：

```bash
curl -X POST http://localhost:8080/api/loadtest/lt_1714521600000_a1b2c3d4/adjust-rate \
  -H "Content-Type: application/json" \
  -d '{"newRate": 30.0}'
```

---

## 监控与报告

### 实时指标说明

| 指标 | 说明 | 单位 |
|-----|------|------|
| currentQPS | 当前每秒发送数 | 条/秒 |
| avgQPS | 平均QPS | 条/秒 |
| avgRT | 平均响应时间 | 毫秒 |
| p95RT | 95%请求的响应时间 | 毫秒 |
| p99RT | 99%请求的响应时间 | 毫秒 |
| successRate | 成功率 | % |
| progress | 任务进度 | % |

### 性能评估参考

| 指标 | 优秀 | 良好 | 一般 | 需优化 |
|-----|------|------|------|--------|
| 成功率 | >99.9% | 99-99.9% | 95-99% | <95% |
| P95延迟 | <50ms | 50-100ms | 100-200ms | >200ms |
| P99延迟 | <100ms | 100-200ms | 200-500ms | >500ms |

---

## 常见问题

### Q1: 如何选择合适的压测倍数？

**建议**：
1. 从1倍（基准）开始，确认系统正常运行
2. 逐步增加到2x、3x、5x，观察性能变化
3. 最终测试10x，找出系统瓶颈

### Q2: 压测时系统响应变慢怎么办？

**解决方案**：
1. 使用`adjust-rate`接口降低速率
2. 使用`pause`接口暂停压测
3. 检查目标系统的CPU、内存、数据库连接等

### Q3: 如何模拟真实的流量波动？

**方案**：使用`peak`（峰值模拟）策略，配置高峰和低谷时段：
```yaml
traffic-strategy: peak
peak-config:
  peak-start-hour: 9
  peak-end-hour: 18
  peak-multiplier: 3.0
  valley-multiplier: 0.3
```

### Q4: MQ连接失败怎么办？

**检查清单**：
1. MQ服务是否启动
2. `broker-url`配置是否正确
3. 网络是否可达（telnet测试端口）
4. 是否需要认证（配置username/password）

### Q5: 如何提高压测吞吐量？

**优化建议**：
1. 增加`batch-size`（批量大小），如从100改为500
2. 增加`concurrent-producers`或`concurrent-threads`
3. 减少目标接口的处理耗时
4. 优化网络连接（如同一机房）

### Q6: 压测数据如何保存？

**当前版本**：数据保存在内存中，服务重启会丢失。  
**后续优化**：可添加数据库持久化，保存历史压测记录。

---

## 技术架构

### 核心组件

```
┌─────────────────────────────────────────────────────┐
│              LoadTestController (REST API)          │
└──────────────────┬──────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────┐
│             LoadTestOrchestrator (编排器)           │
└───┬──────────────────────┬──────────────────────────┘
    │                      │
    ▼                      ▼
┌──────────────┐    ┌──────────────────┐
│ MQStressTest │    │ HTTPStressTest   │
│   Service    │    │    Service       │
└───┬──────────┘    └────┬─────────────┘
    │                    │
    └────────┬───────────┘
             │
             ▼
┌─────────────────────────────────────────────────────┐
│           RateLimiterService (速率控制)             │
│           - 令牌桶算法                              │
│           - 预热机制                                │
│           - 动态调速                                │
└─────────────────────┬───────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────┐
│          MetricsCollectorService (指标采集)         │
│          - QPS统计                                   │
│          - 响应时间统计                              │
│          - P95/P99计算                              │
└─────────────────────────────────────────────────────┘
```

### 速率控制原理

使用**令牌桶算法**：
1. 系统以固定速率生成令牌
2. 每次发送消息需要消耗一个令牌
3. 没有令牌时等待，从而实现精准控流
4. 支持预热：从0逐步提升到目标速率

---

## 后续规划

- [ ] 支持更多MQ类型（RabbitMQ、Kafka、RocketMQ）
- [ ] 添加数据库持久化
- [ ] Web管理界面
- [ ] Prometheus指标导出
- [ ] 压测报告导出（PDF/Excel）
- [ ] 分布式压测（多节点协同）

---

## 技术支持

如有问题，请联系运维团队或查看项目文档。
