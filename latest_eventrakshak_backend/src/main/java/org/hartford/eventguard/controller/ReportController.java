package org.hartford.eventguard.controller;

import org.hartford.eventguard.service.ReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/policy/{subscriptionId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'UNDERWRITER', 'ADMIN')")
    public ResponseEntity<byte[]> getPolicyReport(@PathVariable Long subscriptionId) throws IOException {
        byte[] pdfContent = reportService.generatePolicyReport(subscriptionId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "Policy_Report_" + subscriptionId + ".pdf");

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfContent);
    }

    @GetMapping("/claim/{claimId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'CLAIMS_OFFICER', 'ADMIN')")
    public ResponseEntity<byte[]> getClaimReport(@PathVariable Long claimId) throws IOException {
        byte[] pdfContent = reportService.generateClaimReport(claimId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "Claim_Report_" + claimId + ".pdf");

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfContent);
    }
}
