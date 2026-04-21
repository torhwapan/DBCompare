package com.example.loadforward.controller;

import com.example.loadforward.model.MQRequest;
import com.example.loadforward.model.MQResponse;
import com.example.loadforward.service.MQRPCService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * MQ RPC控制器
 * 提供HTTP接口调用MQ RPC服务
 * 
 * 接口说明：
 * - POST /api/mq-rpc/send          - 发送RPC请求（同步等待响应）
 * - POST /api/mq-rpc/send-async   - 发送RPC请求（异步Future，立即返回）
 * - POST /api/mq-rpc/send-deferred - 发送RPC请求（Spring异步，超时返回）
 * - GET  /api/mq-rpc/health       - 健康检查
 * - GET  /api/mq-rpc/stats        - 统计信息
 */
@Slf4j
@RestController
@RequestMapping("/api/mq-rpc")
public class MQRPCController {

    @Autowired
    private MQRPCService mqRPCService;

    /**
     * 发送RPC请求并等待响应（同步阻塞）
     * 
     * 请求示例：
     * {
     *   "requestType": "DATA_QUERY",
     *   "requestData": {"table": "user_info", "id": 123},
     *   "timeout": 5000
     * }
     */
    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> sendRPC(@RequestBody SendRequest request) {
        try {
            log.info("收到HTTP同步请求 - 类型: {}", request.getRequestType());
            
            // 构建MQ请求
            MQRequest mqRequest = MQRequest.create(
                request.getRequestType(),
                request.getRequestData()
            );
            
            long timeout = request.getTimeout() != null ? request.getTimeout() : 5000;
            
            // 发送并等待响应（阻塞调用）
            MQResponse response = mqRPCService.sendAndReceive(mqRequest, timeout);
            
            // 构建HTTP响应
            Map<String, Object> result = new HashMap<>();
            result.put("success", response.getStatusCode() == 200);
            result.put("response", response);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("发送RPC请求失败", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "请求失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 发送RPC请求（立即返回requestId，不等待响应）
     * 
     * 适用于：
     * - 批量发送请求
     * - 不需要立即获取响应
     * - 后续通过requestId查询响应
     * 
     * 请求示例：
     * {
     *   "requestType": "DATA_INSERT",
     *   "requestData": {"table": "user_info", "data": {...}}
     * }
     * 
     * 响应示例：
     * {
     *   "success": true,
     *   "message": "请求已发送",
     *   "requestId": "uuid-xxxxx"
     * }
     */
    @PostMapping("/send-async")
    public ResponseEntity<Map<String, Object>> sendAsyncRPC(@RequestBody SendRequest request) {
        try {
            log.info("收到HTTP异步请求 - 类型: {}", request.getRequestType());
            
            // 构建MQ请求
            MQRequest mqRequest = MQRequest.create(
                request.getRequestType(),
                request.getRequestData()
            );
            
            long timeout = request.getTimeout() != null ? request.getTimeout() : 5000;
            
            // 发送请求（返回CompletableFuture）
            CompletableFuture<MQResponse> future = mqRPCService.sendAsync(mqRequest, timeout);
            
            // 立即返回requestId
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "请求已发送");
            result.put("requestId", mqRequest.getRequestId());
            result.put("timeout", timeout);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("发送异步RPC请求失败", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "请求失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 发送RPC请求（Spring异步模式，响应就绪后立即返回）
     * 
     * 优势：
     * - 不占用Servlet线程等待
     * - 响应就绪后立即返回
     * - 支持超时处理
     * 
     * 请求示例：
     * {
     *   "requestType": "HEALTH_CHECK",
     *   "timeout": 3000
     * }
     */
    @PostMapping("/send-deferred")
    public DeferredResult<ResponseEntity<Map<String, Object>>> sendDeferredRPC(@RequestBody SendRequest request) {
        log.info("收到HTTP Deferred请求 - 类型: {}", request.getRequestType());
        
        long timeout = request.getTimeout() != null ? request.getTimeout() : 5000;
        
        // 创建DeferredResult（Spring异步响应）
        DeferredResult<ResponseEntity<Map<String, Object>>> deferredResult = 
            new DeferredResult<>(timeout);
        
        // 构建MQ请求
        MQRequest mqRequest = MQRequest.create(
            request.getRequestType(),
            request.getRequestData()
        );
        
        // 发送异步请求
        CompletableFuture<MQResponse> future = mqRPCService.sendAsync(mqRequest, timeout);
        
        // 当Future完成时，设置DeferredResult的结果
        future.whenComplete((response, error) -> {
            if (error != null) {
                // 发生错误
                log.error("RPC请求失败 - ID: {}", mqRequest.getRequestId(), error);
                deferredResult.setResult(ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "请求失败: " + error.getMessage()
                )));
            } else {
                // 成功获取响应
                Map<String, Object> result = new HashMap<>();
                result.put("success", response.getStatusCode() == 200);
                result.put("response", response);
                
                deferredResult.setResult(ResponseEntity.ok(result));
            }
        });
        
        // 设置超时处理
        deferredResult.onTimeout(() -> {
            log.warn("RPC请求超时 - ID: {}", mqRequest.getRequestId());
            deferredResult.setResult(ResponseEntity.ok(Map.of(
                "success", false,
                "message", "请求超时",
                "requestId", mqRequest.getRequestId()
            )));
        });
        
        return deferredResult;
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "UP");
        result.put("service", "MQ-RPC-Service");
        result.put("pendingRequests", mqRPCService.getPendingRequestCount());
        result.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(result);
    }

    /**
     * 获取统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> result = new HashMap<>();
        result.put("pendingRequests", mqRPCService.getPendingRequestCount());
        result.put("service", "MQ-RPC-Service");
        result.put("mode", "Async-Future");
        result.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(result);
    }

    /**
     * 发送请求对象
     */
    @Data
    public static class SendRequest {
        /**
         * 请求类型（如：DATA_QUERY, DATA_INSERT等）
         */
        private String requestType;

        /**
         * 请求数据（任意JSON对象）
         */
        private Object requestData;

        /**
         * 超时时间（毫秒），可选，默认5000
         */
        private Long timeout;
    }
}
