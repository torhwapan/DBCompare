package com.example.loadforward.model;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * MQ请求消息模型
 * 用于RPC模式的请求-响应
 */
@Data
public class MQRequest {

    /**
     * 请求ID（用于匹配请求和响应）
     */
    private String requestId;

    /**
     * 请求类型
     */
    private String requestType;

    /**
     * 请求数据（JSON格式）
     */
    private Object requestData;

    /**
     * 发送时间
     */
    private LocalDateTime sendTime;

    /**
     * 超时时间（毫秒）
     */
    private long timeout = 5000;

    /**
     * 创建请求（自动生成ID）
     */
    public static MQRequest create(String requestType, Object requestData) {
        MQRequest request = new MQRequest();
        request.setRequestId(UUID.randomUUID().toString());
        request.setRequestType(requestType);
        request.setRequestData(requestData);
        request.setSendTime(LocalDateTime.now());
        return request;
    }

    /**
     * 创建请求（自定义ID）
     */
    public static MQRequest create(String requestId, String requestType, Object requestData) {
        MQRequest request = new MQRequest();
        request.setRequestId(requestId);
        request.setRequestType(requestType);
        request.setRequestData(requestData);
        request.setSendTime(LocalDateTime.now());
        return request;
    }
}
