-- ============================================
-- 验证记录表结构（用于持久化验证历史）
-- ============================================

-- 创建验证记录表
CREATE TABLE validation_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id VARCHAR(50) NOT NULL COMMENT '批次ID',
    table_name VARCHAR(100) NOT NULL COMMENT '表名',
    oracle_count BIGINT NOT NULL COMMENT 'Oracle记录数',
    postgres_count BIGINT NOT NULL COMMENT 'PostgreSQL记录数',
    is_consistent BOOLEAN NOT NULL COMMENT '是否一致',
    only_in_oracle_count INT NOT NULL DEFAULT 0 COMMENT '仅在Oracle中的记录数',
    only_in_postgres_count INT NOT NULL DEFAULT 0 COMMENT '仅在PostgreSQL中的记录数',
    field_difference_count INT NOT NULL DEFAULT 0 COMMENT '字段差异记录数',
    duration_ms BIGINT NOT NULL COMMENT '对比耗时(毫秒)',
    validation_time DATETIME NOT NULL COMMENT '验证时间',
    report_file_path VARCHAR(500) COMMENT '详细报告文件路径',
    remarks VARCHAR(500) COMMENT '备注',
    INDEX idx_batch_id (batch_id),
    INDEX idx_table_name (table_name),
    INDEX idx_validation_time (validation_time),
    INDEX idx_is_consistent (is_consistent)
) COMMENT '数据验证历史记录表';

-- 创建汇总统计表
CREATE TABLE validation_summary (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    validation_date DATE NOT NULL UNIQUE COMMENT '验证日期',
    total_tables INT NOT NULL COMMENT '验证表总数',
    consistent_tables INT NOT NULL COMMENT '一致表数',
    inconsistent_tables INT NOT NULL COMMENT '不一致表数',
    total_duration_ms BIGINT NOT NULL COMMENT '总耗时(毫秒)',
    total_differences INT NOT NULL COMMENT '总差异记录数',
    created_time DATETIME NOT NULL COMMENT '创建时间',
    INDEX idx_validation_date (validation_date)
) COMMENT '数据验证汇总表';

-- 查询最近7天的验证情况
SELECT 
    validation_date,
    total_tables,
    consistent_tables,
    CONCAT(ROUND(consistent_tables * 100.0 / total_tables, 2), '%') AS consistency_rate
FROM validation_summary
WHERE validation_date >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
ORDER BY validation_date DESC;

-- 查询特定表的验证历史
SELECT 
    validation_time,
    is_consistent,
    oracle_count,
    postgres_count,
    only_in_oracle_count,
    only_in_postgres_count,
    field_difference_count,
    duration_ms
FROM validation_history
WHERE table_name = 'user_info'
ORDER BY validation_time DESC
LIMIT 30;

-- 查询不一致率最高的表（最近30天）
SELECT 
    table_name,
    COUNT(*) AS total_validations,
    SUM(CASE WHEN is_consistent = 0 THEN 1 ELSE 0 END) AS inconsistent_count,
    CONCAT(ROUND(SUM(CASE WHEN is_consistent = 0 THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 2), '%') AS inconsistency_rate
FROM validation_history
WHERE validation_time >= DATE_SUB(NOW(), INTERVAL 30 DAY)
GROUP BY table_name
HAVING inconsistent_count > 0
ORDER BY inconsistency_rate DESC;

-- 查询每日差异趋势
SELECT 
    DATE(validation_time) AS validation_date,
    COUNT(DISTINCT table_name) AS tables_validated,
    SUM(only_in_oracle_count + only_in_postgres_count + field_difference_count) AS total_differences
FROM validation_history
WHERE validation_time >= DATE_SUB(NOW(), INTERVAL 30 DAY)
GROUP BY DATE(validation_time)
ORDER BY validation_date DESC;
