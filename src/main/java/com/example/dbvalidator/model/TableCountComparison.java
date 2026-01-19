package com.example.dbvalidator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 表数据总量对比结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableCountComparison {
    
    /**
     * 表名称
     */
    private String tableName;
    
    /**
     * 开始时间
     */
    private String startTime;
    
    /**
     * 结束时间
     */
    private String endTime;
    
    /**
     * Oracle数量
     */
    private Long oracleCount;
    
    /**
     * PostgreSQL数量
     */
    private Long postgresCount;
    
    /**
     * 比例（以Oracle表为基准）
     */
    private Double ratio;
    
    /**
     * 对比时间
     */
    private String comparisonTime;
}