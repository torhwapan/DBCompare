package com.example.dbvalidator.controller;

import com.example.dbvalidator.model.ComparisonResult;
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
}
