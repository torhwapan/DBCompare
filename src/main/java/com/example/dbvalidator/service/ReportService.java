package com.example.dbvalidator.service;

import com.example.dbvalidator.model.ComparisonResult;
import com.example.dbvalidator.model.FieldDifference;
import com.example.dbvalidator.model.FieldValuePair;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 报告生成服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {
    
    private final ObjectMapper objectMapper;
    
    /**
     * 生成对比报告（文本格式）
     */
    public String generateTextReport(List<ComparisonResult> results) {
        StringBuilder report = new StringBuilder();
        
        report.append("=" .repeat(80)).append("\n");
        report.append("数据库双写验证报告\n");
        report.append("生成时间: ").append(LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        report.append("=".repeat(80)).append("\n\n");
        
        int totalTables = results.size();
        long consistentTables = results.stream().filter(ComparisonResult::isConsistent).count();
        
        report.append(String.format("总表数: %d\n", totalTables));
        report.append(String.format("一致表数: %d\n", consistentTables));
        report.append(String.format("不一致表数: %d\n\n", totalTables - consistentTables));
        
        for (ComparisonResult result : results) {
            appendTableReport(report, result);
        }
        
        return report.toString();
    }
    
    /**
     * 添加单表报告
     */
    private void appendTableReport(StringBuilder report, ComparisonResult result) {
        report.append("-".repeat(80)).append("\n");
        report.append(String.format("表名: %s\n", result.getTableName()));
        report.append(String.format("一致性: %s\n", result.isConsistent() ? "✓ 一致" : "✗ 不一致"));
        report.append(String.format("对比耗时: %d ms\n", result.getDurationMs()));
        report.append("\n");
        
        report.append(String.format("Oracle 记录数: %d\n", result.getOracleCount()));
        report.append(String.format("PostgreSQL 记录数: %d\n", result.getPostgresCount()));
        report.append("\n");
        
        // 仅在 Oracle 中存在的记录
        if (!result.getOnlyInOracle().isEmpty()) {
            report.append(String.format("仅在 Oracle 中存在的记录数: %d\n", 
                    result.getOnlyInOracle().size()));
            report.append("主键列表: ").append(result.getOnlyInOracle()).append("\n\n");
        }
        
        // 仅在 PostgreSQL 中存在的记录
        if (!result.getOnlyInPostgres().isEmpty()) {
            report.append(String.format("仅在 PostgreSQL 中存在的记录数: %d\n", 
                    result.getOnlyInPostgres().size()));
            report.append("主键列表: ").append(result.getOnlyInPostgres()).append("\n\n");
        }
        
        // 字段值差异
        if (!result.getFieldDifferences().isEmpty()) {
            report.append(String.format("字段值不一致的记录数: %d\n\n", 
                    result.getFieldDifferences().size()));
            
            for (Map.Entry<Object, FieldDifference> entry : 
                    result.getFieldDifferences().entrySet()) {
                appendFieldDifference(report, entry.getValue());
            }
        }
        
        if (result.isConsistent()) {
            report.append("✓ 该表数据完全一致\n");
        }
        
        report.append("\n");
    }
    
    /**
     * 添加字段差异详情
     */
    private void appendFieldDifference(StringBuilder report, FieldDifference diff) {
        report.append(String.format("  主键: %s\n", diff.getPrimaryKey()));
        
        for (Map.Entry<String, FieldValuePair> entry : 
                diff.getDifferentFields().entrySet()) {
            FieldValuePair pair = entry.getValue();
            report.append(String.format("    字段 [%s]:\n", pair.getFieldName()));
            report.append(String.format("      Oracle:     %s\n", pair.getOracleValue()));
            report.append(String.format("      PostgreSQL: %s\n", pair.getPostgresValue()));
        }
        report.append("\n");
    }
    
    /**
     * 生成 JSON 报告
     */
    public String generateJsonReport(List<ComparisonResult> results) throws IOException {
        return objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(results);
    }
    
    /**
     * 保存报告到文件
     */
    public void saveReportToFile(String report, String fileName) throws IOException {
        File file = new File(fileName);
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(report);
        }
        log.info("报告已保存到: {}", file.getAbsolutePath());
    }
    
    /**
     * 打印简要摘要
     */
    public void printSummary(List<ComparisonResult> results) {
        log.info("=".repeat(50));
        log.info("验证摘要:");
        
        for (ComparisonResult result : results) {
            String status = result.isConsistent() ? "✓" : "✗";
            log.info("{} 表 [{}] - Oracle: {} 条, PostgreSQL: {} 条", 
                    status, 
                    result.getTableName(), 
                    result.getOracleCount(), 
                    result.getPostgresCount());
            
            if (!result.isConsistent()) {
                if (!result.getOnlyInOracle().isEmpty()) {
                    log.warn("  - 仅在 Oracle: {} 条", result.getOnlyInOracle().size());
                }
                if (!result.getOnlyInPostgres().isEmpty()) {
                    log.warn("  - 仅在 PostgreSQL: {} 条", result.getOnlyInPostgres().size());
                }
                if (!result.getFieldDifferences().isEmpty()) {
                    log.warn("  - 字段差异: {} 条", result.getFieldDifferences().size());
                }
            }
        }
        
        log.info("=".repeat(50));
    }
}
