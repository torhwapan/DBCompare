package com.example.dbvalidator.loadtest.service;

import com.example.dbvalidator.loadtest.config.LoadTestProperties;
import com.example.dbvalidator.loadtest.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 压测编排器
 * 负责协调和管理整个压测流程，包括任务管理、报告生成等
 */
@Slf4j
@Service
public class LoadTestOrchestrator {

    @Autowired
    private LoadTestProperties properties;

    @Autowired
    private MQStressTestService mqStressTestService;

    @Autowired
    private HTTPStressTestService httpStressTestService;

    @Autowired
    private MetricsCollectorService metricsCollector;

    @Autowired
    private RateLimiterService rateLimiterService;

    /**
     * 存储所有任务
     */
    private final Map<String, LoadTestTask> taskRegistry = new ConcurrentHashMap<>();

    /**
     * 定时任务调度器
     */
    private ScheduledExecutorService scheduler;

    public LoadTestOrchestrator() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "loadtest-reporter");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 创建压测任务
     * 
     * @param testType 压测类型（MQ/HTTP）
     * @param multiplier 压测倍数（1-10）
     * @param taskName 任务名称
     * @param durationSeconds 持续时间（秒）
     * @param targetUrl 目标URL（HTTP压测时需要）
     * @param queueName 队列名称（MQ压测时需要）
     * @return 创建的任务对象
     */
    public LoadTestTask createTask(LoadTestTask.TestType testType, 
                                   int multiplier, 
                                   String taskName,
                                   Long durationSeconds,
                                   String targetUrl,
                                   String queueName) {
        LoadTestTask task = new LoadTestTask();
        task.setTestType(testType);
        task.setTaskName(taskName);
        task.setMultiplier(multiplier);
        task.setTargetRate(properties.getTask().getBaseRatePerSecond() * multiplier);
        task.setDurationSeconds(durationSeconds != null ? durationSeconds : properties.getTask().getDurationSeconds());
        task.setBatchSize(properties.getTask().getBatchSize());
        task.setTrafficStrategy(properties.getTask().getTrafficStrategy());
        task.setRampUpSeconds(properties.getTask().getRampUpSeconds());
        task.setTotalMessages((long) (task.getTargetRate() * task.getDurationSeconds()));
        task.setTargetUrl(targetUrl);
        task.setQueueName(queueName);
        task.initialize();
        
        // 注册任务
        taskRegistry.put(task.getTaskId(), task);
        
        log.info("创建压测任务 - ID：{}，类型：{}，倍数：{}x，速率：{} 条/秒，总量：{}", 
                task.getTaskId(), testType, multiplier, 
                String.format("%.2f", task.getTargetRate()),
                task.getTotalMessages());
        
        return task;
    }

    /**
     * 启动MQ压测
     */
    public LoadTestTask startMQTest(int multiplier, String taskName, Long durationSeconds, String queueName) {
        LoadTestTask task = createTask(LoadTestTask.TestType.MQ, multiplier, taskName, 
                                       durationSeconds, null, queueName);
        mqStressTestService.startTest(task);
        task.setStatus(LoadTestTask.TaskStatus.RUNNING);
        task.setStartTime(LocalDateTime.now());
        return task;
    }

    /**
     * 启动HTTP压测
     */
    public LoadTestTask startHTTPTest(int multiplier, String taskName, Long durationSeconds, String targetUrl) {
        LoadTestTask task = createTask(LoadTestTask.TestType.HTTP, multiplier, taskName, 
                                       durationSeconds, targetUrl, null);
        httpStressTestService.startTest(task);
        task.setStatus(LoadTestTask.TaskStatus.RUNNING);
        task.setStartTime(LocalDateTime.now());
        return task;
    }

    /**
     * 暂停任务
     */
    public void pauseTask(String taskId) {
        LoadTestTask task = taskRegistry.get(taskId);
        if (task == null) {
            log.warn("任务不存在 - 任务ID：{}", taskId);
            return;
        }
        
        if (task.getTestType() == LoadTestTask.TestType.MQ) {
            mqStressTestService.pauseTest(taskId);
        } else {
            httpStressTestService.pauseTest(taskId);
        }
        
        task.setStatus(LoadTestTask.TaskStatus.PAUSED);
        log.info("任务已暂停 - 任务ID：{}", taskId);
    }

    /**
     * 恢复任务
     */
    public void resumeTask(String taskId) {
        LoadTestTask task = taskRegistry.get(taskId);
        if (task == null) {
            log.warn("任务不存在 - 任务ID：{}", taskId);
            return;
        }
        
        if (task.getTestType() == LoadTestTask.TestType.MQ) {
            mqStressTestService.resumeTest(taskId);
        } else {
            httpStressTestService.resumeTest(taskId);
        }
        
        task.setStatus(LoadTestTask.TaskStatus.RUNNING);
        log.info("任务已恢复 - 任务ID：{}", taskId);
    }

    /**
     * 停止任务
     */
    public void stopTask(String taskId) {
        LoadTestTask task = taskRegistry.get(taskId);
        if (task == null) {
            log.warn("任务不存在 - 任务ID：{}", taskId);
            return;
        }
        
        if (task.getTestType() == LoadTestTask.TestType.MQ) {
            mqStressTestService.stopTest(taskId);
        } else {
            httpStressTestService.stopTest(taskId);
        }
        
        task.setStatus(LoadTestTask.TaskStatus.COMPLETED);
        task.setEndTime(LocalDateTime.now());
        log.info("任务已停止 - 任务ID：{}", taskId);
    }

    /**
     * 调整任务速率
     */
    public void adjustTaskRate(String taskId, double newRate) {
        LoadTestTask task = taskRegistry.get(taskId);
        if (task == null) {
            log.warn("任务不存在 - 任务ID：{}", taskId);
            return;
        }
        
        if (task.getTestType() == LoadTestTask.TestType.MQ) {
            mqStressTestService.adjustRate(taskId, newRate);
        } else {
            httpStressTestService.adjustRate(taskId, newRate);
        }
        
        task.setTargetRate(newRate);
    }

    /**
     * 获取任务状态
     */
    public LoadTestTask getTaskStatus(String taskId) {
        return taskRegistry.get(taskId);
    }

    /**
     * 获取所有任务
     */
    public List<LoadTestTask> getAllTasks() {
        return new ArrayList<>(taskRegistry.values());
    }

    /**
     * 获取实时指标
     */
    public LoadTestMetrics getMetrics(String taskId) {
        LoadTestTask task = taskRegistry.get(taskId);
        if (task == null) {
            return null;
        }
        
        long startTime = task.getStartTime() != null ? 
                        task.getStartTime().toInstant(java.time.ZoneId.systemDefault()).toEpochMilli() 
                        : System.currentTimeMillis();
        
        return metricsCollector.getMetricsSnapshot(taskId, startTime, task.getTotalMessages());
    }

    /**
     * 生成压测报告
     */
    public LoadTestReport generateReport(String taskId) {
        LoadTestTask task = taskRegistry.get(taskId);
        if (task == null) {
            log.warn("任务不存在 - 任务ID：{}", taskId);
            return null;
        }
        
        LoadTestReport report = new LoadTestReport();
        report.setTaskId(task.getTaskId());
        report.setTaskName(task.getTaskName());
        report.setTestType(task.getTestType().name());
        report.setMultiplier(task.getMultiplier());
        report.setTrafficStrategy(task.getTrafficStrategy());
        report.setStartTime(task.getStartTime());
        report.setEndTime(task.getEndTime());
        report.setDurationSeconds(task.getDurationSeconds());
        
        // 汇总统计
        LoadTestReport.SummaryStatistics summary = new LoadTestReport.SummaryStatistics();
        summary.setTotalMessages(task.getTotalMessages());
        summary.setSuccessCount(task.getSuccessMessages());
        summary.setFailCount(task.getFailedMessages());
        
        if (summary.getTotalMessages() > 0) {
            summary.setSuccessRate((double) summary.getSuccessCount() / summary.getTotalMessages() * 100);
        }
        
        report.setSummary(summary);
        
        // 错误详情
        report.setErrors(metricsCollector.getErrors(taskId).stream().map(error -> {
            LoadTestReport.ErrorDetail detail = new LoadTestReport.ErrorDetail();
            detail.setErrorTime((LocalDateTime) error.get("timestamp"));
            detail.setErrorMessage((String) error.get("message"));
            detail.setCount(1);
            return detail;
        }).toList());
        
        log.info("生成压测报告 - 任务ID：{}，成功率：{}%", 
                taskId, String.format("%.2f", summary.getSuccessRate()));
        
        return report;
    }

    /**
     * 清理已完成的任务
     */
    public void cleanupCompletedTasks() {
        taskRegistry.entrySet().removeIf(entry -> {
            LoadTestTask task = entry.getValue();
            if (task.getStatus() == LoadTestTask.TaskStatus.COMPLETED || 
                task.getStatus() == LoadTestTask.TaskStatus.CANCELLED ||
                task.getStatus() == LoadTestTask.TaskStatus.FAILED) {
                metricsCollector.cleanup(entry.getKey());
                log.info("清理完成任务 - ID：{}，状态：{}", entry.getKey(), task.getStatus());
                return true;
            }
            return false;
        });
    }
}
