package dev.deepdive.actuator.report;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/reports/generate")
    public ReportResult generate(@RequestParam(defaultValue = "daily-sales") String reportType) {
        return reportService.generate(reportType);
    }
}
