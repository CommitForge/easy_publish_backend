package com.easypublish.service;

import com.easypublish.entities.cars.maintenances.CarMaintenance;

import java.nio.charset.StandardCharsets;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Native-safe fallback PDF generator that does not rely on PDFBox/AWT.
 * It produces a basic text-table PDF using built-in Helvetica.
 */
public final class SimplePdfReportGenerator {

    private static final int PAGE_WIDTH = 842;
    private static final int PAGE_HEIGHT = 595;
    private static final int START_X = 36;
    private static final int START_Y = 560;
    private static final int LINE_HEIGHT = 14;
    private static final int MAX_LINES_PER_PAGE = 34;

    private SimplePdfReportGenerator() {
    }

    public static byte[] generateCarMaintenanceReport(
            List<CarMaintenance> maintenances,
            List<ReportService.RevisionMetadata> previousRevisions,
            String idLabel,
            String reportId,
            Object generatedAt
    ) {
        List<CarMaintenance> rows = maintenances != null ? maintenances : List.of();
        List<ReportService.RevisionMetadata> revisionRows =
                previousRevisions != null ? previousRevisions : List.of();

        List<String> lines = new ArrayList<>();
        lines.add("Car Maintenance Report");
        lines.add("Generated: " + safe(generatedAt));
        lines.add(idLabel + ": " + safe(reportId));
        lines.add("");
        lines.add("Latest Revisions (Maintenance Content)");
        lines.add(headerRow());
        lines.add(repeat('-', 164));

        for (CarMaintenance maintenance : rows) {
            lines.add(formatRow(maintenance));
        }

        if (rows.isEmpty()) {
            lines.add("No maintenance rows found for the requested filter.");
        }

        lines.add("");
        lines.add("Previous Revisions (Metadata Only)");
        lines.add(revisionHeaderRow());
        lines.add(repeat('-', 156));

        for (ReportService.RevisionMetadata revision : revisionRows) {
            lines.add(formatRevisionRow(revision));
        }

        if (revisionRows.isEmpty()) {
            lines.add("No previous revisions found.");
        }

        List<String> pageStreams = buildPageStreams(lines);
        return buildPdf(pageStreams);
    }

    private static List<String> buildPageStreams(List<String> lines) {
        List<String> streams = new ArrayList<>();
        int totalPages = Math.max(1, (int) Math.ceil(lines.size() / (double) MAX_LINES_PER_PAGE));

        for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
            int from = pageIndex * MAX_LINES_PER_PAGE;
            int to = Math.min(lines.size(), from + MAX_LINES_PER_PAGE);
            List<String> pageLines = lines.subList(from, to);

            StringBuilder stream = new StringBuilder();
            stream.append("BT\n");
            stream.append("/F1 10 Tf\n");
            stream.append(START_X).append(' ').append(START_Y).append(" Td\n");

            for (int i = 0; i < pageLines.size(); i++) {
                if (i > 0) {
                    stream.append("0 -").append(LINE_HEIGHT).append(" Td\n");
                }
                stream.append('(')
                        .append(escapePdfText(pageLines.get(i)))
                        .append(") Tj\n");
            }

            stream.append("0 -").append(LINE_HEIGHT + 6).append(" Td\n");
            stream.append("(Page ")
                    .append(pageIndex + 1)
                    .append(" / ")
                    .append(totalPages)
                    .append(") Tj\n");
            stream.append("ET\n");
            streams.add(stream.toString());
        }

