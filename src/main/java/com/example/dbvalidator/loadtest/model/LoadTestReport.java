package com.example.dbvalidator.loadtest.model;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 压测报告
 * 压测结束后生成的完整报告
 */
@Data
public class LoadTestReport {

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * 压测类型（MQ/HTTP）
     */
    private String testType;

    /**
     * 压测倍数
     */
    private int multiplier;

    /**
     * 流量分布策略
     */
    private String trafficStrategy;

    /**
     * 压测时间范围
     */
    private LocalDateTime startTime;

    /**
     * 压测结束时间
     */
    private LocalDateTime endTime;

    /**
     * 压测持续时长（秒）
     */
    private long durationSeconds;

    /**
     * 汇总统计
     */
    private SummaryStatistics summary;

    /**
     * 性能指标
     */
    private PerformanceMetrics performance;

    /**
     * 时间线数据（用于绘制图表）
     */
    private List<TimelinePoint> timeline;

    /**
     * 错误详情
     */
    private List<ErrorDetail> errors;

    @Data
    public static class SummaryStatistics {
        /**
         * 总消息数
         */
        private long totalMessages;

        /**
         * 成功消息数
         */
        private long successCount;

        /**
         * 失败消息数
         */
        private long failCount;

        /**
         * 成功率（百分比）
         */
        private double successRate;

        /**
         * 平均QPS
         */
        private double avgQPS;

        /**
         * 峰值QPS
         */
        private double peakQPS;

        /**
         * 平均响应时间（毫秒）
         */
        private double avgRT;

        /**
         * P95响应时间（毫秒）
         */
        private double p95RT;

        /**
         * P99响应时间（毫秒）
         */
        private double p99RT;

        /**
         * 总发送字节数
         */
        private long totalBytes;
    }

    @Data
    public static class PerformanceMetrics {
        /**
         * CPU使用率
         */
        private String cpuUsage;

        /**
         * 内存使用情况
         */
        private String memoryUsage;

        /**
         * MQ队列堆积数
         */
        private Long mqQueueSize;

        /**
         * 网络IO
         */
        private String networkIO;
    }

    @Data
    public static class TimelinePoint {
        /**
         * 时间点（格式：HH:mm）
         */
        private String time;

        /**
         * QPS
         */
        private double qps;

        /**
         * 成功率（百分比）
         */
        private double successRate;

        /**
         * 平均响应时间（毫秒）
         */
        private double avgRT;

        /**
         * 已发送消息数
         */
        private long sentMessages;
    }

    @Data
    public static class ErrorDetail {
        /**
         * 错误时间
         */
        private LocalDateTime errorTime;

        /**
         * 错误类型
         */
        private String errorType;

        /**
         * 错误信息
         */
        private String errorMessage;

        /**
         * 发生次数
         */
        private int count;
    }
}
