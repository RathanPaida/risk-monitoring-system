package com.rathan.riskmonitor.aws.reports;

import com.rathan.riskmonitor.aws.reports.dto.ReportDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<List<ReportDto>> listReports() {
        List<ReportDto> reports = reportService.listAvailableReports();
        return ResponseEntity.ok(reports);
    }
}
