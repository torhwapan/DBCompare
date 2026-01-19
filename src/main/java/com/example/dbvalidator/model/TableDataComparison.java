package com.example.dbvalidator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 单表数据对比结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableDataComparison {
    
    /**
     * Oracle数量
     */
    private Long oracleCount;
    
    /**
     * PostgreSQL数量
     */
    private Long postgresCount;
    
    /**
     * 比例
     */
    private Double ratio;
    
    /**
     * 仅在Oracle中存在的记录
     */
    private List<Object> onlyInOracle;
    
    /**
     * 仅在PostgreSQL中存在的记录
     */
    private List<Object> onlyInPostgres;
    
    /**
     * 字段差异
     */
    private Map<Object, FieldDifference> fieldDifferences;
}