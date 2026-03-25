package com.easypublish.controller;

import com.easypublish.service.ReportService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.thymeleaf.TemplateEngine;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReportControllerNativePdfTest {

    @Test
    void shouldUseNativeFallbackPdfGeneratorWithoutThymeleaf() throws Exception {
        String previousImageCode = System.getProperty("org.graalvm.nativeimage.imagecode");
        System.setProperty("org.graalvm.nativeimage.imagecode", "runtime");

        try {
            ReportService reportService = Mockito.mock(ReportService.class);
            TemplateEngine templateEngine = Mockito.mock(TemplateEngine.class);
            when(reportService.getCarMaintenancesByType("dt-native")).thenReturn(List.of());

            ReportController controller = new ReportController(reportService, templateEngine);
            var response = controller.generateCarReport(null, "dt-native");

            assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().length > 100);
            assertEquals(
                    "%PDF-",
                    new String(response.getBody(), 0, 5, StandardCharsets.US_ASCII)
            );

            verify(reportService).getCarMaintenancesByType("dt-native");
            verifyNoInteractions(templateEngine);
        } finally {
            if (previousImageCode == null) {
                System.clearProperty("org.graalvm.nativeimage.imagecode");
            } else {
                System.setProperty("org.graalvm.nativeimage.imagecode", previousImageCode);
            }
        }
    }
}
