package com.example.dbvalidator.loadtest.service;

import com.example.dbvalidator.loadtest.config.LoadTestProperties;
import com.example.dbvalidator.loadtest.model.LoadTestTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 速率控制服务 - 核心组件
 * 使用令牌桶算法实现精准的速率控制
 * 支持预热、动态调速和多种流量分布策略
 */
@Slf4j
@Service
public class RateLimiterService {

    @Autowired
    private LoadTestProperties properties;

    /**
     * 令牌桶相关参数
     */
    private volatile double currentRate;           // 当前速率（条/秒）
    private volatile double targetRate;            // 目标速率（条/秒）
    private final AtomicLong tokens = new AtomicLong(0);  // 当前令牌数
    private final AtomicLong lastRefillTime = new AtomicLong(0);  // 上次补充令牌时间

    /**
     * 任务状态控制
     */
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);

    /**
     * 定时任务调度器
     */
    private ScheduledExecutorService scheduler;

    /**
     * 统计数据
     */
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalAllowed = new AtomicLong(0);
    private final AtomicLong totalRejected = new AtomicLong(0);

    @PostConstruct
    public void init() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rate-limiter-refill");
            t.setDaemon(true);
            return t;
        });
        
        // 每100ms补充一次令牌（提高精度）
        scheduler.scheduleAtFixedRate(this::refillTokens, 0, 100, TimeUnit.MILLISECONDS);
        
        log.info("速率控制服务初始化完成，令牌补充间隔：100ms");
    }

    /**
     * 启动速率控制
     * 
     * @param task 压测任务
     */
    public void start(LoadTestTask task) {
        if (running.compareAndSet(false, true)) {
            this.targetRate = task.getTargetRate();
            this.currentRate = 0;
            this.paused.set(false);
            
            // 计算预热期间的速率增长
            if (task.getRampUpSeconds() > 0) {
                startRampUp(task.getRampUpSeconds(), task.getTargetRate());
            } else {
                this.currentRate = task.getTargetRate();
            }
            
            log.info("速率控制启动 - 目标速率：{} 条/秒，预热时间：{} 秒", 
                    task.getTargetRate(), task.getRampUpSeconds());
        }
    }

    /**
     * 预热过程：从0逐步提升到目标速率
     */
    private void startRampUp(int rampUpSeconds, double targetRate) {
        int steps = rampUpSeconds * 10;  // 每100ms一步
        double rateStep = targetRate / steps;
        
        scheduler.scheduleAtFixedRate(() -> {
            if (currentRate < targetRate) {
                currentRate = Math.min(currentRate + rateStep, targetRate);
                log.debug("预热中 - 当前速率：{} 条/秒", String.format("%.2f", currentRate));
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
    }

    /**
     * 补充令牌（每100ms调用一次）
     */
    private void refillTokens() {
        if (!running.get() || paused.get()) {
            return;
        }

        long now = System.currentTimeMillis();
        long lastTime = lastRefillTime.compareAndSet(0, now) ? now : lastRefillTime.get();
        
        // 计算时间间隔（秒）
        double interval = (now - lastTime) / 1000.0;
        
        // 计算新增令牌数（考虑当前速率）
        long newTokens = (long) (currentRate * interval);
        
        if (newTokens > 0) {
            tokens.addAndGet(newTokens);
            lastRefillTime.set(now);
        }
    }

    /**
     * 尝试获取令牌（非阻塞）
     * 
     * @return true-获取成功，可以发送消息；false-获取失败，需要等待
     */
    public boolean tryAcquire() {
        if (!running.get() || paused.get()) {
            return false;
        }

        totalRequests.incrementAndGet();
        
        if (tokens.decrementAndGet() >= 0) {
            totalAllowed.incrementAndGet();
            return true;
        } else {
            // 令牌不足，放回
            tokens.incrementAndGet();
            totalRejected.incrementAndGet();
            return false;
        }
    }

    /**
     * 尝试获取令牌（阻塞式，最多等待指定时间）
     * 
     * @param timeoutMs 最大等待时间（毫秒）
     * @return true-获取成功；false-超时
     */
    public boolean tryAcquire(long timeoutMs) {
        long startTime = System.currentTimeMillis();
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (tryAcquire()) {
                return true;
            }
            
            try {
                Thread.sleep(10);  // 等待10ms后重试
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        
        return false;
    }

    /**
     * 暂停速率控制
     */
    public void pause() {
        paused.set(true);
        log.info("速率控制已暂停");
    }

    /**
     * 恢复速率控制
     */
    public void resume() {
        paused.set(false);
        log.info("速率控制已恢复");
    }

    /**
     * 停止速率控制
     */
    public void stop() {
        running.set(false);
        paused.set(false);
        tokens.set(0);
        log.info("速率控制已停止 - 总请求：{}，允许：{}，拒绝：{}", 
                totalRequests.get(), totalAllowed.get(), totalRejected.get());
    }

    /**
     * 动态调整目标速率
     * 
     * @param newRate 新的速率（条/秒）
     */
    public void adjustRate(double newRate) {
        this.targetRate = newRate;
        this.currentRate = newRate;
        log.info("速率调整 - 新速率：{} 条/秒", String.format("%.2f", newRate));
    }

    /**
     * 获取当前速率
     */
    public double getCurrentRate() {
        return currentRate;
    }

    /**
     * 获取目标速率
     */
    public double getTargetRate() {
        return targetRate;
    }

    /**
     * 获取当前可用令牌数
     */
    public long getAvailableTokens() {
        return tokens.get();
    }

    /**
     * 是否正在运行
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 是否已暂停
     */
    public boolean isPaused() {
        return paused.get();
    }

    /**
     * 获取统计数据
     */
    public RateStatistics getStatistics() {
        RateStatistics stats = new RateStatistics();
        stats.setTotalRequests(totalRequests.get());
        stats.setTotalAllowed(totalAllowed.get());
        stats.setTotalRejected(totalRejected.get());
        stats.setCurrentRate(currentRate);
        stats.setTargetRate(targetRate);
        stats.setRunning(running.get());
        stats.setPaused(paused.get());
        
        if (stats.getTotalRequests() > 0) {
            stats.setPassRate((double) stats.getTotalAllowed() / stats.getTotalRequests() * 100);
        }
        
        return stats;
    }

    /**
     * 速率统计数据
     */
    public static class RateStatistics {
        private long totalRequests;
        private long totalAllowed;
        private long totalRejected;
        private double currentRate;
        private double targetRate;
        private boolean running;
        private boolean paused;
        private double passRate;

        public long getTotalRequests() { return totalRequests; }
        public void setTotalRequests(long totalRequests) { this.totalRequests = totalRequests; }
        
        public long getTotalAllowed() { return totalAllowed; }
        public void setTotalAllowed(long totalAllowed) { this.totalAllowed = totalAllowed; }
        
        public long getTotalRejected() { return totalRejected; }
        public void setTotalRejected(long totalRejected) { this.totalRejected = totalRejected; }
        
        public double getCurrentRate() { return currentRate; }
        public void setCurrentRate(double currentRate) { this.currentRate = currentRate; }
        
        public double getTargetRate() { return targetRate; }
        public void setTargetRate(double targetRate) { this.targetRate = targetRate; }
        
        public boolean isRunning() { return running; }
        public void setRunning(boolean running) { this.running = running; }
        
        public boolean isPaused() { return paused; }
        public void setPaused(boolean paused) { this.paused = paused; }
        
        public double getPassRate() { return passRate; }
        public void setPassRate(double passRate) { this.passRate = passRate; }
    }
}
