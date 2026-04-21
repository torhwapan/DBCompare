package com.example.dbvalidator.loadtest.service;

import com.example.dbvalidator.loadtest.config.LoadTestProperties;
import com.example.dbvalidator.loadtest.model.LoadTestTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.jms.Destination;
import javax.jms.Queue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MQ压测服务
 * 负责向MQ批量发送消息进行压测
 */
@Slf4j
@Service
public class MQStressTestService {

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

    /**
     * JMS模板（需要通过配置初始化）
     */
    // @Autowired
    // private JmsTemplate jmsTemplate;

    @PostConstruct
    public void init() {
        // 初始化线程池
        int poolSize = properties.getMq().getConcurrentProducers();
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
                    Thread t = new Thread(r, "mq-sender-" + threadNum.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()  // 队列满时由调用线程执行
        );
        
        log.info("MQ压测服务初始化完成 - 并发生产者数：{}", poolSize);
    }

    /**
     * 启动MQ压测任务
     * 
     * @param task 压测任务
     */
    public void startTest(LoadTestTask task) {
        String taskId = task.getTaskId();
        AtomicBoolean running = new AtomicBoolean(true);
        taskFlags.put(taskId, running);
        
        // 初始化指标采集
        metricsCollector.initTaskMetrics(taskId);
        
        log.info("启动MQ压测 - 任务ID：{}，队列：{}，目标速率：{} 条/秒", 
                taskId, task.getQueueName(), task.getTargetRate());
        
        // 启动速率控制
        rateLimiterService.start(task);
        
        // 提交发送任务到线程池
        executorService.submit(() -> {
            try {
                executeMQTest(task, running);
            } catch (Exception e) {
                log.error("MQ压测执行异常 - 任务ID：{}", taskId, e);
            } finally {
                running.set(false);
                rateLimiterService.stop();
                log.info("MQ压测完成 - 任务ID：{}", taskId);
            }
        });
    }

    /**
     * 执行MQ消息发送
     */
    private void executeMQTest(LoadTestTask task, AtomicBoolean running) {
        String taskId = task.getTaskId();
        String queueName = task.getQueueName() != null ? 
                          task.getQueueName() : properties.getMq().getQueueName();
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
            
            // 批量发送消息
            long batchSendCount = sendBatch(queueName, batchSize, taskId);
            sentCount += batchSendCount;
            
            // 更新任务进度
            task.setSentMessages(sentCount);
            
            // 定期报告进度（每5秒）
            long now = System.currentTimeMillis();
            if (now - lastReportTime >= 5000) {
                long delta = sentCount - lastMessageCount;
                double qps = (delta * 1000.0) / (now - lastReportTime);
                
                log.info("[{}] MQ压测进度：{} / {} ({}%)，QPS：{}，当前速率：{} 条/秒",
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
        log.info("MQ压测结束 - 任务ID：{}，总发送：{} 条，耗时：{} 秒", taskId, sentCount, elapsed);
    }

    /**
     * 批量发送消息
     * 
     * @param queueName 队列名称
     * @param batchSize 批量大小
     * @param taskId 任务ID
     * @return 实际发送数量
     */
    private long sendBatch(String queueName, int batchSize, String taskId) {
        long successCount = 0;
        List<Future<Boolean>> futures = new ArrayList<>();
        
        for (int i = 0; i < batchSize; i++) {
            Future<Boolean> future = executorService.submit(() -> {
                return sendMessage(queueName, taskId);
            });
            futures.add(future);
        }
        
        // 等待批量发送完成
        for (Future<Boolean> future : futures) {
            try {
                if (future.get(5, TimeUnit.SECONDS)) {
                    successCount++;
                }
            } catch (Exception e) {
                log.warn("MQ消息发送失败 - 任务ID：{}，错误：{}", taskId, e.getMessage());
                metricsCollector.recordFailure(taskId, 5000, e.getMessage());
            }
        }
        
        return successCount;
    }

    /**
     * 发送单条消息（需要实际实现MQ连接）
     * 
     * @param queueName 队列名称
     * @param taskId 任务ID
     * @return 是否成功
     */
    private boolean sendMessage(String queueName, String taskId) {
        long startTime = System.currentTimeMillis();
        
        try {
            // TODO: 实际MQ发送逻辑
            // 方案1：使用 Spring JMS
            // jmsTemplate.send(queueName, session -> {
            //     TextMessage message = session.createTextMessage(generateMessage());
            //     message.setJMSDeliveryMode(DeliveryMode.PERSISTENT);
            //     return message;
            // });
            
            // 方案2：使用 ActiveMQ/RabbitMQ/Kafka 客户端
            
            // 模拟发送（实际需要替换为真实的MQ发送代码）
            simulateSend(10);  // 模拟10ms延迟
            
            long responseTime = System.currentTimeMillis() - startTime;
            metricsCollector.recordSuccess(taskId, responseTime, 1024);  // 假设1KB消息
            
            return true;
            
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            metricsCollector.recordFailure(taskId, responseTime, e.getMessage());
            return false;
        }
    }

    /**
     * 生成消息内容
     */
    private String generateMessage() {
        // 可以根据需要生成不同的消息内容
        return "{\"type\":\"test\",\"timestamp\":" + System.currentTimeMillis() + "}";
    }

    /**
     * 暂停压测
     */
    public void pauseTest(String taskId) {
        rateLimiterService.pause();
        log.info("MQ压测已暂停 - 任务ID：{}", taskId);
    }

    /**
     * 恢复压测
     */
    public void resumeTest(String taskId) {
        rateLimiterService.resume();
        log.info("MQ压测已恢复 - 任务ID：{}", taskId);
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
        log.info("MQ压测已停止 - 任务ID：{}", taskId);
    }

    /**
     * 动态调整速率
     */
    public void adjustRate(String taskId, double newRate) {
        rateLimiterService.adjustRate(newRate);
        log.info("MQ压测速率调整 - 任务ID：{}，新速率：{} 条/秒", taskId, newRate);
    }

    /**
     * 模拟发送（用于测试）
     */
    private void simulateSend(int delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
