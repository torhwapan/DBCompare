package com.example.dbvalidator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 字段差异详情
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldDifference {
    
    /**
     * 主键值
     */
    private Object primaryKey;
    
    /**
     * Oracle 数据
     */
    private Map<String, Object> oracleData;
    
    /**
     * PostgreSQL 数据
     */
    private Map<String, Object> postgresData;
    
    /**
     * 不一致的字段列表
     */
    private Map<String, FieldValuePair> differentFields;
}
