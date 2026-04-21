package com.example.dbvalidator.loadtest.model;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 压测实时指标
 * 用于记录和查询压测过程中的各项性能指标
 */
@Data
public class LoadTestMetrics {

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 采样时间点
     */
    private LocalDateTime timestamp;

    /**
     * 当前QPS（每秒发送请求数）
     */
    private double currentQPS;

    /**
     * 平均QPS（从任务开始到现在的平均值）
     */
    private double avgQPS;

    /**
     * 当前响应时间（毫秒）
     */
    private double currentRT;

    /**
     * 平均响应时间（毫秒）
     */
    private double avgRT;

    /**
     * P95响应时间（毫秒）- 95%的请求在这个时间内
     */
    private double p95RT;

    /**
     * P99响应时间（毫秒）- 99%的请求在这个时间内
     */
    private double p99RT;

    /**
     * 累计成功消息数
     */
    private long totalSuccess;

    /**
     * 累计失败消息数
     */
    private long totalFailed;

    /**
     * 累计发送消息数
     */
    private long totalSent;

    /**
     * 当前成功率（百分比）
     */
    private double successRate;

    /**
     * 累计发送字节数
     */
    private long totalBytesSent;

    /**
     * 当前线程数
     */
    private int activeThreads;

    /**
     * 队列堆积数（MQ专用）
     */
    private int queueBacklog;

    /**
     * 任务运行时长（秒）
     */
    private long elapsedSeconds;

    /**
     * 任务进度（百分比）
     */
    private double progress;

    /**
     * 计算成功率
     */
    public void calculateSuccessRate() {
        if (totalSent > 0) {
            this.successRate = (double) totalSuccess / totalSent * 100;
        } else {
            this.successRate = 0;
        }
    }

    /**
     * 计算平均QPS
     */
    public void calculateAvgQPS() {
        if (elapsedSeconds > 0) {
            this.avgQPS = (double) totalSent / elapsedSeconds;
        } else {
            this.avgQPS = 0;
        }
    }

    /**
     * 计算进度
     */
    public void calculateProgress(long totalMessages) {
        if (totalMessages > 0) {
            this.progress = (double) totalSent / totalMessages * 100;
        } else {
            this.progress = 0;
        }
    }
}
