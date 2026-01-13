package com.example.dbvalidator.runner;

import com.example.dbvalidator.model.ComparisonResult;
import com.example.dbvalidator.service.DataComparisonService;
import com.example.dbvalidator.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 应用启动时自动执行验证（可选）
 * 如果不需要自动执行，注释掉 @Component 注解即可
 */
@Slf4j
// @Component  // 取消注释以启用自动验证
@RequiredArgsConstructor
public class AutoValidationRunner implements ApplicationRunner {
    
    private final DataComparisonService comparisonService;
    private final ReportService reportService;
    
    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("开始自动数据验证...");
        
        try {
            List<ComparisonResult> results = comparisonService.compareAllTables();
            
            // 打印摘要
            reportService.printSummary(results);
            
            // 生成并保存报告
            String report = reportService.generateTextReport(results);
            reportService.saveReportToFile(report, "validation_report.txt");
            
            String jsonReport = reportService.generateJsonReport(results);
            reportService.saveReportToFile(jsonReport, "validation_report.json");
            
            log.info("自动数据验证完成");
            
        } catch (Exception e) {
            log.error("自动数据验证失败", e);
        }
    }
}
