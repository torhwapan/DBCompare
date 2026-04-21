package com.example.dbvalidator.loadtest.service;

import com.example.dbvalidator.loadtest.config.LoadTestProperties;
import com.example.dbvalidator.loadtest.model.LoadTestTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * HTTP压测服务
 * 负责向HTTP接口批量发送请求进行压测
 */
@Slf4j
@Service
public class HTTPStressTestService {

    @Autowired
    private LoadTestProperties properties;

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private MetricsCollectorService metricsCollector;

    /**
     * 线程池用于并发发送
     */
    private ExecutorService executorService;

    /**
     * 任务控制
     */
    private final Map<String, AtomicBoolean> taskFlags = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // 初始化线程池
        int poolSize = properties.getHttp().getConcurrentThreads();
        executorService = new ThreadPoolExecutor(
            poolSize,
            poolSize,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(10000),
            new ThreadFactory() {
                private final AtomicLong threadNum = new AtomicLong(0);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "http-sender-" + threadNum.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()  // 队列满时由调用线程执行
        );
        
        log.info("HTTP压测服务初始化完成 - 并发线程数：{}", poolSize);
    }

    /**
     * 启动HTTP压测任务
     * 
     * @param task 压测任务
     */
    public void startTest(LoadTestTask task) {
        String taskId = task.getTaskId();
        AtomicBoolean running = new AtomicBoolean(true);
        taskFlags.put(taskId, running);
        
        // 初始化指标采集
        metricsCollector.initTaskMetrics(taskId);
        
        log.info("启动HTTP压测 - 任务ID：{}，目标URL：{}，目标速率：{} 条/秒", 
                taskId, task.getTargetUrl(), task.getTargetRate());
        
        // 启动速率控制
        rateLimiterService.start(task);
        
        // 提交发送任务到线程池
        executorService.submit(() -> {
            try {
                executeHTTPTest(task, running);
            } catch (Exception e) {
                log.error("HTTP压测执行异常 - 任务ID：{}", taskId, e);
            } finally {
                running.set(false);
                rateLimiterService.stop();
                log.info("HTTP压测完成 - 任务ID：{}", taskId);
            }
        });
    }

    /**
     * 执行HTTP请求发送
     */
    private void executeHTTPTest(LoadTestTask task, AtomicBoolean running) {
        String taskId = task.getTaskId();
        String targetUrl = task.getTargetUrl();
        int batchSize = task.getBatchSize();
        long totalMessages = task.getTotalMessages();
        
        long sentCount = 0;
        long startTime = System.currentTimeMillis();
        long lastReportTime = startTime;
        long lastMessageCount = 0;
        
        while (running.get() && sentCount < totalMessages) {
            // 检查是否暂停
            if (rateLimiterService.isPaused()) {
                sleep(100);
                continue;
            }
            
            // 获取令牌（控制速率）
            if (!rateLimiterService.tryAcquire(1000)) {
                // 未获取到令牌，短暂等待
                sleep(10);
                continue;
            }
            
            // 批量发送请求
            long batchSendCount = sendBatch(targetUrl, batchSize, taskId);
            sentCount += batchSendCount;
            
            // 更新任务进度
            task.setSentMessages(sentCount);
            
            // 定期报告进度（每5秒）
            long now = System.currentTimeMillis();
            if (now - lastReportTime >= 5000) {
                long delta = sentCount - lastMessageCount;
                double qps = (delta * 1000.0) / (now - lastReportTime);
                
                log.info("[{}] HTTP压测进度：{} / {} ({}%)，QPS：{}，当前速率：{} 条/秒",
                        taskId,
                        sentCount,
                        totalMessages,
                        String.format("%.2f", (double) sentCount / totalMessages * 100),
                        String.format("%.2f", qps),
                        String.format("%.2f", rateLimiterService.getCurrentRate()));
                
                // 更新QPS指标
                metricsCollector.updateQPS(taskId, qps);
                
                lastReportTime = now;
                lastMessageCount = sentCount;
            }
        }
        
        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
        log.info("HTTP压测结束 - 任务ID：{}，总发送：{} 条，耗时：{} 秒", taskId, sentCount, elapsed);
    }

