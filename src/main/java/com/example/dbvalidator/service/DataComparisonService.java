package com.example.dbvalidator.service;

import com.example.dbvalidator.config.ValidatorProperties;
import com.example.dbvalidator.model.*;
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
    
    /**
     * 需求1：总量对比 - 对比指定时间范围内的表数据总量
     * 
     * @param tableName 表名称
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param timeField 时间字段名（用于不同表的时间字段可能不同的情况）
     * @return 表数据总量对比结果
     */
    public TableCountComparison compareTableCount(String tableName, String startTime, String endTime, String timeField) {
        long oracleCount = getRecordCountWithTimeFilter(oracleJdbcTemplate, tableName, startTime, endTime, timeField);
        long postgresCount = getRecordCountWithTimeFilter(postgresJdbcTemplate, tableName, startTime, endTime, timeField);
        
        Double ratio = oracleCount > 0 ? (double) postgresCount / oracleCount : 0.0;
        
        return TableCountComparison.builder()
                .tableName(tableName)
                .startTime(startTime)
                .endTime(endTime)
                .oracleCount(oracleCount)
                .postgresCount(postgresCount)
                .ratio(ratio)
                .comparisonTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .build();
    }
    
    /**
     * 需求1：批量总量对比 - 对比多个表的数据总量
     * 
     * @param tableNames 表名称数组
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param timeField 时间字段名
     * @return 所有表的总量对比结果数组
     */
    public List<TableCountComparison> compareTableCounts(List<String> tableNames, String startTime, String endTime, String timeField) {
        List<TableCountComparison> results = new ArrayList<>();
        
        for (String tableName : tableNames) {
            log.info("开始总量对比表: {}", tableName);
            TableCountComparison result = compareTableCount(tableName, startTime, endTime, timeField);
            results.add(result);
            log.info("表 {} 总量对比完成, Oracle: {}, PostgreSQL: {}, Ratio: {}", 
                    tableName, result.getOracleCount(), result.getPostgresCount(), result.getRatio());
        }
        
        return results;
    }
    
    /**
     * 需求2：单个表数据对比（带过滤条件）
     * 
     * @param tableName 表名称
     * @param ignoredFields 不对比的字段列表
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param timeField 时间字段名
     * @return 单表数据对比结果
     */
    public TableDataComparison compareSingleTableWithDataFilter(
            String tableName, 
            List<String> ignoredFields, 
            String startTime, 
            String endTime, 
            String timeField) {
        
        String primaryKey = validatorProperties.getPrimaryKey();
        
        // 查询记录总数（带时间过滤）
        long oracleCount = getRecordCountWithTimeFilter(oracleJdbcTemplate, tableName, startTime, endTime, timeField);
        long postgresCount = getRecordCountWithTimeFilter(postgresJdbcTemplate, tableName, startTime, endTime, timeField);
        
        log.info("表 {} (带时间过滤) - Oracle记录数: {}, PostgreSQL记录数: {}", 
                tableName, oracleCount, postgresCount);
        
        // 获取带时间过滤的主键
        Set<Object> oracleKeys = getPrimaryKeysWithTimeFilter(oracleJdbcTemplate, tableName, primaryKey, startTime, endTime, timeField);
        Set<Object> postgresKeys = getPrimaryKeysWithTimeFilter(postgresJdbcTemplate, tableName, primaryKey, startTime, endTime, timeField);
        
        // 找出差异主键
        List<Object> onlyInOracle = oracleKeys.stream()
                .filter(key -> !postgresKeys.contains(key))
                .collect(Collectors.toList());
        
        List<Object> onlyInPostgres = postgresKeys.stream()
                .filter(key -> !oracleKeys.contains(key))
                .collect(Collectors.toList());
        
        // 对比共同存在的记录
        Set<Object> commonKeys = new HashSet<>(oracleKeys);
        commonKeys.retainAll(postgresKeys);
        
        Map<Object, FieldDifference> fieldDifferences = compareRecordsWithDataFilter(
                tableName, primaryKey, commonKeys, ignoredFields, startTime, endTime, timeField);
        
        Double ratio = oracleCount > 0 ? (double) postgresCount / oracleCount : 0.0;
        
        return TableDataComparison.builder()
                .oracleCount(oracleCount)
                .postgresCount(postgresCount)
                .ratio(ratio)
                .onlyInOracle(onlyInOracle)
                .onlyInPostgres(onlyInPostgres)
                .fieldDifferences(fieldDifferences)
                .build();
    }
    
    /**
     * 获取带时间过滤的记录总数
     */
    private long getRecordCountWithTimeFilter(JdbcTemplate jdbcTemplate, String tableName, String startTime, String endTime, String timeField) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) FROM ").append(tableName);
        
        if (timeField != null && !timeField.trim().isEmpty() && startTime != null && !startTime.trim().isEmpty()) {
            sql.append(" WHERE ").append(timeField).append(" >= ?");
            if (endTime != null && !endTime.trim().isEmpty()) {
                sql.append(" AND ").append(timeField).append(" <= ?");
            }
        }
        
        if (timeField != null && !timeField.trim().isEmpty() && startTime != null && !startTime.trim().isEmpty() && endTime != null && !endTime.trim().isEmpty()) {
            return jdbcTemplate.queryForObject(sql.toString(), Long.class, startTime, endTime);
        } else if (timeField != null && !timeField.trim().isEmpty() && startTime != null && !startTime.trim().isEmpty()) {
            return jdbcTemplate.queryForObject(sql.toString(), Long.class, startTime);
        } else {
            // 如果没有时间过滤条件，则查询全部
            return getRecordCount(jdbcTemplate, tableName);
        }
    }
    
    /**
     * 获取带时间过滤的主键
     */
    private Set<Object> getPrimaryKeysWithTimeFilter(JdbcTemplate jdbcTemplate, 
                                                     String tableName, 
                                                     String primaryKey, 
                                                     String startTime, 
                                                     String endTime, 
                                                     String timeField) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(primaryKey).append(" FROM ").append(tableName);
        
        if (timeField != null && !timeField.trim().isEmpty() && startTime != null && !startTime.trim().isEmpty()) {
            sql.append(" WHERE ").append(timeField).append(" >= ?");
            if (endTime != null && !endTime.trim().isEmpty()) {
                sql.append(" AND ").append(timeField).append(" <= ?");
            }
        }
        
        if (timeField != null && !timeField.trim().isEmpty() && startTime != null && !startTime.trim().isEmpty() && endTime != null && !endTime.trim().isEmpty()) {
            List<Object> keys = jdbcTemplate.queryForList(sql.toString(), Object.class, startTime, endTime);
            return new HashSet<>(keys);
        } else if (timeField != null && !timeField.trim().isEmpty() && startTime != null && !startTime.trim().isEmpty()) {
            List<Object> keys = jdbcTemplate.queryForList(sql.toString(), Object.class, startTime);
            return new HashSet<>(keys);
        } else {
            // 如果没有时间过滤条件，则查询全部
            return getPrimaryKeys(jdbcTemplate, tableName, primaryKey);
        }
    }
    
    /**
     * 对比带过滤条件的记录
     */
    private Map<Object, FieldDifference> compareRecordsWithDataFilter(String tableName, 
                                                                 String primaryKey, 
                                                                 Set<Object> commonKeys,
                                                                 List<String> ignoredFields,
                                                                 String startTime,
                                                                 String endTime,
                                                                 String timeField) {
        Map<Object, FieldDifference> differences = new HashMap<>();
        
        // 批量处理
        List<Object> keyList = new ArrayList<>(commonKeys);
        int batchSize = validatorProperties.getBatchSize();
        
        for (int i = 0; i < keyList.size(); i += batchSize) {
            int end = Math.min(i + batchSize, keyList.size());
            List<Object> batch = keyList.subList(i, end);
            
            Map<Object, FieldDifference> batchDiff = compareBatchWithDataFilter(
                    tableName, primaryKey, batch, ignoredFields, startTime, endTime, timeField);
            differences.putAll(batchDiff);
        }
        
        return differences;
    }
    
    /**
     * 批量对比带过滤条件的记录
     */
    private Map<Object, FieldDifference> compareBatchWithDataFilter(String tableName, 
                                                                  String primaryKey, 
                                                                  List<Object> keys,
                                                                  List<String> ignoredFields,
                                                                  String startTime,
                                                                  String endTime,
                                                                  String timeField) {
        Map<Object, FieldDifference> differences = new HashMap<>();
        
        // 构建 IN 查询
        String inClause = keys.stream()
                .map(k -> "?")
                .collect(Collectors.joining(","));
        
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT * FROM ").append(tableName).append(" WHERE ").append(primaryKey).append(" IN (").append(inClause).append(")");
        
        // 添加时间过滤条件
        if (timeField != null && !timeField.trim().isEmpty() && startTime != null && !startTime.trim().isEmpty()) {
            sql.append(" AND ").append(timeField).append(" >= ?");
            if (endTime != null && !endTime.trim().isEmpty()) {
                sql.append(" AND ").append(timeField).append(" <= ?");
            }
        }
        
        List<Object> params = new ArrayList<>(keys);
        if (timeField != null && !timeField.trim().isEmpty() && startTime != null && !startTime.trim().isEmpty()) {
            params.add(startTime);
            if (endTime != null && !endTime.trim().isEmpty()) {
                params.add(endTime);
            }
        }
        
        // 查询 Oracle 数据
        List<Map<String, Object>> oracleData = oracleJdbcTemplate.queryForList(
                sql.toString(), params.toArray());
        Map<Object, Map<String, Object>> oracleMap = oracleData.stream()
                .collect(Collectors.toMap(
                        row -> row.get(primaryKey.toUpperCase()),
                        row -> row
                ));
        
        // 查询 PostgreSQL 数据
        List<Map<String, Object>> postgresData = postgresJdbcTemplate.queryForList(
                sql.toString(), params.toArray());
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
            
            FieldDifference diff = compareRowWithIgnoredFields(key, oracleRow, postgresRow, ignoredFields);
            if (diff != null && !diff.getDifferentFields().isEmpty()) {
                differences.put(key, diff);
            }
        }
        
        return differences;
    }
    
    /**
     * 对比单行数据（带忽略字段）
     */
    private FieldDifference compareRowWithIgnoredFields(Object primaryKey, 
                                                        Map<String, Object> oracleRow, 
                                                        Map<String, Object> postgresRow,
                                                        List<String> ignoredFields) {
        Map<String, FieldValuePair> differentFields = new HashMap<>();
        List<String> configuredIgnoreFields = validatorProperties.getIgnoreFields();
        
        // 合并配置的忽略字段和传入的忽略字段
        Set<String> allIgnoreFields = new HashSet<>();
        if (configuredIgnoreFields != null) {
            allIgnoreFields.addAll(configuredIgnoreFields.stream().map(String::toLowerCase).collect(Collectors.toSet()));
        }
        if (ignoredFields != null) {
            allIgnoreFields.addAll(ignoredFields.stream().map(String::toLowerCase).collect(Collectors.toSet()));
        }
        
        // 获取所有字段名（Oracle 通常是大写，PostgreSQL 通常是小写）
        Set<String> allFields = new HashSet<>();
        oracleRow.keySet().forEach(k -> allFields.add(k.toLowerCase()));
        postgresRow.keySet().forEach(k -> allFields.add(k.toLowerCase()));
        
        for (String field : allFields) {
            // 跳过忽略字段
            if (allIgnoreFields.contains(field.toLowerCase())) {
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
}