        return streams;
    }

    private static byte[] buildPdf(List<String> pageStreams) {
        int pageCount = pageStreams.size();
        int fontObjectId = 1;
        int firstContentObjectId = 2;
        int firstPageObjectId = firstContentObjectId + pageCount;
        int pagesObjectId = firstPageObjectId + pageCount;
        int catalogObjectId = pagesObjectId + 1;
        int totalObjects = catalogObjectId;

        String[] objects = new String[totalObjects + 1];
        objects[fontObjectId] = "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>";

        for (int i = 0; i < pageCount; i++) {
            String stream = ascii(pageStreams.get(i));
            int contentObjectId = firstContentObjectId + i;
            int pageObjectId = firstPageObjectId + i;

            objects[contentObjectId] =
                    "<< /Length " + stream.getBytes(StandardCharsets.US_ASCII).length + " >>\n"
                            + "stream\n"
                            + stream
                            + "endstream";

            objects[pageObjectId] =
                    "<< /Type /Page"
                            + " /Parent " + pagesObjectId + " 0 R"
                            + " /MediaBox [0 0 " + PAGE_WIDTH + " " + PAGE_HEIGHT + "]"
                            + " /Resources << /Font << /F1 " + fontObjectId + " 0 R >> >>"
                            + " /Contents " + contentObjectId + " 0 R"
                            + " >>";
        }

        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < pageCount; i++) {
            if (i > 0) {
                kids.append(' ');
            }
            kids.append(firstPageObjectId + i).append(" 0 R");
        }

        objects[pagesObjectId] =
                "<< /Type /Pages /Kids [" + kids + "] /Count " + pageCount + " >>";

        objects[catalogObjectId] =
                "<< /Type /Catalog /Pages " + pagesObjectId + " 0 R >>";

        StringBuilder pdf = new StringBuilder();
        pdf.append("%PDF-1.4\n");

        int[] offsets = new int[totalObjects + 1];
        for (int objectId = 1; objectId <= totalObjects; objectId++) {
            offsets[objectId] = pdf.length();
            pdf.append(objectId)
                    .append(" 0 obj\n")
                    .append(objects[objectId])
                    .append("\nendobj\n");
        }

        int xrefOffset = pdf.length();
        pdf.append("xref\n")
                .append("0 ").append(totalObjects + 1).append('\n')
                .append("0000000000 65535 f \n");

        for (int objectId = 1; objectId <= totalObjects; objectId++) {
            pdf.append(String.format(Locale.ROOT, "%010d 00000 n \n", offsets[objectId]));
        }

        pdf.append("trailer\n")
                .append("<< /Size ").append(totalObjects + 1)
                .append(" /Root ").append(catalogObjectId).append(" 0 R >>\n")
                .append("startxref\n")
                .append(xrefOffset).append('\n')
                .append("%%EOF\n");

        return pdf.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private static String headerRow() {
        return pad("Date", 12)
                + " | " + pad("Distance", 12)
                + " | " + pad("Service", 28)
                + " | " + pad("Parts", 24)
                + " | " + pad("Cost", 10)
                + " | " + pad("Performed By", 24)
                + " | " + pad("Note", 40);
    }

    private static String revisionHeaderRow() {
        return pad("Date", 12)
                + " | " + pad("Data Item ID", 68)
                + " | " + pad("Name", 70);
    }

    private static String formatRow(CarMaintenance m) {
        return pad(m.getDate(), 12)
                + " | " + pad(m.getDistance(), 12)
                + " | " + pad(m.getService(), 28)
                + " | " + pad(m.getParts(), 24)
                + " | " + pad(m.getCost(), 10)
                + " | " + pad(m.getPerformedBy(), 24)
                + " | " + pad(m.getNote(), 40);
    }

    private static String formatRevisionRow(ReportService.RevisionMetadata revision) {
        return pad(revision.getDate(), 12)
                + " | " + pad(revision.getId(), 68)
                + " | " + pad(revision.getName(), 70);
    }

    private static String pad(String value, int maxLen) {
        String text = safe(value);
        if (text.length() > maxLen) {
            return text.substring(0, maxLen - 1) + "~";
        }
        return String.format(Locale.ROOT, "%-" + maxLen + "s", text);
    }

    private static String safe(Object value) {
        if (value == null) {
            return "-";
        }
        if (value instanceof TemporalAccessor) {
            return value.toString();
        }
        return ascii(value.toString());
    }

    private static String ascii(String input) {
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\r' || c == '\n' || c == '\t') {
                out.append(' ');
            } else if (c >= 32 && c <= 126) {
                out.append(c);
            } else {
                out.append('?');
            }
        }
        return out.toString();
    }

    private static String escapePdfText(String text) {
        StringBuilder escaped = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' || c == '(' || c == ')') {
                escaped.append('\\');
            }
            escaped.append(c);
        }
        return escaped.toString();
    }

    private static String repeat(char c, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(c);
        }
        return builder.toString();
    }
}
