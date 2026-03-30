package com.easypublish.service;

import com.easypublish.entities.cars.maintenances.CarMaintenance;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SimplePdfReportGeneratorTest {

    @Test
    void shouldGenerateValidPdfHeaderAndContent() {
        CarMaintenance maintenance = new CarMaintenance();
        maintenance.setDate("2026-03-24");
        maintenance.setDistance("120000");
        maintenance.setService("Brake pads (front)");
        maintenance.setParts("Pads + fluid");
        maintenance.setCost("350");
        maintenance.setPerformedBy("Garage A");
        maintenance.setNote("Everything OK");

        byte[] pdfBytes = SimplePdfReportGenerator.generateCarMaintenanceReport(
                List.of(maintenance),
                List.of(),
                "Data Type ID",
                "0xabc",
                LocalDateTime.of(2026, 3, 24, 19, 0)
        );

        String pdfText = new String(pdfBytes, StandardCharsets.US_ASCII);

        assertTrue(pdfText.startsWith("%PDF-1.4"));
        assertTrue(pdfText.contains("Car Maintenance Report"));
        assertTrue(pdfText.contains("Data Type ID"));
        assertTrue(pdfText.contains("0xabc"));
    }
}
