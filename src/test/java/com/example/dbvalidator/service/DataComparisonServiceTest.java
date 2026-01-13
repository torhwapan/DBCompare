package com.example.dbvalidator.service;

import com.example.dbvalidator.config.ValidatorProperties;
import com.example.dbvalidator.model.ComparisonResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 数据对比服务测试
 */
@SpringBootTest
class DataComparisonServiceTest {
    
    @Mock
    private JdbcTemplate oracleJdbcTemplate;
    
    @Mock
    private JdbcTemplate postgresJdbcTemplate;
    
    @Mock
    private ValidatorProperties validatorProperties;
    
    private DataComparisonService comparisonService;
    
    @BeforeEach
    void setUp() {
        comparisonService = new DataComparisonService(
                oracleJdbcTemplate, 
                postgresJdbcTemplate, 
                validatorProperties
        );
        
        // 设置默认配置
        when(validatorProperties.getPrimaryKey()).thenReturn("id");
        when(validatorProperties.getBatchSize()).thenReturn(1000);
        when(validatorProperties.getIgnoreFields()).thenReturn(Arrays.asList("updated_at"));
        when(validatorProperties.getTables()).thenReturn(Arrays.asList("user_info"));
    }
    
    @Test
    void testCompareTable_WhenDataIsConsistent_ShouldReturnConsistentResult() {
        // 准备测试数据
        String tableName = "user_info";
        
        // Mock 记录总数
        when(oracleJdbcTemplate.queryForObject(anyString(), any(Class.class)))
                .thenReturn(100L);
        when(postgresJdbcTemplate.queryForObject(anyString(), any(Class.class)))
                .thenReturn(100L);
        
        // Mock 主键列表
        List<Object> keys = Arrays.asList(1L, 2L, 3L, 4L, 5L);
        when(oracleJdbcTemplate.queryForList(anyString(), any(Class.class)))
                .thenReturn(keys);
        when(postgresJdbcTemplate.queryForList(anyString(), any(Class.class)))
                .thenReturn(keys);
        
        // Mock 数据行
        List<Map<String, Object>> oracleData = new ArrayList<>();
        List<Map<String, Object>> postgresData = new ArrayList<>();
        
        for (Object key : keys) {
            Map<String, Object> oracleRow = new HashMap<>();
            oracleRow.put("ID", key);
            oracleRow.put("NAME", "User" + key);
            oracleRow.put("EMAIL", "user" + key + "@example.com");
            oracleData.add(oracleRow);
            
            Map<String, Object> postgresRow = new HashMap<>();
            postgresRow.put("id", key);
            postgresRow.put("name", "User" + key);
            postgresRow.put("email", "user" + key + "@example.com");
            postgresData.add(postgresRow);
        }
        
        when(oracleJdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(oracleData);
        when(postgresJdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(postgresData);
        
        // 执行对比
        ComparisonResult result = comparisonService.compareTable(tableName);
        
        // 验证结果
        assertNotNull(result);
        assertEquals(tableName, result.getTableName());
        assertTrue(result.isConsistent());
        assertEquals(0, result.getOnlyInOracle().size());
        assertEquals(0, result.getOnlyInPostgres().size());
        assertEquals(0, result.getFieldDifferences().size());
    }
    
    @Test
    void testCompareTable_WhenRecordCountDifferent_ShouldReturnInconsistentResult() {
        String tableName = "user_info";
        
        // Mock 不同的记录总数
        when(oracleJdbcTemplate.queryForObject(anyString(), any(Class.class)))
                .thenReturn(100L);
        when(postgresJdbcTemplate.queryForObject(anyString(), any(Class.class)))
                .thenReturn(95L);
        
        // Mock 主键列表
        List<Object> oracleKeys = Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L);
        List<Object> postgresKeys = Arrays.asList(1L, 2L, 3L, 4L, 5L);
        
        when(oracleJdbcTemplate.queryForList(anyString(), any(Class.class)))
                .thenReturn(oracleKeys);
        when(postgresJdbcTemplate.queryForList(anyString(), any(Class.class)))
                .thenReturn(postgresKeys);
        
        // 执行对比
        ComparisonResult result = comparisonService.compareTable(tableName);
        
        // 验证结果
        assertNotNull(result);
        assertFalse(result.isConsistent());
        assertEquals(1, result.getOnlyInOracle().size());
        assertTrue(result.getOnlyInOracle().contains(6L));
    }
}
