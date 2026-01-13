package com.example.dbvalidator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 数据对比结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComparisonResult {
    
    /**
     * 表名
     */
    private String tableName;
    
    /**
     * Oracle 记录总数
     */
    private long oracleCount;
    
    /**
     * PostgreSQL 记录总数
     */
    private long postgresCount;
    
    /**
     * 数据是否一致
     */
    private boolean isConsistent;
    
    /**
     * 仅在 Oracle 中存在的主键列表
     */
    private List<Object> onlyInOracle;
    
    /**
     * 仅在 PostgreSQL 中存在的主键列表
     */
    private List<Object> onlyInPostgres;
    
    /**
     * 字段值不一致的记录
     * Key: 主键值
     * Value: 差异详情
     */
    private Map<Object, FieldDifference> fieldDifferences;
    
    /**
     * 对比耗时（毫秒）
     */
    private long durationMs;
    
    /**
     * 对比时间
     */
    private String comparisonTime;
}
