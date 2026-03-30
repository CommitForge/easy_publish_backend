package com.easypublish.controller;

import com.easypublish.entities.cars.maintenances.CarMaintenance;
import com.easypublish.service.ReportService;
import com.easypublish.service.SimplePdfReportGenerator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/report")
public class ReportController {

    private final ReportService reportService;
    private final TemplateEngine templateEngine;

    public ReportController(ReportService reportService, TemplateEngine templateEngine) {
        this.reportService = reportService;
        this.templateEngine = templateEngine;
    }

    @PostMapping(value = "/car", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> generateCarReport(
            @RequestParam(required = false) String dataItemId,
            @RequestParam(required = false) String dataTypeId) throws Exception {

        ReportService.CarReportPayload reportPayload;

        if (dataItemId != null && !dataItemId.isBlank()) {
            reportPayload = reportService.getCarReportByDataItem(dataItemId);
        } else if (dataTypeId != null && !dataTypeId.isBlank()) {
            reportPayload = reportService.getCarReportByType(dataTypeId);
        } else {
            throw new IllegalArgumentException("Either dataItemId or dataTypeId must be provided");
        }

        List<CarMaintenance> maintenances = reportPayload.getMaintenances();
        List<ReportService.RevisionMetadata> previousRevisions = reportPayload.getPreviousRevisions();

        Context context = new Context();
        context.setVariable("maintenances", maintenances);
        context.setVariable("previousRevisions", previousRevisions);

        String reportId = dataItemId != null && !dataItemId.isBlank() ? dataItemId : dataTypeId;
        String idLabel = dataItemId != null && !dataItemId.isBlank() ? "Data Item ID" : "Data Type ID";

        context.setVariable("reportId", reportId);
        context.setVariable("idLabel", idLabel);

        context.setVariable("generatedAt", LocalDateTime.now());
        context.setVariable("logoPath", "classpath:/templates/images/logo-cars.png");

        context.setVariable("reportScope",
                dataItemId != null && !dataItemId.isBlank()
                        ? "Single Maintenance Record"
                        : "Latest Revision Snapshot");

        byte[] pdfBytes;
        if (isNativeImageRuntime()) {
            pdfBytes = SimplePdfReportGenerator.generateCarMaintenanceReport(
                    maintenances,
                    previousRevisions,
                    idLabel,
                    reportId,
                    context.getVariable("generatedAt")
            );
        } else {
            String html = templateEngine.process("reports/car-report", context);
            ByteArrayOutputStream pdf = new ByteArrayOutputStream();

            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, null);
            builder.toStream(pdf);
            builder.run();
            pdfBytes = pdf.toByteArray();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=car-report.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    private boolean isNativeImageRuntime() {
        return System.getProperty("org.graalvm.nativeimage.imagecode") != null;
    }
}
