package com.example.loadforward.model;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * MQ响应消息模型
 * 用于RPC模式的请求-响应
 */
@Data
public class MQResponse {

    /**
     * 对应的请求ID（用于匹配）
     */
    private String requestId;

    /**
     * 响应状态码（200成功，其他失败）
     */
    private int statusCode;

    /**
     * 响应数据（JSON格式）
     */
    private Object responseData;

    /**
     * 错误信息（如果失败）
     */
    private String errorMessage;

    /**
     * 响应时间
     */
    private LocalDateTime responseTime;

    /**
     * 处理耗时（毫秒）
     */
    private long processingTime;

    /**
     * 创建成功响应
     */
    public static MQResponse success(String requestId, Object responseData, long processingTime) {
        MQResponse response = new MQResponse();
        response.setRequestId(requestId);
        response.setStatusCode(200);
        response.setResponseData(responseData);
        response.setResponseTime(LocalDateTime.now());
        response.setProcessingTime(processingTime);
        return response;
    }

    /**
     * 创建失败响应
     */
    public static MQResponse error(String requestId, String errorMessage, int statusCode) {
        MQResponse response = new MQResponse();
        response.setRequestId(requestId);
        response.setStatusCode(statusCode);
        response.setErrorMessage(errorMessage);
        response.setResponseTime(LocalDateTime.now());
        response.setProcessingTime(0);
        return response;
    }
}
