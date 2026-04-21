package com.example.loadforward.service;

import com.example.loadforward.config.RabbitMQConfig;
import com.example.loadforward.model.MQRequest;
import com.example.loadforward.model.MQResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * MQ RPC服务（异步Future版）
 * 使用CompletableFuture + ConcurrentHashMap实现跨集群异步消息收发
 * 
 * 核心原理：
 * 1. 发送请求前创建CompletableFuture并存入ConcurrentHashMap
 * 2. 请求消息携带唯一requestId
 * 3. 收到响应后通过requestId查找对应的Future并complete
 * 4. 调用方通过Future.get()异步等待响应
 * 
 * 优势：
 * 1. 真正的异步非阻塞调用
 * 2. 支持跨vhost/跨集群通信（不依赖Direct Reply-To）
 * 3. 手动控制超时和重试策略
 * 4. 请求响应通过requestId精准匹配
 * 
 * 适用场景：
 * - 跨RabbitMQ集群调用
 * - 不同vhost间的消息通信
 * - 需要异步非阻塞的RPC调用
 */
@Slf4j
@Service
public class MQRPCService {

    @Autowired
    private RabbitMQConfig rabbitMQConfig;

    /**
     * 远程RabbitTemplate（发送请求到对方集群）
     */
    @Autowired
    @Qualifier("remoteRabbitTemplate")
    private RabbitTemplate remoteRabbitTemplate;

    /**
     * 本地RabbitTemplate（接收对方的响应消息）
     */
    @Autowired
    @Qualifier("localRabbitTemplate")
    private RabbitTemplate localRabbitTemplate;

    /**
     * 待完成的请求Future映射表
     * Key: requestId
     * Value: CompletableFuture<MQResponse>
     */
    private final ConcurrentHashMap<String, CompletableFuture<MQResponse>> pendingRequests = new ConcurrentHashMap<>();

    /**
     * 默认超时时间（毫秒）
     */
    private static final long DEFAULT_TIMEOUT_MS = 5000;

    @PostConstruct
    public void init() {
        log.info("MQ RPC服务初始化完成（异步Future版）");
        log.info("本地MQ - Exchange: {}, Queue: {}", 
                rabbitMQConfig.getLocalExchange(), 
                rabbitMQConfig.getLocalQueue());
        log.info("远程MQ - Exchange: {}, Queue: {}", 
                rabbitMQConfig.getRemoteExchange(), 
                rabbitMQConfig.getRemoteQueue());
        log.info("待完成请求映射表已就绪");
    }

    /**
     * 异步发送请求并等待响应（非阻塞）
     * 
     * 工作流程：
     * 1. 创建CompletableFuture并存入Map
     * 2. 发送请求消息（携带requestId）
     * 3. 立即返回Future，调用方可以异步等待
     * 4. 收到响应后，通过@RabbitListener完成Future
     * 
     * @param request 请求对象
     * @return CompletableFuture<MQResponse> 异步响应Future
     */
    public CompletableFuture<MQResponse> sendAsync(MQRequest request) {
        return sendAsync(request, DEFAULT_TIMEOUT_MS);
    }

    /**
     * 异步发送请求并等待响应（带超时时间）
     * 
     * @param request 请求对象
     * @param timeoutMs 超时时间（毫秒）
     * @return CompletableFuture<MQResponse> 异步响应Future
     */
    public CompletableFuture<MQResponse> sendAsync(MQRequest request, long timeoutMs) {
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("发送异步RPC请求 - ID: {}, 类型: {}", 
                    request.getRequestId(), request.getRequestType());
            
            // 1. 创建CompletableFuture并存入映射表
            CompletableFuture<MQResponse> future = new CompletableFuture<>();
            pendingRequests.put(request.getRequestId(), future);
            
            // 2. 设置超时任务（防止Future永远不完成）
            ScheduledExecutorScheduler scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.schedule(() -> {
                if (!future.isDone()) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    log.warn("RPC请求超时 - ID: {}, 耗时: {}ms", request.getRequestId(), elapsed);
                    pendingRequests.remove(request.getRequestId());
                    future.completeExceptionally(new TimeoutException("请求超时: " + elapsed + "ms"));
                }
                scheduler.shutdown();
            }, timeoutMs, TimeUnit.MILLISECONDS);
            
