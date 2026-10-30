package application.controller.report;

import application.domain.dto.report.AdminReportResponse;
import application.domain.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API de Reporting (SDD - Reporting Services).
 * Solo el rol autorizado (Administrator) consulta reportes administrativos.
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * GenerateAdministrativeReport (SDD): informacion de seguimiento
     * administrativo del Marketplace. Solo lectura.
     */
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<AdminReportResponse> generateAdministrativeReport() {
        return ResponseEntity.ok(reportService.generateAdministrativeReport());
    }
}
