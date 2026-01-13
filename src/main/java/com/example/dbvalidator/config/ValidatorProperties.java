package com.example.dbvalidator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 验证器配置属性
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "validator")
public class ValidatorProperties {
    
    /**
     * 要验证的表列表
     */
    private List<String> tables;
    
    /**
     * 批量查询大小
     */
    private int batchSize = 1000;
    
    /**
     * 主键字段名
     */
    private String primaryKey = "id";
    
    /**
     * 忽略的字段列表
     */
    private List<String> ignoreFields;
}
