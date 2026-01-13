package com.example.dbvalidator.service;

import com.example.dbvalidator.config.ValidatorProperties;
import com.example.dbvalidator.model.ComparisonResult;
import com.example.dbvalidator.model.FieldDifference;
import com.example.dbvalidator.model.FieldValuePair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据对比验证服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataComparisonService {
    
    private final JdbcTemplate oracleJdbcTemplate;
    private final JdbcTemplate postgresJdbcTemplate;
    private final ValidatorProperties validatorProperties;
    
    /**
     * 对比所有配置的表
     */
    public List<ComparisonResult> compareAllTables() {
        List<ComparisonResult> results = new ArrayList<>();
        
        for (String tableName : validatorProperties.getTables()) {
            log.info("开始对比表: {}", tableName);
            ComparisonResult result = compareTable(tableName);
            results.add(result);
            log.info("表 {} 对比完成, 数据一致性: {}", tableName, result.isConsistent());
        }
        
        return results;
    }
    
    /**
     * 对比单个表的数据
     */
    public ComparisonResult compareTable(String tableName) {
        long startTime = System.currentTimeMillis();
        
        String primaryKey = validatorProperties.getPrimaryKey();
        
        // 1. 查询记录总数
        long oracleCount = getRecordCount(oracleJdbcTemplate, tableName);
        long postgresCount = getRecordCount(postgresJdbcTemplate, tableName);
        
        log.info("表 {} - Oracle记录数: {}, PostgreSQL记录数: {}", 
                tableName, oracleCount, postgresCount);
        
        // 2. 获取所有主键
        Set<Object> oracleKeys = getPrimaryKeys(oracleJdbcTemplate, tableName, primaryKey);
        Set<Object> postgresKeys = getPrimaryKeys(postgresJdbcTemplate, tableName, primaryKey);
        
        // 3. 找出差异主键
        List<Object> onlyInOracle = oracleKeys.stream()
                .filter(key -> !postgresKeys.contains(key))
                .collect(Collectors.toList());
        
        List<Object> onlyInPostgres = postgresKeys.stream()
                .filter(key -> !oracleKeys.contains(key))
                .collect(Collectors.toList());
        
        // 4. 对比共同存在的记录
        Set<Object> commonKeys = new HashSet<>(oracleKeys);
        commonKeys.retainAll(postgresKeys);
        
        Map<Object, FieldDifference> fieldDifferences = compareRecords(
                tableName, primaryKey, commonKeys);
        
        // 5. 构建结果
        boolean isConsistent = onlyInOracle.isEmpty() 
                && onlyInPostgres.isEmpty() 
                && fieldDifferences.isEmpty();
        
        long duration = System.currentTimeMillis() - startTime;
        
        return ComparisonResult.builder()
                .tableName(tableName)
                .oracleCount(oracleCount)
                .postgresCount(postgresCount)
                .isConsistent(isConsistent)
                .onlyInOracle(onlyInOracle)
                .onlyInPostgres(onlyInPostgres)
                .fieldDifferences(fieldDifferences)
                .durationMs(duration)
                .comparisonTime(LocalDateTime.now().format(
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .build();
    }
    
    /**
     * 获取表的记录总数
     */
    private long getRecordCount(JdbcTemplate jdbcTemplate, String tableName) {
        String sql = String.format("SELECT COUNT(*) FROM %s", tableName);
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0;
    }
    
    /**
     * 获取表的所有主键
     */
    private Set<Object> getPrimaryKeys(JdbcTemplate jdbcTemplate, 
                                       String tableName, 
                                       String primaryKey) {
        String sql = String.format("SELECT %s FROM %s", primaryKey, tableName);
        List<Object> keys = jdbcTemplate.queryForList(sql, Object.class);
        return new HashSet<>(keys);
    }
    
    /**
     * 对比共同存在的记录
     */
    private Map<Object, FieldDifference> compareRecords(String tableName, 
                                                         String primaryKey, 
                                                         Set<Object> commonKeys) {
        Map<Object, FieldDifference> differences = new HashMap<>();
        
        // 批量处理
        List<Object> keyList = new ArrayList<>(commonKeys);
        int batchSize = validatorProperties.getBatchSize();
        
        for (int i = 0; i < keyList.size(); i += batchSize) {
            int end = Math.min(i + batchSize, keyList.size());
            List<Object> batch = keyList.subList(i, end);
            
            Map<Object, FieldDifference> batchDiff = compareBatch(
                    tableName, primaryKey, batch);
            differences.putAll(batchDiff);
        }
        
        return differences;
    }
    
    /**
     * 批量对比记录
     */
    private Map<Object, FieldDifference> compareBatch(String tableName, 
                                                      String primaryKey, 
                                                      List<Object> keys) {
        Map<Object, FieldDifference> differences = new HashMap<>();
        
        // 构建 IN 查询
        String inClause = keys.stream()
                .map(k -> "?")
                .collect(Collectors.joining(","));
        
        String sql = String.format("SELECT * FROM %s WHERE %s IN (%s)", 
                tableName, primaryKey, inClause);
        
        // 查询 Oracle 数据
        List<Map<String, Object>> oracleData = oracleJdbcTemplate.queryForList(
                sql, keys.toArray());
        Map<Object, Map<String, Object>> oracleMap = oracleData.stream()
                .collect(Collectors.toMap(
                        row -> row.get(primaryKey.toUpperCase()),
                        row -> row
                ));
        
        // 查询 PostgreSQL 数据
        List<Map<String, Object>> postgresData = postgresJdbcTemplate.queryForList(
                sql, keys.toArray());
        Map<Object, Map<String, Object>> postgresMap = postgresData.stream()
                .collect(Collectors.toMap(
                        row -> row.get(primaryKey.toLowerCase()),
                        row -> row
                ));
        
        // 对比每条记录
        for (Object key : keys) {
            Map<String, Object> oracleRow = oracleMap.get(key);
            Map<String, Object> postgresRow = postgresMap.get(key);
            
            if (oracleRow == null || postgresRow == null) {
                continue;
            }
            
            FieldDifference diff = compareRow(key, oracleRow, postgresRow);
            if (diff != null && !diff.getDifferentFields().isEmpty()) {
                differences.put(key, diff);
            }
        }
        
        return differences;
    }
    
    /**
     * 对比单行数据
     */
    private FieldDifference compareRow(Object primaryKey, 
                                       Map<String, Object> oracleRow, 
                                       Map<String, Object> postgresRow) {
        Map<String, FieldValuePair> differentFields = new HashMap<>();
        List<String> ignoreFields = validatorProperties.getIgnoreFields();
        
        // 获取所有字段名（Oracle 通常是大写，PostgreSQL 通常是小写）
        Set<String> allFields = new HashSet<>();
        oracleRow.keySet().forEach(k -> allFields.add(k.toLowerCase()));
        postgresRow.keySet().forEach(k -> allFields.add(k.toLowerCase()));
        
        for (String field : allFields) {
            // 跳过忽略字段
            if (ignoreFields != null && ignoreFields.contains(field.toLowerCase())) {
                continue;
            }
            
            Object oracleValue = oracleRow.get(field.toUpperCase());
            Object postgresValue = postgresRow.get(field.toLowerCase());
            
            // 对比值
            if (!Objects.equals(normalizeValue(oracleValue), normalizeValue(postgresValue))) {
                differentFields.put(field, FieldValuePair.builder()
                        .fieldName(field)
                        .oracleValue(oracleValue)
                        .postgresValue(postgresValue)
                        .build());
            }
        }
        
        if (differentFields.isEmpty()) {
            return null;
        }
        
        return FieldDifference.builder()
                .primaryKey(primaryKey)
                .oracleData(oracleRow)
                .postgresData(postgresRow)
                .differentFields(differentFields)
                .build();
    }
    
    /**
     * 标准化值（处理类型差异）
     */
    private Object normalizeValue(Object value) {
        if (value == null) {
            return null;
        }
        
        // 处理数值类型
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        
        // 处理字符串，去除首尾空格
        if (value instanceof String) {
            return ((String) value).trim();
        }
        
        // 处理日期时间类型
        if (value instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) value).getTime();
        }
        
        if (value instanceof java.sql.Date) {
            return ((java.sql.Date) value).getTime();
        }
        
        return value;
    }
}
