package com.easypublish.erp.service;

import com.easypublish.erp.dto.ErpRecordBulkRequest;
import com.easypublish.erp.dto.ErpRecordUpsertRequest;
import com.easypublish.erp.entities.ErpBlob;
import com.easypublish.erp.entities.ErpIntegration;
import com.easypublish.erp.entities.ErpRecord;
import com.easypublish.erp.repositories.ErpBlobRepository;
import com.easypublish.erp.repositories.ErpRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class ErpRecordService {

    private final ErpRecordRepository recordRepository;
    private final ErpBlobRepository blobRepository;
    private final ObjectMapper objectMapper;

    public ErpRecordService(
            ErpRecordRepository recordRepository,
            ErpBlobRepository blobRepository,
            ObjectMapper objectMapper
    ) {
        this.recordRepository = recordRepository;
        this.blobRepository = blobRepository;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> upsertRecord(ErpIntegration integration, ErpRecordUpsertRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing record payload");
        }

        String integrationId = integration.getId();
        String requestIntegrationId = ErpSecurityService.normalize(request.getIntegrationId());
        if (requestIntegrationId != null && !requestIntegrationId.equals(integrationId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Record integrationId mismatch");
        }

        ErpRecord record = findByExternalOrNew(integrationId, request.getExternalRecordId());
        applyRecordPayload(record, request, integration);
        record.setIntegrationId(integrationId);
        ErpRecord saved = recordRepository.save(record);
        return toResponse(saved, null);
    }

    public Map<String, Object> upsertBulk(ErpIntegration integration, ErpRecordBulkRequest request) {
        if (request == null || request.getRecords() == null || request.getRecords().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "records[] is required");
        }
        String requestIntegrationId = ErpSecurityService.normalize(request.getIntegrationId());
        if (requestIntegrationId != null && !requestIntegrationId.equals(integration.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bulk integrationId mismatch");
        }

        List<Map<String, Object>> savedRows = new ArrayList<>();
        for (ErpRecordUpsertRequest row : request.getRecords()) {
            savedRows.add(upsertRecord(integration, row));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("integrationId", integration.getId());
        response.put("count", savedRows.size());
        response.put("records", savedRows);
        return response;
    }

    public List<Map<String, Object>> listRecords(
            String integrationId,
            String publishStatus,
            String query
    ) {
        List<ErpRecord> rows = ErpSecurityService.normalize(publishStatus) == null
                ? recordRepository.findByIntegrationIdOrderByUpdatedAtDesc(integrationId)
                : recordRepository.findByIntegrationIdAndPublishStatusOrderByUpdatedAtDesc(
                integrationId,
                publishStatus.trim().toUpperCase()
        );

        String normalizedQuery = ErpSecurityService.normalize(query);
        if (normalizedQuery != null) {
            String q = normalizedQuery.toLowerCase();
            rows = rows.stream().filter(row ->
                    containsIgnoreCase(row.getExternalRecordId(), q)
                            || containsIgnoreCase(row.getRecordName(), q)
                            || containsIgnoreCase(row.getRecordDescription(), q)
                            || containsIgnoreCase(row.getContainerId(), q)
                            || containsIgnoreCase(row.getDataTypeId(), q)
            ).toList();
        }

        return rows.stream().map(row -> toResponse(row, null)).toList();
    }

    public Map<String, Object> getRecord(String integrationId, String recordId) {
        ErpRecord record = requireRecord(integrationId, recordId);
        return toResponse(record, null);
    }

    public Map<String, Object> checkRecord(ErpIntegration integration, String recordId) {
        ErpRecord record = requireRecord(integration.getId(), recordId);
        List<String> errors = new ArrayList<>();
        String containerId = effectiveContainerId(record, integration);
        String dataTypeId = effectiveDataTypeId(record, integration);
        String recordName = ErpSecurityService.normalize(record.getRecordName());
        String content = effectiveContent(record);

        if (containerId == null) {
            errors.add("Missing containerId (record/container default)");
        }
        if (dataTypeId == null) {
            errors.add("Missing dataTypeId (record/type default)");
        }
        if (recordName == null) {
            errors.add("Missing recordName");
        }
        if (content == null) {
            errors.add("Missing contentRaw/contentCompacted");
        }

        String metadataJson = ErpSecurityService.normalize(record.getMetadataJson());
        if (metadataJson != null) {
            try {
                objectMapper.readTree(metadataJson);
            } catch (Exception ex) {
                errors.add("metadataJson is not valid JSON");
            }
        }

        if (errors.isEmpty()) {
            record.setValidationStatus("VALID");
            record.setValidationMessage("Record check passed.");
        } else {
            record.setValidationStatus("INVALID");
            record.setValidationMessage(String.join("; ", errors));
        }

        ErpRecord saved = recordRepository.save(record);
        return toResponse(saved, null);
    }

    public Map<String, Object> compactRecord(ErpIntegration integration, String recordId) {
        ErpRecord record = requireRecord(integration.getId(), recordId);
        String content = ErpSecurityService.normalize(record.getContentRaw());
        if (content == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "contentRaw is empty");
        }

        String compacted = compact(content);
        record.setContentCompacted(compacted);
        ErpRecord saved = recordRepository.save(record);

        Map<String, Object> response = toResponse(saved, null);
        response.put("compactedLength", compacted.length());
        return response;
    }

    public Map<String, Object> zipRecord(ErpIntegration integration, String recordId) {
        ErpRecord record = requireRecord(integration.getId(), recordId);
        String content = effectiveContent(record);
        if (content == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No content available to zip");
        }

        byte[] compressed = zipString(content, "record-" + record.getId() + ".json");
        String checksum = sha256Hex(compressed);

        ErpBlob blob = new ErpBlob();
        blob.setIntegrationId(integration.getId());
        blob.setRecordId(record.getId());
        blob.setBlobType("RECORD_CONTENT_ZIP");
        blob.setCompression("zip");
        blob.setFileName("record-" + record.getId() + ".zip");
        blob.setOriginalSize((long) content.getBytes(StandardCharsets.UTF_8).length);
        blob.setCompressedSize((long) compressed.length);
        blob.setChecksumSha256(checksum);
        blob.setPayload(compressed);
        ErpBlob savedBlob = blobRepository.save(blob);

        record.setZipBlobId(savedBlob.getId());
        ErpRecord savedRecord = recordRepository.save(record);

        Map<String, Object> response = toResponse(savedRecord, null);
        response.put("blobId", savedBlob.getId());
        response.put("compressedSize", savedBlob.getCompressedSize());
        response.put("originalSize", savedBlob.getOriginalSize());
        response.put("checksumSha256", checksum);
        return response;
    }

    public Map<String, Object> unzipRecord(ErpIntegration integration, String recordId) {
        ErpRecord record = requireRecord(integration.getId(), recordId);
        String zipBlobId = ErpSecurityService.normalize(record.getZipBlobId());
        if (zipBlobId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Record has no zip blob");
        }
        ErpBlob blob = blobRepository.findById(zipBlobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ERP blob not found"));

        String unzipped = unzipToString(blob.getPayload());
        Map<String, Object> response = toResponse(record, null);
        response.put("unzippedContent", unzipped);
        response.put("unzippedBase64", Base64.getEncoder().encodeToString(unzipped.getBytes(StandardCharsets.UTF_8)));
        return response;
    }

    public ErpRecord requireRecord(String integrationId, String recordId) {
        ErpRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ERP record not found"));
        if (!integrationId.equals(record.getIntegrationId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Record/integration mismatch");
        }
        return record;
    }

    public String effectiveContainerId(ErpRecord record, ErpIntegration integration) {
        String recordValue = ErpSecurityService.normalize(record.getContainerId());
        return recordValue != null ? recordValue : ErpSecurityService.normalize(integration.getDefaultContainerId());
    }

    public String effectiveDataTypeId(ErpRecord record, ErpIntegration integration) {
        String recordValue = ErpSecurityService.normalize(record.getDataTypeId());
        return recordValue != null ? recordValue : ErpSecurityService.normalize(integration.getDefaultDataTypeId());
    }

    public String effectiveContent(ErpRecord record) {
        String compacted = ErpSecurityService.normalize(record.getContentCompacted());
        if (compacted != null) {
            return compacted;
        }
        return ErpSecurityService.normalize(record.getContentRaw());
    }

    private void applyRecordPayload(ErpRecord record, ErpRecordUpsertRequest request, ErpIntegration integration) {
        if (record.getIntegrationId() == null) {
            record.setIntegrationId(integration.getId());
        }
        if (request.getExternalRecordId() != null) {
            record.setExternalRecordId(ErpSecurityService.normalize(request.getExternalRecordId()));
        }
        if (request.getRecordName() != null) {
            record.setRecordName(ErpSecurityService.normalize(request.getRecordName()));
        }
        if (request.getRecordDescription() != null) {
            record.setRecordDescription(ErpSecurityService.normalize(request.getRecordDescription()));
        }
        if (request.getContainerId() != null) {
            record.setContainerId(ErpSecurityService.normalize(request.getContainerId()));
        }
        if (request.getDataTypeId() != null) {
            record.setDataTypeId(ErpSecurityService.normalize(request.getDataTypeId()));
        }
        if (request.getContentRaw() != null) {
            record.setContentRaw(request.getContentRaw());
            record.setContentCompacted(null);
        }
        if (request.getMetadataJson() != null) {
            record.setMetadataJson(request.getMetadataJson());
        }
        if (request.getRecipientsCsv() != null) {
            record.setRecipientsCsv(ErpSecurityService.normalize(request.getRecipientsCsv()));
        }
        if (request.getReferencesCsv() != null) {
            record.setReferencesCsv(ErpSecurityService.normalize(request.getReferencesCsv()));
        }
        if (request.getTagsCsv() != null) {
            record.setTagsCsv(ErpSecurityService.normalize(request.getTagsCsv()));
        }
        if (request.getShouldPublish() != null) {
            record.setShouldPublish(request.getShouldPublish());
        }
        if (record.getValidationStatus() == null || record.getValidationStatus().isBlank()) {
            record.setValidationStatus("NOT_CHECKED");
        }
        if (record.getPublishStatus() == null || record.getPublishStatus().isBlank()) {
            record.setPublishStatus("NEW");
        }
        record.setUpdatedAt(Instant.now());
        if (record.getCreatedAt() == null) {
            record.setCreatedAt(Instant.now());
        }
    }

    private ErpRecord findByExternalOrNew(String integrationId, String externalRecordIdRaw) {
        String externalRecordId = ErpSecurityService.normalize(externalRecordIdRaw);
        if (externalRecordId == null) {
            return new ErpRecord();
        }
        Optional<ErpRecord> existing = recordRepository.findByIntegrationIdAndExternalRecordId(
                integrationId,
                externalRecordId
        );
        return existing.orElseGet(ErpRecord::new);
    }

    public Map<String, Object> toResponse(ErpRecord record, String extraInfo) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", record.getId());
        map.put("integrationId", record.getIntegrationId());
        map.put("externalRecordId", record.getExternalRecordId());
        map.put("recordName", record.getRecordName());
        map.put("recordDescription", record.getRecordDescription());
        map.put("containerId", record.getContainerId());
        map.put("dataTypeId", record.getDataTypeId());
        map.put("contentRaw", record.getContentRaw());
        map.put("contentCompacted", record.getContentCompacted());
        map.put("metadataJson", record.getMetadataJson());
        map.put("recipientsCsv", record.getRecipientsCsv());
        map.put("referencesCsv", record.getReferencesCsv());
        map.put("tagsCsv", record.getTagsCsv());
        map.put("shouldPublish", record.isShouldPublish());
        map.put("validationStatus", record.getValidationStatus());
        map.put("validationMessage", record.getValidationMessage());
        map.put("publishStatus", record.getPublishStatus());
        map.put("zipBlobId", record.getZipBlobId());
        map.put("linkedDataItemId", record.getLinkedDataItemId());
        map.put("linkedVerificationId", record.getLinkedVerificationId());
        map.put("createdAt", record.getCreatedAt());
        map.put("updatedAt", record.getUpdatedAt());
        if (extraInfo != null) {
            map.put("info", extraInfo);
        }
        return map;
    }

    private String compact(String value) {
        try {
            return objectMapper.writeValueAsString(objectMapper.readTree(value));
        } catch (Exception ignored) {
            return value.trim().replaceAll("\\s+", " ");
        }
    }

    private byte[] zipString(String content, String entryName) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output)) {
                ZipEntry entry = new ZipEntry(entryName);
                zip.putNextEntry(entry);
                zip.write(content.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            return output.toByteArray();
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to zip record", ex);
        }
    }

    private String unzipToString(byte[] payload) {
        try (ZipInputStream zipInput = new ZipInputStream(new ByteArrayInputStream(payload))) {
            ZipEntry entry = zipInput.getNextEntry();
            if (entry == null) {
                return "";
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = zipInput.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to unzip record", ex);
        }
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to hash payload", ex);
        }
    }

    private boolean containsIgnoreCase(String value, String q) {
        return value != null && value.toLowerCase().contains(q);
    }
}

