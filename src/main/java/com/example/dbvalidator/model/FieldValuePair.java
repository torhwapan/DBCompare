package com.example.dbvalidator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字段值对
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldValuePair {
    
    /**
     * 字段名
     */
    private String fieldName;
    
    /**
     * Oracle 值
     */
    private Object oracleValue;
    
    /**
     * PostgreSQL 值
     */
    private Object postgresValue;
}