    /**
     * 批量发送HTTP请求
     * 
     * @param targetUrl 目标URL
     * @param batchSize 批量大小
     * @param taskId 任务ID
     * @return 实际发送数量
     */
    private long sendBatch(String targetUrl, int batchSize, String taskId) {
        long successCount = 0;
        List<Future<Boolean>> futures = new ArrayList<>();
        
        for (int i = 0; i < batchSize; i++) {
            Future<Boolean> future = executorService.submit(() -> {
                return sendRequest(targetUrl, taskId);
            });
            futures.add(future);
        }
        
        // 等待批量发送完成
        for (Future<Boolean> future : futures) {
            try {
                if (future.get(10, TimeUnit.SECONDS)) {
                    successCount++;
                }
            } catch (Exception e) {
                log.warn("HTTP请求失败 - 任务ID：{}，错误：{}", taskId, e.getMessage());
                metricsCollector.recordFailure(taskId, 10000, e.getMessage());
            }
        }
        
        return successCount;
    }

    /**
     * 发送单个HTTP请求
     * 
     * @param targetUrl 目标URL
     * @param taskId 任务ID
     * @return 是否成功
     */
    private boolean sendRequest(String targetUrl, String taskId) {
        long startTime = System.currentTimeMillis();
        HttpURLConnection connection = null;
        
        try {
            URL url = new URL(targetUrl);
            connection = (HttpURLConnection) url.openConnection();
            
            // 配置连接
            connection.setRequestMethod(properties.getHttp().getMethod());
            connection.setConnectTimeout(properties.getHttp().getConnectTimeout());
            connection.setReadTimeout(properties.getHttp().getReadTimeout());
            connection.setDoOutput(true);
            
            // 设置请求头
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "LoadTest-Simulator/1.0");
            
            // 构建请求体
            String requestBody = generateRequestBody();
            
            // 发送请求
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            // 读取响应
            int responseCode = connection.getResponseCode();
            long responseTime = System.currentTimeMillis() - startTime;
            
            if (responseCode >= 200 && responseCode < 300) {
                metricsCollector.recordSuccess(taskId, responseTime, requestBody.length());
                return true;
            } else {
                String errorMsg = "HTTP " + responseCode;
                metricsCollector.recordFailure(taskId, responseTime, errorMsg);
                return false;
            }
            
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            metricsCollector.recordFailure(taskId, responseTime, e.getMessage());
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * 生成请求体
     */
    private String generateRequestBody() {
        // 可以根据需要生成不同的请求内容
        return "{\"type\":\"loadtest\",\"timestamp\":" + System.currentTimeMillis() + 
               ",\"data\":\"test\"}";
    }

    /**
     * 暂停压测
     */
    public void pauseTest(String taskId) {
        rateLimiterService.pause();
        log.info("HTTP压测已暂停 - 任务ID：{}", taskId);
    }

    /**
     * 恢复压测
     */
    public void resumeTest(String taskId) {
        rateLimiterService.resume();
        log.info("HTTP压测已恢复 - 任务ID：{}", taskId);
    }

    /**
     * 停止压测
     */
    public void stopTest(String taskId) {
        AtomicBoolean running = taskFlags.get(taskId);
        if (running != null) {
            running.set(false);
        }
        rateLimiterService.stop();
        log.info("HTTP压测已停止 - 任务ID：{}", taskId);
    }

    /**
     * 动态调整速率
     */
    public void adjustRate(String taskId, double newRate) {
        rateLimiterService.adjustRate(newRate);
        log.info("HTTP压测速率调整 - 任务ID：{}，新速率：{} 条/秒", taskId, newRate);
    }

    /**
     * 休眠辅助方法
     */
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
