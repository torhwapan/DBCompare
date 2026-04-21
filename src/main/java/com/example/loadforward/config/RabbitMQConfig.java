package com.example.loadforward.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ双集群配置
 * 支持跨集群异步消息收发
 * 
 * 场景说明：
 * - 发送消息：通过remoteRabbitTemplate发送到对方集群
 * - 接收消息：通过localRabbitTemplate监听本地集群接收对方回复
 * - 通过requestId手动关联请求和响应
 */
@Configuration
public class RabbitMQConfig {

    // ==================== 本地MQ配置 ====================
    
    @Value("${mq.local.host:localhost}")
    private String localHost;
    
    @Value("${mq.local.port:5672}")
    private int localPort;
    
    @Value("${mq.local.username:guest}")
    private String localUsername;
    
    @Value("${mq.local.password:guest}")
    private String localPassword;
    
    @Value("${mq.local.virtual-host:/}")
    private String localVirtualHost;
    
    @Value("${mq.local.exchange:loadtest.local.exchange}")
    private String localExchange;
    
    @Value("${mq.local.queue:loadtest.local.queue}")
    private String localQueue;
    
    @Value("${mq.local.routing-key:loadtest.local}")
    private String localRoutingKey;

    // ==================== 远程MQ配置 ====================
    
    @Value("${mq.remote.host:192.168.1.100}")
    private String remoteHost;
    
    @Value("${mq.remote.port:5672}")
    private int remotePort;
    
    @Value("${mq.remote.username:admin}")
    private String remoteUsername;
    
    @Value("${mq.remote.password:admin123}")
    private String remotePassword;
    
    @Value("${mq.remote.virtual-host:/prod}")
    private String remoteVirtualHost;
    
    @Value("${mq.remote.exchange:loadtest.remote.exchange}")
    private String remoteExchange;
    
    @Value("${mq.remote.queue:loadtest.remote.queue}")
    private String remoteQueue;
    
    @Value("${mq.remote.routing-key:loadtest.remote}")
    private String remoteRoutingKey;

    // ==================== 消息转换器 ====================

    /**
     * JSON消息转换器
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // ==================== 本地MQ连接工厂 ====================

    /**
     * 本地MQ连接工厂
     */
    @Bean(name = "localConnectionFactory")
    public ConnectionFactory localConnectionFactory() {
        CachingConnectionFactory factory = new CachingConnectionFactory();
        factory.setHost(localHost);
        factory.setPort(localPort);
        factory.setUsername(localUsername);
        factory.setPassword(localPassword);
        factory.setVirtualHost(localVirtualHost);
        return factory;
    }

    // ==================== 远程MQ连接工厂 ====================

    /**
     * 远程MQ连接工厂
     */
    @Bean(name = "remoteConnectionFactory")
    public ConnectionFactory remoteConnectionFactory() {
        CachingConnectionFactory factory = new CachingConnectionFactory();
        factory.setHost(remoteHost);
        factory.setPort(remotePort);
        factory.setUsername(remoteUsername);
        factory.setPassword(remotePassword);
        factory.setVirtualHost(remoteVirtualHost);
        return factory;
    }

    // ==================== 本地RabbitTemplate ====================

    /**
     * 本地RabbitTemplate（用于接收对方的回复消息）
     * 监听本地集群队列，等待对方发送响应
     */
    @Bean(name = "localRabbitTemplate")
    public RabbitTemplate localRabbitTemplate(
            @Qualifier("localConnectionFactory") ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    // ==================== 远程RabbitTemplate ====================

    /**
     * 远程RabbitTemplate（用于发送请求消息到对方集群）
     * 发送到对方集群的队列，对方消费后回复
     */
    @Bean(name = "remoteRabbitTemplate")
    public RabbitTemplate remoteRabbitTemplate(
            @Qualifier("remoteConnectionFactory") ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    // ==================== 本地MQ声明 ====================

    /**
     * 本地交换机
     */
    @Bean
    public Exchange localExchange() {
        return ExchangeBuilder.directExchange(localExchange).durable(true).build();
    }

    /**
     * 本地队列
     */
    @Bean
    public Queue localQueue() {
        return QueueBuilder.durable(localQueue).build();
    }

    /**
     * 本地绑定
     */
    @Bean
    public Binding localBinding(Queue localQueue, Exchange localExchange) {
        return BindingBuilder.bind(localQueue)
                .to(localExchange)
                .with(localRoutingKey)
                .noargs();
    }

    // ==================== 远程MQ声明 ====================

    /**
     * 远程交换机
     */
    @Bean
    public Exchange remoteExchange() {
        return ExchangeBuilder.directExchange(remoteExchange).durable(true).build();
    }

    /**
     * 远程队列
     */
    @Bean
    public Queue remoteQueue() {
        return QueueBuilder.durable(remoteQueue).build();
    }

    /**
     * 远程绑定
     */
    @Bean
    public Binding remoteBinding(Queue remoteQueue, Exchange remoteExchange) {
        return BindingBuilder.bind(remoteQueue)
                .to(remoteExchange)
                .with(remoteRoutingKey)
                .noargs();
    }

    // ==================== Getter方法 ====================
    
    public String getLocalExchange() {
        return localExchange;
    }
    
    public String getLocalRoutingKey() {
        return localRoutingKey;
    }
    
    public String getRemoteExchange() {
        return remoteExchange;
    }
    
    public String getRemoteRoutingKey() {
        return remoteRoutingKey;
    }
}