            // 3. 发送请求到远程集群
            remoteRabbitTemplate.convertAndSend(
                rabbitMQConfig.getRemoteExchange(),
                rabbitMQConfig.getRemoteRoutingKey(),
                request
            );
            
            log.info("异步RPC请求已发送 - ID: {}", request.getRequestId());
            
            // 4. 返回Future，调用方可以异步等待
            return future;
            
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("发送异步RPC请求失败 - ID: {}, 耗时: {}ms", 
                    request.getRequestId(), elapsed, e);
            pendingRequests.remove(request.getRequestId());
            CompletableFuture<MQResponse> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(e);
            return failedFuture;
        }
    }

    /**
     * 同步发送请求并等待响应（阻塞调用）
     * 
     * 内部调用异步方法，然后通过Future.get()阻塞等待
     * 
     * @param request 请求对象
     * @return 响应对象
     */
    public MQResponse sendAndReceive(MQRequest request) {
        return sendAndReceive(request, DEFAULT_TIMEOUT_MS);
    }

    /**
     * 同步发送请求并等待响应（带超时时间）
     * 
     * @param request 请求对象
     * @param timeoutMs 超时时间（毫秒）
     * @return 响应对象
     */
    public MQResponse sendAndReceive(MQRequest request, long timeoutMs) {
        try {
            log.info("发送同步RPC请求 - ID: {}, 类型: {}", 
                    request.getRequestId(), request.getRequestType());
            
            // 调用异步方法
            CompletableFuture<MQResponse> future = sendAsync(request, timeoutMs);
            
            // 阻塞等待响应
            MQResponse response = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            
            log.info("同步RPC请求完成 - ID: {}, 状态: {}", 
                    request.getRequestId(), response.getStatusCode());
            
            return response;
            
        } catch (TimeoutException e) {
            log.error("RPC请求超时 - ID: {}", request.getRequestId(), e);
            return MQResponse.error(request.getRequestId(), "请求超时", 408);
            
        } catch (Exception e) {
            log.error("RPC请求失败 - ID: {}", request.getRequestId(), e);
            return MQResponse.error(request.getRequestId(), "请求失败: " + e.getMessage(), 500);
        }
    }

    /**
     * 监听本地响应队列，接收对方的回复
     * 
     * 这个方法会自动监听本地MQ队列，当对方发送响应消息时：
     * 1. 提取requestId
     * 2. 从pendingRequests中找到对应的Future
     * 3. 调用future.complete(response)完成异步调用
     * 
     * @param response 响应消息
     */
    @RabbitListener(queues = "${mq.local.queue:loadtest.local.queue}")
    public void handleResponse(MQResponse response) {
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("收到RPC响应 - ID: {}, 状态: {}", 
                    response.getRequestId(), response.getStatusCode());
            
            // 根据requestId查找对应的Future
            CompletableFuture<MQResponse> future = pendingRequests.remove(response.getRequestId());
            
            if (future != null && !future.isDone()) {
                // 完成Future，唤醒等待的调用方
                future.complete(response);
                log.info("RPC响应已匹配 - ID: {}, 耗时: {}ms", 
                        response.getRequestId(), startTime);
            } else {
                log.warn("RPC响应无法匹配 - ID: {}, 可能已超时或被移除", 
                        response.getRequestId());
            }
            
        } catch (Exception e) {
            log.error("处理RPC响应失败 - ID: {}, 耗时: {}ms", 
                    response.getRequestId(), System.currentTimeMillis() - startTime, e);
        }
    }

    /**
     * 获取当前待完成的请求数量（用于监控）
     * 
     * @return 待完成请求数
     */
    public int getPendingRequestCount() {
        return pendingRequests.size();
    }

    /**
     * 清理所有待完成的请求（用于优雅关闭）
     */
    public void clearPendingRequests() {
        int count = pendingRequests.size();
        pendingRequests.forEach((requestId, future) -> {
            if (!future.isDone()) {
                future.completeExceptionally(new RuntimeException("服务关闭，请求被取消"));
            }
        });
        pendingRequests.clear();
        log.info("已清理 {} 个待完成请求", count);
    }
}
