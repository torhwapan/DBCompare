package com.example.dbvalidator.loadtest.model;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 压测任务模型
 * 用于描述一个完整的压测任务配置和状态
 */
@Data
public class LoadTestTask {

    /**
     * 任务ID（自动生成）
     */
    private String taskId;

    /**
     * 压测类型：MQ 或 HTTP
     */
    private TestType testType;

    /**
     * 任务名称（可选，用于标识）
     */
    private String taskName;

    /**
     * 压测倍数（1-10）
     * 1倍 = 100万条/天
     */
    private int multiplier;

    /**
     * 目标速率（条/秒）
     * = baseRatePerSecond * multiplier
     */
    private double targetRate;

    /**
     * 持续时间（秒）
     */
    private long durationSeconds;

    /**
     * 批量发送大小
     */
    private int batchSize;

    /**
     * 流量分布策略
     * uniform: 均匀分布
     * peak: 峰值模拟
     * staircase: 阶梯递增
     */
    private String trafficStrategy;

    /**
     * 预热时间（秒）
     */
    private int rampUpSeconds;

    /**
     * 目标总消息数
     */
    private long totalMessages;

    /**
     * 任务状态
     */
    private TaskStatus status;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 实际发送消息数
     */
    private long sentMessages = 0;

    /**
     * 成功消息数
     */
    private long successMessages = 0;

    /**
     * 失败消息数
     */
    private long failedMessages = 0;

    /**
     * MQ专用：队列名称
     */
    private String queueName;

    /**
     * HTTP专用：目标URL
     */
    private String targetUrl;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 备注
     */
    private String remark;

    public enum TestType {
        /**
         * MQ消息压测
         */
        MQ,

        /**
         * HTTP接口压测
         */
        HTTP
    }

    public enum TaskStatus {
        /**
         * 待执行
         */
        PENDING,

        /**
         * 执行中
         */
        RUNNING,

        /**
         * 已暂停
         */
        PAUSED,

        /**
         * 已完成
         */
        COMPLETED,

        /**
         * 已取消
         */
        CANCELLED,

        /**
         * 执行失败
         */
        FAILED
    }

    /**
     * 创建新任务时自动生成ID和时间
     */
    public void initialize() {
        if (this.taskId == null) {
            this.taskId = "lt_" + System.currentTimeMillis() + "_" + 
                         UUID.randomUUID().toString().substring(0, 8);
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = TaskStatus.PENDING;
        }
        // 计算目标总消息数
        if (this.totalMessages == 0) {
            this.totalMessages = (long) (this.targetRate * this.durationSeconds);
        }
    }
}
