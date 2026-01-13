package com.example.dbvalidator.service;

import com.example.dbvalidator.model.ComparisonResult;
import com.example.dbvalidator.model.FieldDifference;
import com.example.dbvalidator.model.ValidationRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

/**
 * 增强版报告服务 - 支持大数据量和长期报告
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnhancedReportService {
    
    private final ObjectMapper objectMapper;
    private static final String REPORT_BASE_DIR = "reports";
    private static final int MAX_DETAIL_RECORDS = 100; // 每个报告最多显示的详细差异记录数
    
    /**
     * 生成精简报告（只包含摘要信息）
     */
    public String generateCompactReport(List<ComparisonResult> results) {
        StringBuilder report = new StringBuilder();
        
        report.append("=" .repeat(80)).append("\n");
        report.append("数据验证精简报告\n");
        report.append("生成时间: ").append(LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        report.append("=".repeat(80)).append("\n\n");
        
        // 总体统计
        int totalTables = results.size();
        long consistentTables = results.stream().filter(ComparisonResult::isConsistent).count();
        long totalOracleRecords = results.stream().mapToLong(ComparisonResult::getOracleCount).sum();
        long totalPostgresRecords = results.stream().mapToLong(ComparisonResult::getPostgresCount).sum();
        long totalDuration = results.stream().mapToLong(ComparisonResult::getDurationMs).sum();
        
        report.append("【总体统计】\n");
        report.append(String.format("验证表数: %d\n", totalTables));
        report.append(String.format("一致表数: %d (%.2f%%)\n", 
                consistentTables, (consistentTables * 100.0 / totalTables)));
        report.append(String.format("不一致表数: %d\n", totalTables - consistentTables));
        report.append(String.format("Oracle 总记录数: %,d\n", totalOracleRecords));
        report.append(String.format("PostgreSQL 总记录数: %,d\n", totalPostgresRecords));
        report.append(String.format("总耗时: %.2f 秒\n\n", totalDuration / 1000.0));
        
        // 只显示不一致的表
        List<ComparisonResult> inconsistentResults = results.stream()
                .filter(r -> !r.isConsistent())
                .collect(Collectors.toList());
        
        if (inconsistentResults.isEmpty()) {
            report.append("✓ 所有表数据完全一致！\n");
        } else {
            report.append("【不一致表详情】\n\n");
            for (ComparisonResult result : inconsistentResults) {
                appendCompactTableReport(report, result);
            }
        }
        
        return report.toString();
    }
    
    /**
     * 添加精简表报告
     */
    private void appendCompactTableReport(StringBuilder report, ComparisonResult result) {
        report.append(String.format("表: %s\n", result.getTableName()));
        report.append(String.format("  Oracle: %,d 条 | PostgreSQL: %,d 条 | 耗时: %d ms\n",
                result.getOracleCount(), result.getPostgresCount(), result.getDurationMs()));
        
        if (!result.getOnlyInOracle().isEmpty()) {
            report.append(String.format("  ⚠ 仅在 Oracle: %d 条\n", result.getOnlyInOracle().size()));
        }
        if (!result.getOnlyInPostgres().isEmpty()) {
            report.append(String.format("  ⚠ 仅在 PostgreSQL: %d 条\n", result.getOnlyInPostgres().size()));
        }
        if (!result.getFieldDifferences().isEmpty()) {
            report.append(String.format("  ⚠ 字段差异: %d 条\n", result.getFieldDifferences().size()));
        }
        report.append("\n");
    }
    
    /**
     * 生成分段报告（按日期范围）
     */
    public String generateSegmentedReport(List<ComparisonResult> results, 
                                         LocalDate startDate, 
                                         LocalDate endDate) {
        StringBuilder report = new StringBuilder();
        
        report.append("=" .repeat(80)).append("\n");
        report.append(String.format("数据验证报告 [%s ~ %s]\n", startDate, endDate));
        report.append("生成时间: ").append(LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        report.append("=".repeat(80)).append("\n\n");
        
        report.append(generateCompactReport(results));
        
        return report.toString();
    }
    
    /**
     * 生成月度汇总报告
     */
    public String generateMonthlySummary(Map<LocalDate, List<ComparisonResult>> dailyResults) {
        StringBuilder report = new StringBuilder();
        
        report.append("=" .repeat(80)).append("\n");
        report.append("月度数据验证汇总报告\n");
        report.append("生成时间: ").append(LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        report.append("=".repeat(80)).append("\n\n");
        
        // 按日期排序
        List<LocalDate> dates = new ArrayList<>(dailyResults.keySet());
        Collections.sort(dates);
        
        // 统计信息
        Map<String, Integer> tableInconsistentDays = new HashMap<>();
        int totalValidations = 0;
        int totalInconsistentDays = 0;
        
        report.append("【每日验证结果】\n\n");
        for (LocalDate date : dates) {
            List<ComparisonResult> results = dailyResults.get(date);
            long inconsistentTables = results.stream()
                    .filter(r -> !r.isConsistent())
                    .count();
            
            String status = inconsistentTables == 0 ? "✓" : "✗";
            report.append(String.format("%s %s: 验证 %d 个表, 不一致 %d 个\n",
                    status, date, results.size(), inconsistentTables));
            
            if (inconsistentTables > 0) {
                totalInconsistentDays++;
                results.stream()
                        .filter(r -> !r.isConsistent())
                        .forEach(r -> tableInconsistentDays.merge(
                                r.getTableName(), 1, Integer::sum));
            }
            
            totalValidations++;
        }
        
        report.append("\n");
        report.append("【月度汇总】\n");
        report.append(String.format("验证天数: %d 天\n", totalValidations));
        report.append(String.format("完全一致天数: %d 天 (%.2f%%)\n",
                totalValidations - totalInconsistentDays,
                ((totalValidations - totalInconsistentDays) * 100.0 / totalValidations)));
        report.append(String.format("存在差异天数: %d 天\n\n", totalInconsistentDays));
        
        if (!tableInconsistentDays.isEmpty()) {
            report.append("【问题表统计】\n");
            tableInconsistentDays.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(entry -> report.append(String.format("  %s: %d 天存在差异\n",
                            entry.getKey(), entry.getValue())));
        }
        
        return report.toString();
    }
    
    /**
     * 保存详细报告到文件（带压缩）
     */
    public String saveDetailedReport(ComparisonResult result, boolean compress) throws IOException {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String fileName = String.format("%s_%s_detailed", 
                result.getTableName(), dateStr);
        
        // 创建报告目录
        Path reportDir = Paths.get(REPORT_BASE_DIR, dateStr);
        Files.createDirectories(reportDir);
        
        if (compress) {
            fileName += ".json.gz";
            Path filePath = reportDir.resolve(fileName);
            
            try (FileOutputStream fos = new FileOutputStream(filePath.toFile());
                 GZIPOutputStream gzos = new GZIPOutputStream(fos);
                 OutputStreamWriter writer = new OutputStreamWriter(gzos)) {
                
                objectMapper.writeValue(writer, result);
            }
            
            log.info("详细报告已保存（压缩）: {}", filePath);
            return filePath.toString();
        } else {
            fileName += ".json";
            Path filePath = reportDir.resolve(fileName);
            objectMapper.writeValue(filePath.toFile(), result);
            
            log.info("详细报告已保存: {}", filePath);
            return filePath.toString();
        }
    }
    
    /**
     * 生成差异明细 CSV 文件（便于导入 Excel 分析）
     */
    public String generateDifferenceCSV(ComparisonResult result) throws IOException {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String fileName = String.format("%s_%s_differences.csv", 
                result.getTableName(), dateStr);
        
        Path reportDir = Paths.get(REPORT_BASE_DIR, dateStr);
        Files.createDirectories(reportDir);
        Path filePath = reportDir.resolve(fileName);
        
        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            // CSV 表头
            writer.write("主键,字段名,Oracle值,PostgreSQL值,差异类型\n");
            
            // 仅在 Oracle 中的记录
            for (Object key : result.getOnlyInOracle()) {
                writer.write(String.format("%s,全部,存在,不存在,记录缺失\n", key));
            }
            
            // 仅在 PostgreSQL 中的记录
            for (Object key : result.getOnlyInPostgres()) {
                writer.write(String.format("%s,全部,不存在,存在,记录多余\n", key));
            }
            
            // 字段值差异
            for (Map.Entry<Object, FieldDifference> entry : 
                    result.getFieldDifferences().entrySet()) {
                Object primaryKey = entry.getKey();
                FieldDifference diff = entry.getValue();
                
                diff.getDifferentFields().forEach((fieldName, pair) -> {
                    try {
                        writer.write(String.format("%s,%s,%s,%s,字段不一致\n",
                                primaryKey,
                                fieldName,
                                escapeCSV(pair.getOracleValue()),
                                escapeCSV(pair.getPostgresValue())));
                    } catch (IOException e) {
                        log.error("写入 CSV 失败", e);
                    }
                });
            }
        }
        
        log.info("差异明细 CSV 已保存: {}", filePath);
        return filePath.toString();
    }
    
    /**
     * CSV 值转义
     */
    private String escapeCSV(Object value) {
        if (value == null) {
            return "";
        }
        String str = value.toString();
        if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
            return "\"" + str.replace("\"", "\"\"") + "\"";
        }
        return str;
    }
    
    /**
     * 生成验证记录（用于数据库存储）
     */
    public ValidationRecord toValidationRecord(ComparisonResult result, String batchId) {
        return ValidationRecord.builder()
                .batchId(batchId)
                .tableName(result.getTableName())
                .oracleCount(result.getOracleCount())
                .postgresCount(result.getPostgresCount())
                .isConsistent(result.isConsistent())
                .onlyInOracleCount(result.getOnlyInOracle().size())
                .onlyInPostgresCount(result.getOnlyInPostgres().size())
                .fieldDifferenceCount(result.getFieldDifferences().size())
                .durationMs(result.getDurationMs())
                .validationTime(LocalDateTime.now())
                .build();
    }
    
    /**
     * 生成趋势分析报告
     */
    public String generateTrendReport(List<ValidationRecord> historicalRecords) {
        StringBuilder report = new StringBuilder();
        
        report.append("=" .repeat(80)).append("\n");
        report.append("数据一致性趋势分析报告\n");
        report.append("生成时间: ").append(LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        report.append("=".repeat(80)).append("\n\n");
        
        // 按表分组
        Map<String, List<ValidationRecord>> recordsByTable = historicalRecords.stream()
                .collect(Collectors.groupingBy(ValidationRecord::getTableName));
        
        report.append("【各表趋势分析】\n\n");
        
        for (Map.Entry<String, List<ValidationRecord>> entry : recordsByTable.entrySet()) {
            String tableName = entry.getKey();
            List<ValidationRecord> records = entry.getValue();
            
            // 按时间排序
            records.sort(Comparator.comparing(ValidationRecord::getValidationTime));
            
            long consistentCount = records.stream()
                    .filter(ValidationRecord::getIsConsistent)
                    .count();
            
            report.append(String.format("表: %s\n", tableName));
            report.append(String.format("  验证次数: %d\n", records.size()));
            report.append(String.format("  一致次数: %d (%.2f%%)\n", 
                    consistentCount, (consistentCount * 100.0 / records.size())));
            
            // 最近的状态
            ValidationRecord latest = records.get(records.size() - 1);
            report.append(String.format("  最新状态: %s\n", 
                    latest.getIsConsistent() ? "✓ 一致" : "✗ 不一致"));
            
            if (!latest.getIsConsistent()) {
                report.append(String.format("    - 仅在 Oracle: %d 条\n", 
                        latest.getOnlyInOracleCount()));
                report.append(String.format("    - 仅在 PostgreSQL: %d 条\n", 
                        latest.getOnlyInPostgresCount()));
                report.append(String.format("    - 字段差异: %d 条\n", 
                        latest.getFieldDifferenceCount()));
            }
            
            report.append("\n");
        }
        
        return report.toString();
    }
    
    /**
     * 清理旧报告（保留指定天数）
     */
    public void cleanOldReports(int retentionDays) throws IOException {
        Path reportBaseDir = Paths.get(REPORT_BASE_DIR);
        if (!Files.exists(reportBaseDir)) {
            return;
        }
        
        LocalDate cutoffDate = LocalDate.now().minusDays(retentionDays);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        
        Files.list(reportBaseDir)
                .filter(Files::isDirectory)
                .forEach(dir -> {
                    try {
                        String dirName = dir.getFileName().toString();
                        LocalDate dirDate = LocalDate.parse(dirName, formatter);
                        
                        if (dirDate.isBefore(cutoffDate)) {
                            deleteDirectory(dir);
                            log.info("已清理旧报告目录: {}", dir);
                        }
                    } catch (Exception e) {
                        log.warn("清理目录失败: {}", dir, e);
                    }
                });
    }
    
    /**
     * 递归删除目录
     */
    private void deleteDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            Files.walk(directory)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            log.error("删除文件失败: {}", path, e);
                        }
                    });
        }
    }
}
