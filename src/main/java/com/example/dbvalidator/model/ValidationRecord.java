package com.example.dbvalidator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 验证记录实体 - 用于数据库持久化
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationRecord {
    
    /**
     * 记录ID
     */
    private Long id;
    
    /**
     * 批次ID（用于关联同一次验证的多个表）
     */
    private String batchId;
    
    /**
     * 表名
     */
    private String tableName;
    
    /**
     * Oracle 记录数
     */
    private Long oracleCount;
    
    /**
     * PostgreSQL 记录数
     */
    private Long postgresCount;
    
    /**
     * 是否一致
     */
    private Boolean isConsistent;
    
    /**
     * 仅在 Oracle 中存在的记录数
     */
    private Integer onlyInOracleCount;
    
    /**
     * 仅在 PostgreSQL 中存在的记录数
     */
    private Integer onlyInPostgresCount;
    
    /**
     * 字段差异记录数
     */
    private Integer fieldDifferenceCount;
    
    /**
     * 对比耗时（毫秒）
     */
    private Long durationMs;
    
    /**
     * 验证时间
     */
    private LocalDateTime validationTime;
    
    /**
     * 详细报告文件路径（可选）
     */
    private String reportFilePath;
    
    /**
     * 备注
     */
    private String remarks;
}
