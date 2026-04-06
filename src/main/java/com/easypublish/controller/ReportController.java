package com.easypublish.controller;

import com.easypublish.dtos.ContainerNodeDto;
import com.easypublish.dtos.ContainerTreeDto;
import com.easypublish.dtos.ContainerTreeIncludeEnum;
import com.easypublish.dtos.DataItemNodeDto;
import com.easypublish.dtos.DataTypeNodeDto;
import com.easypublish.entities.cars.maintenances.CarMaintenance;
import com.easypublish.entities.onchain.Container;
import com.easypublish.entities.onchain.Creator;
import com.easypublish.entities.onchain.DataItem;
import com.easypublish.entities.onchain.DataItemVerification;
import com.easypublish.entities.onchain.DataType;
import com.easypublish.service.NodeService;
import com.easypublish.service.ReportService;
import com.easypublish.service.SimplePdfReportGenerator;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/report")
public class ReportController {

    private enum SmartScope {
        CONTAINER,
        DATA_TYPE,
        DATA_ITEM
    }

    private final ReportService reportService;
    private final TemplateEngine templateEngine;
    private final NodeService nodeService;
    private final int smartReportMaxRecords;

    public ReportController(
            ReportService reportService,
            TemplateEngine templateEngine,
            NodeService nodeService,
            @Value("${app.report.smart.max-records:1000}") int smartReportMaxRecords
    ) {
        this.reportService = reportService;
        this.templateEngine = templateEngine;
        this.nodeService = nodeService;
        this.smartReportMaxRecords = Math.max(1, smartReportMaxRecords);
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

    @PostMapping(value = "/smart", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> generateSmartReport(
            @RequestParam String userAddress,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String containerId,
            @RequestParam(required = false) String dataTypeId,
            @RequestParam(required = false) String dataItemId,
            @RequestParam(required = false) String dataItemQuery,
            @RequestParam(required = false) String dataItemSearchFields,
            @RequestParam(required = false) Boolean dataItemVerified,
            @RequestParam(required = false) Boolean dataItemHasRevisions,
            @RequestParam(required = false) Boolean dataItemHasVerifications,
            @RequestParam(required = false) String dataItemDataType,
            @RequestParam(required = false) String dataItemSortBy,
            @RequestParam(required = false) String dataItemSortDirection,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) Integer limit
    ) throws Exception {
        if (userAddress == null || userAddress.isBlank()) {
            throw new IllegalArgumentException("userAddress is required");
        }

        String normalizedContainerId = normalizeBlank(containerId);
        String normalizedDataTypeId = normalizeBlank(dataTypeId);
        String normalizedDataItemId = normalizeBlank(dataItemId);
        String normalizedScope = normalizeBlank(scope);

        SmartScope effectiveScope = resolveScope(
                normalizedScope,
                normalizedContainerId,
                normalizedDataTypeId,
                normalizedDataItemId
        );

        int safeLimit = clampLimit(limit);

        EnumSet<ContainerTreeIncludeEnum> includes =
                effectiveScope == SmartScope.CONTAINER
                        ? EnumSet.of(ContainerTreeIncludeEnum.CONTAINER, ContainerTreeIncludeEnum.DATA_TYPE)
                        : EnumSet.of(
                        ContainerTreeIncludeEnum.CONTAINER,
                        ContainerTreeIncludeEnum.DATA_TYPE,
                        ContainerTreeIncludeEnum.DATA_ITEM,
                        ContainerTreeIncludeEnum.DATA_ITEM_VERIFICATION
                );

        ContainerTreeDto tree = nodeService.getContainerTree(
                normalizedContainerId,
                normalizedDataTypeId,
                normalizedDataItemId,
                null,
                null,
                null,
                null,
                null,
                null,
                effectiveScope == SmartScope.DATA_TYPE ? normalizeBlank(dataItemQuery) : null,
                effectiveScope == SmartScope.DATA_TYPE ? normalizeBlank(dataItemSearchFields) : null,
                effectiveScope == SmartScope.DATA_TYPE ? dataItemVerified : null,
                effectiveScope == SmartScope.DATA_TYPE ? dataItemHasRevisions : null,
                effectiveScope == SmartScope.DATA_TYPE ? dataItemHasVerifications : null,
                effectiveScope == SmartScope.DATA_TYPE ? normalizeBlank(dataItemDataType) : null,
                effectiveScope == SmartScope.DATA_TYPE ? normalizeBlank(dataItemSortBy) : null,
                effectiveScope == SmartScope.DATA_TYPE ? normalizeBlank(dataItemSortDirection) : null,
                userAddress,
                normalizeBlank(domain),
                0,
                safeLimit,
                includes
        );

        List<ContainerNodeDto> containerNodes = tree.getContainers() != null
                ? tree.getContainers()
                : List.of();
        Map<String, Object> meta = tree.getMeta() != null ? tree.getMeta() : Map.of();

        List<Map<String, Object>> typeRows = new ArrayList<>();
        List<Map<String, Object>> itemRows = new ArrayList<>();
        List<Map<String, Object>> verificationRows = new ArrayList<>();
        Map<String, Object> selectedItemSummary = null;

        long totalRecords;
        int displayedRecords;
        String reportScopeLabel;
        String idLabel;
        String reportId;
        String filterSummary = null;

        if (effectiveScope == SmartScope.CONTAINER) {
            reportScopeLabel = "Container Summary (Data Types)";
            idLabel = "Container ID";
            reportId = safe(normalizedContainerId);

            outer:
            for (ContainerNodeDto containerNode : containerNodes) {
                Container container = containerNode.getContainer();
                if (container == null) {
                    continue;
                }
                for (DataTypeNodeDto dataTypeNode : safeList(containerNode.getDataTypes())) {
                    DataType dataType = dataTypeNode.getDataType();
                    if (dataType == null) {
                        continue;
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("index", typeRows.size() + 1);
                    row.put("containerName", safe(container.getName()));
                    row.put("containerId", safe(container.getId()));
                    row.put("dataTypeName", safe(dataType.getName()));
                    row.put("dataTypeId", safe(dataType.getId()));
                    row.put("description", safe(dataType.getDescription()));
                    row.put("externalId", safe(dataType.getExternalId()));
                    row.put("creatorAddr", safeCreator(dataType.getCreator()));
                    row.put("createdOnChain", formatTimestamp(dataType.getCreator()));
                    typeRows.add(row);
                    if (typeRows.size() >= safeLimit) {
                        break outer;
                    }
                }
            }

            totalRecords = toLong(meta.get("totalDataTypes"), typeRows.size());
            displayedRecords = typeRows.size();
        } else if (effectiveScope == SmartScope.DATA_TYPE) {
            reportScopeLabel = "Data Type Summary (Data Items)";
            idLabel = "Data Type ID";
            reportId = safe(normalizedDataTypeId);

            outer:
            for (ContainerNodeDto containerNode : containerNodes) {
                Container container = containerNode.getContainer();
                for (DataTypeNodeDto dataTypeNode : safeList(containerNode.getDataTypes())) {
                    DataType dataType = dataTypeNode.getDataType();
                    for (DataItemNodeDto dataItemNode : safeList(dataTypeNode.getDataItems())) {
                        DataItem dataItem = dataItemNode.getDataItem();
                        if (dataItem == null) {
                            continue;
                        }

                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("index", itemRows.size() + 1);
                        row.put("containerName", safe(container != null ? container.getName() : null));
                        row.put("containerId", safe(container != null ? container.getId() : null));
                        row.put("dataTypeName", safe(dataType != null ? dataType.getName() : null));
                        row.put("dataTypeId", safe(dataType != null ? dataType.getId() : null));
                        row.put("dataItemName", safe(dataItem.getName()));
                        row.put("dataItemId", safe(dataItem.getId()));
                        row.put("description", safe(dataItem.getDescription()));
                        row.put("verified", yesNo(dataItem.isVerified()));
                        row.put("externalId", safe(dataItem.getExternalId()));
                        row.put("externalIndex", safeBigInteger(dataItem.getExternalIndex()));
                        row.put("creatorAddr", safeCreator(dataItem.getCreator()));
                        row.put("createdOnChain", formatTimestamp(dataItem.getCreator()));
                        row.put("verificationCount", safeList(dataItemNode.getDataItemVerifications()).size());
                        itemRows.add(row);

                        if (itemRows.size() >= safeLimit) {
                            break outer;
                        }
                    }
                }
            }

            totalRecords = toLong(meta.get("totalDataItems"), itemRows.size());
            displayedRecords = itemRows.size();
            filterSummary = buildFilterSummary(
                    dataItemQuery,
                    dataItemVerified,
                    dataItemHasRevisions,
                    dataItemHasVerifications,
                    dataItemSortBy,
                    dataItemSortDirection
            );
        } else {
            reportScopeLabel = "Single Data Item";
            idLabel = "Data Item ID";
            reportId = safe(normalizedDataItemId);

            DataItemNodeDto selectedNode = null;
            DataType selectedType = null;
            Container selectedContainer = null;

            outer:
            for (ContainerNodeDto containerNode : containerNodes) {
                Container container = containerNode.getContainer();
                for (DataTypeNodeDto dataTypeNode : safeList(containerNode.getDataTypes())) {
                    DataType dataType = dataTypeNode.getDataType();
                    for (DataItemNodeDto dataItemNode : safeList(dataTypeNode.getDataItems())) {
                        DataItem dataItem = dataItemNode.getDataItem();
                        if (dataItem == null) {
                            continue;
                        }
                        if (normalizedDataItemId != null && normalizedDataItemId.equals(dataItem.getId())) {
                            selectedNode = dataItemNode;
                            selectedType = dataType;
                            selectedContainer = container;
                            break outer;
                        }
                        if (selectedNode == null) {
                            selectedNode = dataItemNode;
                            selectedType = dataType;
                            selectedContainer = container;
                        }
                    }
                }
            }

            if (selectedNode != null && selectedNode.getDataItem() != null) {
                DataItem dataItem = selectedNode.getDataItem();
                selectedItemSummary = new LinkedHashMap<>();
                selectedItemSummary.put("containerName", safe(selectedContainer != null ? selectedContainer.getName() : null));
                selectedItemSummary.put("containerId", safe(selectedContainer != null ? selectedContainer.getId() : null));
                selectedItemSummary.put("dataTypeName", safe(selectedType != null ? selectedType.getName() : null));
                selectedItemSummary.put("dataTypeId", safe(selectedType != null ? selectedType.getId() : null));
                selectedItemSummary.put("dataItemName", safe(dataItem.getName()));
                selectedItemSummary.put("dataItemId", safe(dataItem.getId()));
                selectedItemSummary.put("description", safe(dataItem.getDescription()));
                selectedItemSummary.put("verified", yesNo(dataItem.isVerified()));
                selectedItemSummary.put("externalId", safe(dataItem.getExternalId()));
                selectedItemSummary.put("externalIndex", safeBigInteger(dataItem.getExternalIndex()));
                selectedItemSummary.put("creatorAddr", safeCreator(dataItem.getCreator()));
                selectedItemSummary.put("createdOnChain", formatTimestamp(dataItem.getCreator()));
                selectedItemSummary.put("contentPreview", abbreviate(dataItem.getContent(), 250));

                List<DataItemVerification> allVerifications = safeList(selectedNode.getDataItemVerifications());
                int verificationLimit = Math.min(safeLimit, allVerifications.size());
                for (int i = 0; i < verificationLimit; i++) {
                    DataItemVerification verification = allVerifications.get(i);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("index", verificationRows.size() + 1);
                    row.put("verificationId", safe(verification.getId()));
                    row.put("name", safe(verification.getName()));
                    row.put("description", safe(verification.getDescription()));
                    row.put("verified", yesNo(verification.getVerified()));
                    row.put("creatorAddr", safeCreator(verification.getCreator()));
                    row.put("createdOnChain", formatTimestamp(verification.getCreator()));
                    row.put("externalId", safe(verification.getExternalId()));
                    verificationRows.add(row);
                }

                totalRecords = allVerifications.size();
                displayedRecords = verificationRows.size();
            } else {
                totalRecords = 0;
                displayedRecords = 0;
            }
        }

        boolean truncated = totalRecords > displayedRecords;
        String limitNote = "This report is limited to first " + safeLimit + " records.";
        String truncationMessage = truncated
                ? "Report contains first " + displayedRecords + " of " + totalRecords + " records."
                : null;

        Context context = new Context();
        context.setVariable("generatedAt", LocalDateTime.now());
        context.setVariable("scopeKey", effectiveScope.name().toLowerCase(Locale.ROOT));
        context.setVariable("reportScope", reportScopeLabel);
        context.setVariable("idLabel", idLabel);
        context.setVariable("reportId", reportId);
        context.setVariable("limitNote", limitNote);
        context.setVariable("truncated", truncated);
        context.setVariable("truncationMessage", truncationMessage);
        context.setVariable("totalRecords", totalRecords);
        context.setVariable("displayedRecords", displayedRecords);
        context.setVariable("maxRecords", safeLimit);
        context.setVariable("filterSummary", filterSummary);
        context.setVariable("typeRows", typeRows);
        context.setVariable("itemRows", itemRows);
        context.setVariable("selectedItemSummary", selectedItemSummary);
        context.setVariable("verificationRows", verificationRows);
        context.setVariable("logoPath", "classpath:/templates/images/logo.png");

        List<String> fallbackLines = buildSmartFallbackLines(
                reportScopeLabel,
                idLabel,
                reportId,
                limitNote,
                truncationMessage,
                filterSummary,
                typeRows,
                itemRows,
                selectedItemSummary,
                verificationRows
        );

        byte[] pdfBytes;
        if (isNativeImageRuntime()) {
            pdfBytes = SimplePdfReportGenerator.generateTextReport("Smart Report", fallbackLines);
        } else {
            try {
                String html = templateEngine.process("reports/smart-report", context);
                ByteArrayOutputStream pdf = new ByteArrayOutputStream();

                PdfRendererBuilder builder = new PdfRendererBuilder();
                builder.withHtmlContent(html, null);
                builder.toStream(pdf);
                builder.run();
                pdfBytes = pdf.toByteArray();
            } catch (Exception ignored) {
                pdfBytes = SimplePdfReportGenerator.generateTextReport("Smart Report", fallbackLines);
            }
        }

        String fileName = "smart-report-" + effectiveScope.name().toLowerCase(Locale.ROOT) + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName)
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    private SmartScope resolveScope(
            String scope,
            String containerId,
            String dataTypeId,
            String dataItemId
    ) {
        SmartScope derived = null;

        if (dataItemId != null) {
            derived = SmartScope.DATA_ITEM;
        } else if (dataTypeId != null) {
            derived = SmartScope.DATA_TYPE;
        } else if (containerId != null) {
            derived = SmartScope.CONTAINER;
        }

        if (derived == null) {
            throw new IllegalArgumentException(
                    "Select current data context first (container, data type, or data item)."
            );
        }

        if (scope == null) {
            return derived;
        }

        SmartScope requested = parseScope(scope);
        if (requested != derived) {
            throw new IllegalArgumentException(
                    "scope does not match selected IDs (data item > data type > container)."
            );
        }

        return derived;
    }

    private static SmartScope parseScope(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "container" -> SmartScope.CONTAINER;
            case "data_type", "datatype", "type" -> SmartScope.DATA_TYPE;
            case "data_item", "dataitem", "item" -> SmartScope.DATA_ITEM;
            default -> throw new IllegalArgumentException("Unsupported scope: " + value);
        };
    }

    private int clampLimit(Integer requestedLimit) {
        if (requestedLimit == null) {
            return smartReportMaxRecords;
        }
        return Math.min(Math.max(1, requestedLimit), smartReportMaxRecords);
    }

    private static String buildFilterSummary(
            String query,
            Boolean verified,
            Boolean hasRevisions,
            Boolean hasVerifications,
            String sortBy,
            String sortDirection
    ) {
        List<String> parts = new ArrayList<>();

        if (query != null && !query.isBlank()) {
            parts.add("query=\"" + query.trim() + "\"");
        }
        if (verified != null) {
            parts.add("verified=" + (verified ? "true" : "false"));
        }
        if (hasRevisions != null) {
            parts.add("revisions=" + (hasRevisions ? "with" : "without"));
        }
        if (hasVerifications != null) {
            parts.add("verifications=" + (hasVerifications ? "with" : "without"));
        }
        if (sortBy != null && !sortBy.isBlank()) {
            String direction = (sortDirection != null && !sortDirection.isBlank())
                    ? sortDirection.trim().toLowerCase(Locale.ROOT)
                    : "desc";
            parts.add("sort=" + sortBy.trim() + " " + direction);
        }

        if (parts.isEmpty()) {
            return "No additional data item filters.";
        }

        return String.join(", ", parts);
    }

    private static <T> List<T> safeList(List<T> value) {
        return value != null ? value : List.of();
    }

    private static long toLong(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (Exception ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String safeBigInteger(BigInteger value) {
        return value == null ? "-" : value.toString();
    }

    private static String yesNo(Boolean value) {
        if (value == null) {
            return "-";
        }
        return value ? "Yes" : "No";
    }

    private static String safeCreator(Creator creator) {
        if (creator == null) {
            return "-";
        }
        return safe(creator.getCreatorAddr());
    }

    private static String formatTimestamp(Creator creator) {
        if (creator == null || creator.getCreatorTimestampMs() == null) {
            return "-";
        }

        try {
            long epochMs = creator.getCreatorTimestampMs().longValueExact();
            if (epochMs <= 0) {
                return "-";
            }
            return Instant.ofEpochMilli(epochMs)
                    .atZone(ZoneOffset.UTC)
                    .toString();
        } catch (Exception ignored) {
            return "-";
        }
    }

    private static String abbreviate(String value, int maxLength) {
        String safeValue = safe(value);
        if (safeValue.length() <= maxLength) {
            return safeValue;
        }
        return safeValue.substring(0, maxLength - 3) + "...";
    }

    private static List<String> buildSmartFallbackLines(
            String reportScope,
            String idLabel,
            String reportId,
            String limitNote,
            String truncationMessage,
            String filterSummary,
            List<Map<String, Object>> typeRows,
            List<Map<String, Object>> itemRows,
            Map<String, Object> selectedItemSummary,
            List<Map<String, Object>> verificationRows
    ) {
        List<String> lines = new ArrayList<>();
        lines.add("Scope: " + reportScope);
        lines.add(idLabel + ": " + reportId);
        lines.add(limitNote);

        if (truncationMessage != null) {
            lines.add(truncationMessage);
        }

        if (filterSummary != null) {
            lines.add("Filters: " + filterSummary);
        }

        if (!typeRows.isEmpty()) {
            lines.add("");
            lines.add("Data Types");
            for (Map<String, Object> row : typeRows) {
                lines.add(row.get("index") + ". "
                        + row.get("dataTypeName") + " | "
                        + row.get("dataTypeId") + " | "
                        + row.get("creatorAddr"));
            }
        }

        if (!itemRows.isEmpty()) {
            lines.add("");
            lines.add("Data Items");
            for (Map<String, Object> row : itemRows) {
                lines.add(row.get("index") + ". "
                        + row.get("dataItemName") + " | "
                        + row.get("dataItemId") + " | verified="
                        + row.get("verified") + " | verifications="
                        + row.get("verificationCount"));
            }
        }

        if (selectedItemSummary != null && !selectedItemSummary.isEmpty()) {
            lines.add("");
            lines.add("Data Item Details");
            lines.add("Name: " + selectedItemSummary.get("dataItemName"));
            lines.add("ID: " + selectedItemSummary.get("dataItemId"));
            lines.add("Verified: " + selectedItemSummary.get("verified"));
            lines.add("Creator: " + selectedItemSummary.get("creatorAddr"));
        }

        if (!verificationRows.isEmpty()) {
            lines.add("");
            lines.add("Item Verifications");
            for (Map<String, Object> row : verificationRows) {
                lines.add(row.get("index") + ". "
                        + row.get("verificationId") + " | "
                        + row.get("name") + " | verified="
                        + row.get("verified"));
            }
        }

        if (typeRows.isEmpty() && itemRows.isEmpty() && verificationRows.isEmpty()) {
            lines.add("");
            lines.add("No rows found for the selected report context.");
        }

        return lines;
    }

    private boolean isNativeImageRuntime() {
        return System.getProperty("org.graalvm.nativeimage.imagecode") != null;
    }
}
