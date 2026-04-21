package com.example.dbvalidator.loadtest.controller;

import com.example.dbvalidator.loadtest.model.*;
import com.example.dbvalidator.loadtest.service.LoadTestOrchestrator;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 压测REST控制器
 * 提供压测相关的所有API接口
 * 
 * 接口说明：
 * - POST /api/loadtest/mq/start      - 启动MQ压测
 * - POST /api/loadtest/http/start    - 启动HTTP压测
 * - POST /api/loadtest/{taskId}/stop - 停止压测
 * - POST /api/loadtest/{taskId}/pause - 暂停压测
 * - POST /api/loadtest/{taskId}/resume - 恢复压测
 * - GET  /api/loadtest/{taskId}/status - 查询任务状态
 * - GET  /api/loadtest/{taskId}/metrics - 查询实时指标
 * - GET  /api/loadtest/{taskId}/report - 获取压测报告
 * - GET  /api/loadtest/tasks          - 查询所有任务
 * - POST /api/loadtest/{taskId}/adjust-rate - 动态调整速率
 */
@Slf4j
@RestController
@RequestMapping("/api/loadtest")
public class LoadTestController {

    @Autowired
    private LoadTestOrchestrator orchestrator;

    /**
     * 启动MQ压测
     * 
     * 请求示例：
     * {
     *   "multiplier": 5,
     *   "taskName": "MQ压测-5倍",
     *   "durationSeconds": 86400,
     *   "queueName": "test.queue"
     * }
     */
    @PostMapping("/mq/start")
    public ResponseEntity<Map<String, Object>> startMQTest(@RequestBody MQTestRequest request) {
        try {
            log.info("收到MQ压测请求 - 倍数：{}x，任务名称：{}", request.getMultiplier(), request.getTaskName());
            
            LoadTestTask task = orchestrator.startMQTest(
                request.getMultiplier(),
                request.getTaskName(),
                request.getDurationSeconds(),
                request.getQueueName()
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "MQ压测已启动");
            response.put("task", task);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("启动MQ压测失败", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "启动MQ压测失败：" + e.getMessage()
            ));
        }
    }

    /**
     * 启动HTTP压测
     * 
     * 请求示例：
     * {
     *   "multiplier": 3,
     *   "taskName": "HTTP压测-3倍",
     *   "durationSeconds": 3600,
     *   "targetUrl": "http://localhost:8080/api/data/receive"
     * }
     */
    @PostMapping("/http/start")
    public ResponseEntity<Map<String, Object>> startHTTPTest(@RequestBody HTTPTestRequest request) {
        try {
            log.info("收到HTTP压测请求 - 倍数：{}x，任务名称：{}，目标URL：{}", 
                    request.getMultiplier(), request.getTaskName(), request.getTargetUrl());
            
            LoadTestTask task = orchestrator.startHTTPTest(
                request.getMultiplier(),
                request.getTaskName(),
                request.getDurationSeconds(),
                request.getTargetUrl()
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "HTTP压测已启动");
            response.put("task", task);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("启动HTTP压测失败", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "启动HTTP压测失败：" + e.getMessage()
            ));
        }
    }

    /**
     * 停止压测任务
     */
    @PostMapping("/{taskId}/stop")
    public ResponseEntity<Map<String, Object>> stopTest(@PathVariable String taskId) {
        try {
            orchestrator.stopTask(taskId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "压测已停止",
                "taskId", taskId
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "停止压测失败：" + e.getMessage()
            ));
        }
    }

    /**
     * 暂停压测任务
     */
    @PostMapping("/{taskId}/pause")
    public ResponseEntity<Map<String, Object>> pauseTest(@PathVariable String taskId) {
        try {
            orchestrator.pauseTask(taskId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "压测已暂停",
                "taskId", taskId
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "暂停压测失败：" + e.getMessage()
            ));
        }
    }

    /**
     * 恢复压测任务
     */
    @PostMapping("/{taskId}/resume")
    public ResponseEntity<Map<String, Object>> resumeTest(@PathVariable String taskId) {
        try {
            orchestrator.resumeTask(taskId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "压测已恢复",
                "taskId", taskId
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "恢复压测失败：" + e.getMessage()
            ));
        }
    }

    /**
     * 查询任务状态
     */
    @GetMapping("/{taskId}/status")
    public ResponseEntity<Map<String, Object>> getTaskStatus(@PathVariable String taskId) {
        LoadTestTask task = orchestrator.getTaskStatus(taskId);
        if (task == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "任务不存在"
            ));
        }
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "task", task
        ));
    }

    /**
     * 查询实时指标
     */
    @GetMapping("/{taskId}/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics(@PathVariable String taskId) {
        LoadTestMetrics metrics = orchestrator.getMetrics(taskId);
        if (metrics == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "任务不存在或指标不可用"
            ));
        }
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "metrics", metrics
        ));
    }

    /**
     * 获取压测报告
     */
    @GetMapping("/{taskId}/report")
    public ResponseEntity<Map<String, Object>> getReport(@PathVariable String taskId) {
        LoadTestReport report = orchestrator.generateReport(taskId);
        if (report == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "任务不存在"
            ));
        }
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "report", report
        ));
    }

    /**
     * 查询所有任务
     */
    @GetMapping("/tasks")
    public ResponseEntity<Map<String, Object>> getAllTasks() {
        List<LoadTestTask> tasks = orchestrator.getAllTasks();
        return ResponseEntity.ok(Map.of(
            "success", true,
            "tasks", tasks,
            "total", tasks.size()
        ));
    }

    /**
     * 动态调整速率
     * 
     * 请求示例：
     * {
     *   "newRate": 50.0
     * }
     */
    @PostMapping("/{taskId}/adjust-rate")
    public ResponseEntity<Map<String, Object>> adjustRate(
            @PathVariable String taskId,
            @RequestBody AdjustRateRequest request) {
        try {
            orchestrator.adjustTaskRate(taskId, request.getNewRate());
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "速率已调整",
                "newRate", request.getNewRate()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "调整速率失败：" + e.getMessage()
            ));
        }
    }

    // ========== 请求对象 ==========

    @Data
    public static class MQTestRequest {
        /**
         * 压测倍数（1-10）
         */
        private int multiplier;

        /**
         * 任务名称
         */
        private String taskName;

        /**
         * 持续时间（秒），可选，默认86400
         */
        private Long durationSeconds;

        /**
         * 队列名称，可选，默认配置文件中的队列
         */
        private String queueName;
    }

    @Data
    public static class HTTPTestRequest {
        /**
         * 压测倍数（1-10）
         */
        private int multiplier;

        /**
         * 任务名称
         */
        private String taskName;

        /**
         * 持续时间（秒），可选，默认86400
         */
        private Long durationSeconds;

        /**
         * 目标URL（必填）
         */
        private String targetUrl;
    }

    @Data
    public static class AdjustRateRequest {
        /**
         * 新的速率（条/秒）
         */
        private double newRate;
    }
}
