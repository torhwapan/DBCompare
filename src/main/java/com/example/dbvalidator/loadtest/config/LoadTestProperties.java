package com.example.dbvalidator.loadtest.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 压测配置属性
 * 用于配置压测相关的参数，包括MQ、HTTP和任务设置
 */
@Data
@Component
@ConfigurationProperties(prefix = "loadtest")
public class LoadTestProperties {

    /**
     * MQ配置（支持双集群）
     */
    private MQConfig mq = new MQConfig();

    /**
     * HTTP配置
     */
    private HTTPConfig http = new HTTPConfig();

    /**
     * 压测任务默认配置
     */
    private TaskConfig task = new TaskConfig();

    @Data
    public static class MQConfig {
        /**
         * MQ Broker地址
         * 示例：tcp://localhost:61616 (ActiveMQ)
         */
        private String brokerUrl = "tcp://localhost:61616";

        /**
         * 队列名称
         */
        private String queueName = "test.queue";

        /**
         * 并发生产者数量
         */
        private int concurrentProducers = 10;

        /**
         * 批量发送大小
         */
        private int batchSize = 100;

        /**
         * 是否启用持久化
         */
        private boolean persistent = true;

        /**
         * 用户名（如果需要认证）
         */
        private String username;

        /**
         * 密码（如果需要认证）
         */
        private String password;
    }

    @Data
    public static class HTTPConfig {
        /**
         * 目标HTTP服务基础URL
         * 示例：http://localhost:8080
         */
        private String baseUrl = "http://localhost:8080";

        /**
         * 目标接口路径
         * 示例：/api/data/receive
         */
        private String endpoint = "/api/data/receive";

        /**
         * HTTP方法：POST/GET/PUT
         */
        private String method = "POST";

        /**
         * 并发线程数
         */
        private int concurrentThreads = 20;

        /**
         * 连接超时时间（毫秒）
         */
        private int connectTimeout = 5000;

        /**
         * 读取超时时间（毫秒）
         */
        private int readTimeout = 10000;
    }

    @Data
    public static class TaskConfig {
        /**
         * 基础速率（条/秒）
         * 默认11.57条/秒（对应100万条/天）
         */
        private double baseRatePerSecond = 11.57;

        /**
         * 压测倍数（1-10）
         * 1倍 = 100万/天，10倍 = 1000万/天
         */
        private int multiplier = 1;

        /**
         * 持续时间（秒）
         * 默认86400秒（24小时）
         */
        private long durationSeconds = 86400;

        /**
         * 批量发送大小
         */
        private int batchSize = 100;

        /**
         * 预热时间（秒）
         * 从0速率逐步提升到目标速率的时间
         */
        private int rampUpSeconds = 300;

        /**
         * 流量分布策略
         * uniform: 均匀分布
         * peak: 峰值模拟
         * staircase: 阶梯递增
         */
        private String trafficStrategy = "uniform";

        /**
         * 峰值模拟配置（当trafficStrategy=peak时使用）
         */
        private PeakConfig peakConfig = new PeakConfig();
    }

    @Data
    public static class PeakConfig {
        /**
         * 峰值时段开始小时（0-23）
         */
        private int peakStartHour = 9;

        /**
         * 峰值时段结束小时（0-23）
         */
        private int peakEndHour = 18;

        /**
         * 峰值倍数（相对于基础速率）
         * 例如：3.0表示峰值时段速率为平均速率的3倍
         */
        private double peakMultiplier = 3.0;

        /**
         * 谷值倍数（非峰值时段）
         * 例如：0.3表示谷值时段速率为平均速率的0.3倍
         */
        private double valleyMultiplier = 0.3;
    }
}
