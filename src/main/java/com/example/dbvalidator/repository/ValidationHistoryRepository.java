package com.example.dbvalidator.repository;

import com.example.dbvalidator.model.ValidationRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 验证记录数据访问层
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ValidationHistoryRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    /**
     * 保存验证记录
     */
    public void save(ValidationRecord record) {
        String sql = "INSERT INTO validation_history " +
                "(batch_id, table_name, oracle_count, postgres_count, is_consistent, " +
                "only_in_oracle_count, only_in_postgres_count, field_difference_count, " +
                "duration_ms, validation_time, report_file_path, remarks) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        jdbcTemplate.update(sql,
                record.getBatchId(),
                record.getTableName(),
                record.getOracleCount(),
                record.getPostgresCount(),
                record.getIsConsistent(),
                record.getOnlyInOracleCount(),
                record.getOnlyInPostgresCount(),
                record.getFieldDifferenceCount(),
                record.getDurationMs(),
                Timestamp.valueOf(record.getValidationTime()),
                record.getReportFilePath(),
                record.getRemarks());
        
        log.info("验证记录已保存: 批次={}, 表={}", record.getBatchId(), record.getTableName());
    }
    
    /**
     * 批量保存验证记录
     */
    public void batchSave(List<ValidationRecord> records) {
        String sql = "INSERT INTO validation_history " +
                "(batch_id, table_name, oracle_count, postgres_count, is_consistent, " +
                "only_in_oracle_count, only_in_postgres_count, field_difference_count, " +
                "duration_ms, validation_time, report_file_path, remarks) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        jdbcTemplate.batchUpdate(sql, records, records.size(),
                (ps, record) -> {
                    ps.setString(1, record.getBatchId());
                    ps.setString(2, record.getTableName());
                    ps.setLong(3, record.getOracleCount());
                    ps.setLong(4, record.getPostgresCount());
                    ps.setBoolean(5, record.getIsConsistent());
                    ps.setInt(6, record.getOnlyInOracleCount());
                    ps.setInt(7, record.getOnlyInPostgresCount());
                    ps.setInt(8, record.getFieldDifferenceCount());
                    ps.setLong(9, record.getDurationMs());
                    ps.setTimestamp(10, Timestamp.valueOf(record.getValidationTime()));
                    ps.setString(11, record.getReportFilePath());
                    ps.setString(12, record.getRemarks());
                });
        
        log.info("批量保存 {} 条验证记录", records.size());
    }
    
    /**
     * 查询指定日期范围的验证记录
     */
    public List<ValidationRecord> findByDateRange(LocalDate startDate, LocalDate endDate) {
        String sql = "SELECT * FROM validation_history " +
                "WHERE DATE(validation_time) BETWEEN ? AND ? " +
                "ORDER BY validation_time DESC";
        
        return jdbcTemplate.query(sql, new ValidationRecordRowMapper(),
                startDate, endDate);
    }
    
    /**
     * 查询指定表的历史记录
     */
    public List<ValidationRecord> findByTableName(String tableName, int limit) {
        String sql = "SELECT * FROM validation_history " +
                "WHERE table_name = ? " +
                "ORDER BY validation_time DESC " +
                "LIMIT ?";
        
        return jdbcTemplate.query(sql, new ValidationRecordRowMapper(),
                tableName, limit);
    }
    
    /**
     * 查询指定批次的所有记录
     */
    public List<ValidationRecord> findByBatchId(String batchId) {
        String sql = "SELECT * FROM validation_history " +
                "WHERE batch_id = ? " +
                "ORDER BY table_name";
        
        return jdbcTemplate.query(sql, new ValidationRecordRowMapper(), batchId);
    }
    
    /**
     * 查询最近N天不一致的记录
     */
    public List<ValidationRecord> findInconsistentRecords(int days) {
        String sql = "SELECT * FROM validation_history " +
                "WHERE is_consistent = false " +
                "AND validation_time >= DATE_SUB(NOW(), INTERVAL ? DAY) " +
                "ORDER BY validation_time DESC";
        
        return jdbcTemplate.query(sql, new ValidationRecordRowMapper(), days);
    }
    
    /**
     * 保存每日汇总
     */
    public void saveDailySummary(LocalDate validationDate,
                                 int totalTables,
                                 int consistentTables,
                                 long totalDuration,
                                 int totalDifferences) {
        String sql = "INSERT INTO validation_summary " +
                "(validation_date, total_tables, consistent_tables, inconsistent_tables, " +
                "total_duration_ms, total_differences, created_time) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "total_tables = ?, consistent_tables = ?, inconsistent_tables = ?, " +
                "total_duration_ms = ?, total_differences = ?";
        
        int inconsistentTables = totalTables - consistentTables;
        LocalDateTime now = LocalDateTime.now();
        
        jdbcTemplate.update(sql,
                validationDate, totalTables, consistentTables, inconsistentTables,
                totalDuration, totalDifferences, now,
                totalTables, consistentTables, inconsistentTables,
                totalDuration, totalDifferences);
        
        log.info("每日汇总已保存: 日期={}", validationDate);
    }
    
    /**
     * RowMapper
     */
    private static class ValidationRecordRowMapper implements RowMapper<ValidationRecord> {
        @Override
        public ValidationRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return ValidationRecord.builder()
                    .id(rs.getLong("id"))
                    .batchId(rs.getString("batch_id"))
                    .tableName(rs.getString("table_name"))
                    .oracleCount(rs.getLong("oracle_count"))
                    .postgresCount(rs.getLong("postgres_count"))
                    .isConsistent(rs.getBoolean("is_consistent"))
                    .onlyInOracleCount(rs.getInt("only_in_oracle_count"))
                    .onlyInPostgresCount(rs.getInt("only_in_postgres_count"))
                    .fieldDifferenceCount(rs.getInt("field_difference_count"))
                    .durationMs(rs.getLong("duration_ms"))
                    .validationTime(rs.getTimestamp("validation_time").toLocalDateTime())
                    .reportFilePath(rs.getString("report_file_path"))
                    .remarks(rs.getString("remarks"))
                    .build();
        }
    }
}
