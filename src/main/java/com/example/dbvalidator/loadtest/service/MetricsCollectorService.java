package com.example.dbvalidator.loadtest.service;

import com.example.dbvalidator.loadtest.model.LoadTestMetrics;
import com.example.dbvalidator.loadtest.model.LoadTestTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 指标采集服务
 * 负责收集和统计压测过程中的各项性能指标
 */
@Slf4j
@Service
public class MetricsCollectorService {

    /**
     * 任务指标存储（每个任务独立统计）
     */
    private final Map<String, TaskMetricsContext> metricsMap = new ConcurrentHashMap<>();

    /**
     * 定时任务调度器（用于定期计算百分位指标）
     */
    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void init() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "metrics-calculator");
            t.setDaemon(true);
            return t;
        });
        
        // 每秒计算一次P95/P99指标
        scheduler.scheduleAtFixedRate(this::calculatePercentiles, 1, 1, TimeUnit.SECONDS);
        
        log.info("指标采集服务初始化完成");
    }

    /**
     * 初始化任务的指标上下文
     */
    public void initTaskMetrics(String taskId) {
        metricsMap.put(taskId, new TaskMetricsContext());
        log.debug("初始化任务指标 - 任务ID：{}", taskId);
    }

    /**
     * 记录成功消息
     */
    public void recordSuccess(String taskId, long responseTime, long bytesSent) {
        TaskMetricsContext context = metricsMap.get(taskId);
        if (context == null) {
            log.warn("任务指标上下文不存在 - 任务ID：{}", taskId);
            return;
        }

        context.successCount.incrementAndGet();
        context.totalSent.incrementAndGet();
        context.totalBytesSent.addAndGet(bytesSent);
        context.totalResponseTime.addAndGet(responseTime);
        context.responseTimes.add(responseTime);
        context.lastSuccessTime = LocalDateTime.now();
    }

    /**
     * 记录失败消息
     */
    public void recordFailure(String taskId, long responseTime, String errorMessage) {
        TaskMetricsContext context = metricsMap.get(taskId);
        if (context == null) {
            log.warn("任务指标上下文不存在 - 任务ID：{}", taskId);
            return;
        }

        context.failedCount.incrementAndGet();
        context.totalSent.incrementAndGet();
        context.totalResponseTime.addAndGet(responseTime);
        context.addError(errorMessage);
        context.lastFailureTime = LocalDateTime.now();
    }

    /**
     * 更新QPS
     */
    public void updateQPS(String taskId, double qps) {
        TaskMetricsContext context = metricsMap.get(taskId);
        if (context == null) {
            return;
        }
        context.currentQPS = qps;
    }

    /**
     * 更新活跃线程数
     */
    public void updateActiveThreads(String taskId, int threads) {
        TaskMetricsContext context = metricsMap.get(taskId);
        if (context == null) {
            return;
        }
        context.activeThreads = threads;
    }

    /**
     * 获取任务当前指标快照
     */
    public LoadTestMetrics getMetricsSnapshot(String taskId, long startTime, long totalMessages) {
        TaskMetricsContext context = metricsMap.get(taskId);
        if (context == null) {
            return null;
        }

        LoadTestMetrics metrics = new LoadTestMetrics();
        metrics.setTaskId(taskId);
        metrics.setTimestamp(LocalDateTime.now());
        
        long totalSent = context.totalSent.get();
        long successCount = context.successCount.get();
        long failedCount = context.failedCount.get();
        long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;

        metrics.setTotalSent(totalSent);
        metrics.setTotalSuccess(successCount);
        metrics.setTotalFailed(failedCount);
        metrics.setCurrentQPS(context.currentQPS);
        metrics.setActiveThreads(context.activeThreads);
        metrics.setTotalBytesSent(context.totalBytesSent.get());
        metrics.setElapsedSeconds(elapsedSeconds);

        // 计算平均值
        metrics.calculateSuccessRate();
        metrics.calculateAvgQPS();
        metrics.calculateProgress(totalMessages);

        if (totalSent > 0) {
            metrics.setAvgRT((double) context.totalResponseTime.get() / totalSent);
        }

        // P95/P99从缓存中获取
        metrics.setP95RT(context.p95RT);
        metrics.setP99RT(context.p99RT);

        return metrics;
    }

    /**
     * 计算百分位指标（P95/P99）
     */
    private void calculatePercentiles() {
        for (Map.Entry<String, TaskMetricsContext> entry : metricsMap.entrySet()) {
            TaskMetricsContext context = entry.getValue();
            List<Long> times = context.responseTimes;
            
            if (times.isEmpty()) {
                continue;
            }

            // 排序计算百分位
            List<Long> sorted = new ArrayList<>(times);
            Collections.sort(sorted);
            
            int size = sorted.size();
            int p95Index = (int) (size * 0.95);
            int p99Index = (int) (size * 0.99);
            
            context.p95RT = sorted.get(Math.min(p95Index, size - 1)).doubleValue();
            context.p99RT = sorted.get(Math.min(p99Index, size - 1)).doubleValue();
            
            // 清空列表，避免内存溢出（保留最近的数据）
            if (size > 10000) {
                context.responseTimes = new ArrayList<>(sorted.subList(size - 5000, size));
            }
        }
    }

    /**
     * 获取错误列表
     */
    public List<Map<String, Object>> getErrors(String taskId) {
        TaskMetricsContext context = metricsMap.get(taskId);
        if (context == null) {
            return Collections.emptyList();
        }
        return context.getRecentErrors(100);
    }

    /**
     * 清理任务指标
     */
    public void cleanup(String taskId) {
        metricsMap.remove(taskId);
        log.info("清理任务指标 - 任务ID：{}", taskId);
    }

    /**
     * 获取所有活跃任务
     */
    public Set<String> getActiveTasks() {
        return metricsMap.keySet();
    }

    /**
     * 任务指标上下文（内部类）
     */
    private static class TaskMetricsContext {
        private final AtomicLong totalSent = new AtomicLong(0);
        private final AtomicLong successCount = new AtomicLong(0);
        private final AtomicLong failedCount = new AtomicLong(0);
        private final AtomicLong totalResponseTime = new AtomicLong(0);
        private final AtomicLong totalBytesSent = new AtomicLong(0);
        
        private volatile List<Long> responseTimes = Collections.synchronizedList(new ArrayList<>());
        private volatile double p95RT = 0;
        private volatile double p99RT = 0;
        private volatile double currentQPS = 0;
        private volatile int activeThreads = 0;
        
        private LocalDateTime lastSuccessTime;
        private LocalDateTime lastFailureTime;
        
        private final List<Map<String, Object>> errors = Collections.synchronizedList(new ArrayList<>());

        public void addError(String errorMessage) {
            Map<String, Object> error = new HashMap<>();
            error.put("timestamp", LocalDateTime.now());
            error.put("message", errorMessage);
            errors.add(error);
            
            // 最多保留1000条错误
            if (errors.size() > 1000) {
                errors.remove(0);
            }
        }

        public List<Map<String, Object>> getRecentErrors(int limit) {
            int size = errors.size();
            if (size <= limit) {
                return new ArrayList<>(errors);
            }
            return new ArrayList<>(errors.subList(size - limit, size));
        }
    }
}
