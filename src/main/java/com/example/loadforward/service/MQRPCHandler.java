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

/**
 * MQ RPC服务处理器（对方服务部署）
 * 
 * 这个类应该部署在对方的服务上！
 * 对方只需要实现这个监听器，处理完请求后发送响应到本地队列。
 * 
 * 工作流程：
 * 1. 监听远程队列，接收请求
 * 2. 处理业务逻辑
 * 3. 将响应发送到本地队列（通过requestId匹配）
 * 4. 本地服务通过@RabbitListener接收响应并完成Future
 * 
 * 核心优势：
 * - 支持跨vhost/跨集群通信
 * - 不依赖Direct Reply-To机制
 * - 请求响应通过requestId精准匹配
 */
@Slf4j
@Service
public class MQRPCHandler {

    @Autowired
    private RabbitMQConfig rabbitMQConfig;

    /**
     * 本地RabbitTemplate（用于发送响应到本地队列）
     */
    @Autowired
    @Qualifier("localRabbitTemplate")
    private RabbitTemplate localRabbitTemplate;

    /**
     * 监听远程队列，接收请求并处理
     * 
     * 这个监听器部署在对方的服务上！
     * 对方接收请求后，处理完将响应发送到本地队列。
     * 
     * @param request 请求消息
     */
    @RabbitListener(queues = "${mq.remote.queue:loadtest.remote.queue}")
    public void handleRequest(MQRequest request) {
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("收到RPC请求 - ID: {}, 类型: {}", request.getRequestId(), request.getRequestType());
            
            // 处理业务逻辑
            MQResponse response = processBusinessLogic(request);
            
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("RPC请求处理完成 - ID: {}, 状态: {}, 耗时: {}ms", 
                    request.getRequestId(), 
                    response.getStatusCode(),
                    elapsed);
            
            // 将响应发送回本地队列（对方的本地队列）
            localRabbitTemplate.convertAndSend(
                rabbitMQConfig.getLocalExchange(),
                rabbitMQConfig.getLocalRoutingKey(),
                response
            );
            
            log.info("RPC响应已发送 - ID: {}", request.getRequestId());
            
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("RPC请求处理失败 - ID: {}, 错误: {}, 耗时: {}ms", 
                    request.getRequestId(), e.getMessage(), elapsed, e);
            
            // 发送错误响应
            MQResponse errorResponse = MQResponse.error(
                request.getRequestId(), 
                "处理失败: " + e.getMessage(), 
                500
            );
            
            localRabbitTemplate.convertAndSend(
                rabbitMQConfig.getLocalExchange(),
                rabbitMQConfig.getLocalRoutingKey(),
                errorResponse
            );
        }
    }

    /**
     * 业务逻辑处理（示例）
     * 根据你的实际需求修改这个方法
     */
    private MQResponse processBusinessLogic(MQRequest request) {
        try {
            // 根据请求类型分发处理
            switch (request.getRequestType()) {
                case "DATA_QUERY":
                    return handleDataQuery(request);
                case "DATA_INSERT":
                    return handleDataInsert(request);
                case "DATA_UPDATE":
                    return handleDataUpdate(request);
                case "HEALTH_CHECK":
                    return handleHealthCheck(request);
                default:
                    return MQResponse.error(
                        request.getRequestId(), 
                        "未知请求类型: " + request.getRequestType(), 
                        400
                    );
            }
        } catch (Exception e) {
            return MQResponse.error(
                request.getRequestId(), 
                "业务处理失败: " + e.getMessage(), 
                500
            );
        }
    }

    /**
     * 示例：数据查询处理
     */
    private MQResponse handleDataQuery(MQRequest request) {
        // TODO: 实现你的查询逻辑
        log.info("处理数据查询请求 - ID: {}", request.getRequestId());
        
        // 模拟处理结果
        Object result = new Object() {
            public String message = "查询成功";
            public long timestamp = System.currentTimeMillis();
        };
        
        return MQResponse.success(
            request.getRequestId(), 
            result, 
            100  // 模拟处理耗时
        );
    }

    /**
     * 示例：数据插入处理
     */
    private MQResponse handleDataInsert(MQRequest request) {
        // TODO: 实现你的插入逻辑
        log.info("处理数据插入请求 - ID: {}", request.getRequestId());
        
        return MQResponse.success(
            request.getRequestId(), 
            "插入成功", 
            200
        );
    }

    /**
     * 示例：数据更新处理
     */
    private MQResponse handleDataUpdate(MQRequest request) {
        // TODO: 实现你的更新逻辑
        log.info("处理数据更新请求 - ID: {}", request.getRequestId());
        
        return MQResponse.success(
            request.getRequestId(), 
            "更新成功", 
            150
        );
    }

    /**
     * 示例：健康检查
     */
    private MQResponse handleHealthCheck(MQRequest request) {
        log.info("处理健康检查请求 - ID: {}", request.getRequestId());
        
        Object healthInfo = new Object() {
            public String status = "UP";
            public long timestamp = System.currentTimeMillis();
            public String service = "MQ-RPC-Service";
        };
        
        return MQResponse.success(
            request.getRequestId(), 
            healthInfo, 
            10
        );
    }
}
