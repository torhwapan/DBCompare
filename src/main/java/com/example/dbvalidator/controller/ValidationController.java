package com.example.dbvalidator.controller;

import com.example.dbvalidator.model.*;
import com.example.dbvalidator.service.DataComparisonService;
import com.example.dbvalidator.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据验证控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/validation")
@RequiredArgsConstructor
public class ValidationController {
    
    private final DataComparisonService comparisonService;
    private final ReportService reportService;
    
    /**
     * 验证所有表
     */
    @PostMapping("/compare-all")
    public ResponseEntity<Map<String, Object>> compareAllTables() {
        log.info("开始验证所有表...");
        
        List<ComparisonResult> results = comparisonService.compareAllTables();
        reportService.printSummary(results);
        
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("totalTables", results.size());
        response.put("consistentTables", results.stream()
                .filter(ComparisonResult::isConsistent).count());
        response.put("results", results);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 验证单个表
     */
    @PostMapping("/compare-table/{tableName}")
    public ResponseEntity<ComparisonResult> compareTable(
            @PathVariable String tableName) {
        log.info("开始验证表: {}", tableName);
        
        ComparisonResult result = comparisonService.compareTable(tableName);
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 获取文本格式报告
     */
    @GetMapping("/report/text")
    public ResponseEntity<String> getTextReport() {
        log.info("生成文本报告...");
        
        List<ComparisonResult> results = comparisonService.compareAllTables();
        String report = reportService.generateTextReport(results);
        
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(report);
    }
    
    /**
     * 获取 JSON 格式报告
     */
    @GetMapping("/report/json")
    public ResponseEntity<String> getJsonReport() throws IOException {
        log.info("生成 JSON 报告...");
        
        List<ComparisonResult> results = comparisonService.compareAllTables();
        String report = reportService.generateJsonReport(results);
        
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(report);
    }
    
    /**
     * 下载文本报告
     */
    @GetMapping("/report/download/text")
    public ResponseEntity<String> downloadTextReport() {
        log.info("下载文本报告...");
        
        List<ComparisonResult> results = comparisonService.compareAllTables();
        String report = reportService.generateTextReport(results);
        
        String fileName = "validation_report_" + 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + 
                ".txt";
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, 
                        "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(report);
    }
    
    /**
     * 下载 JSON 报告
     */
    @GetMapping("/report/download/json")
    public ResponseEntity<String> downloadJsonReport() throws IOException {
        log.info("下载 JSON 报告...");
        
        List<ComparisonResult> results = comparisonService.compareAllTables();
        String report = reportService.generateJsonReport(results);
        
        String fileName = "validation_report_" + 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + 
                ".json";
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, 
                        "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(report);
    }
    
    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(health);
    }
    
    /**
     * 需求1：总量对比 - 对比指定时间范围内各表的数据总量
     * 
     * @param request 总量对比请求参数
     * @return 各表数据总量对比结果数组
     */
    @PostMapping("/table-count-comparison")
    public ResponseEntity<List<TableCountComparison>> compareTableCounts(@RequestBody TableCountComparisonRequest request) {
        log.info("开始总量对比，表列表: {}, 时间范围: {} 到 {}", 
                request.getTableNames(), request.getStartTime(), request.getEndTime());
        
        List<TableCountComparison> results = comparisonService.compareTableCounts(
                request.getTableNames(), 
                request.getStartTime(), 
                request.getEndTime(), 
                request.getTimeField()
        );
        
        return ResponseEntity.ok(results);
    }
    
    /**
     * 需求2：单个表数据对比（带过滤条件）
     * 
     * @param tableName 表名称
     * @param request 单表数据对比请求参数
     * @return 单表数据对比结果
     */
    @PostMapping("/table-data-comparison/{tableName}")
    public ResponseEntity<TableDataComparison> compareSingleTableWithDataFilter(
            @PathVariable String tableName, 
            @RequestBody SingleTableComparisonRequest request) {
        
        log.info("开始单表数据对比: {}, 时间范围: {} 到 {}, 忽略字段: {}", 
                tableName, request.getStartTime(), request.getEndTime(), request.getIgnoredFields());
        
        TableDataComparison result = comparisonService.compareSingleTableWithDataFilter(
                tableName,
                request.getIgnoredFields(),
                request.getStartTime(),
                request.getEndTime(),
                request.getTimeField()
        );
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 总量对比请求参数模型
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TableCountComparisonRequest {
        private List<String> tableNames;
        private String startTime;
        private String endTime;
        private String timeField; // 时间字段名
    }
    
    /**
     * 单表数据对比请求参数模型
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SingleTableComparisonRequest {
        private List<String> ignoredFields;
        private String startTime;
        private String endTime;
        private String timeField; // 时间字段名
    }
}
