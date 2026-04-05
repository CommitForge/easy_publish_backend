package com.easypublish.erp.service;

import com.easypublish.entities.onchain.DataItem;
import com.easypublish.entities.onchain.DataItemVerification;
import com.easypublish.erp.entities.ErpIntegration;
import com.easypublish.erp.entities.ErpRecord;
import com.easypublish.erp.entities.ErpVerificationCandidate;
import com.easypublish.erp.repositories.ErpRecordRepository;
import com.easypublish.erp.repositories.ErpVerificationCandidateRepository;
import com.easypublish.repositories.DataItemVerificationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ErpVerificationService {

    private final ErpRecordRepository recordRepository;
    private final ErpVerificationCandidateRepository candidateRepository;
    private final DataItemVerificationRepository verificationRepository;
    private final EntityManager entityManager;

    public ErpVerificationService(
            ErpRecordRepository recordRepository,
            ErpVerificationCandidateRepository candidateRepository,
            DataItemVerificationRepository verificationRepository,
            EntityManager entityManager
    ) {
        this.recordRepository = recordRepository;
        this.candidateRepository = candidateRepository;
        this.verificationRepository = verificationRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public Map<String, Object> refreshCandidates(ErpIntegration integration) {
        List<ErpRecord> records = recordRepository.findByIntegrationIdOrderByUpdatedAtDesc(integration.getId());
        Map<String, ErpRecord> recordByDataItem = records.stream()
                .filter(record -> ErpSecurityService.normalize(record.getLinkedDataItemId()) != null)
                .collect(Collectors.toMap(
                        record -> record.getLinkedDataItemId(),
                        record -> record,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        if (recordByDataItem.isEmpty()) {
            return Map.of(
                    "integrationId", integration.getId(),
                    "refreshed", 0,
                    "candidates", List.of(),
                    "info", "No linked data items found in ERP records."
            );
        }

        List<String> dataItemIds = new ArrayList<>(recordByDataItem.keySet());
        List<DataItem> dataItems = loadDataItemsByIds(dataItemIds);
        List<DataItemVerification> verifications = verificationRepository.findByDataItemIdIn(dataItemIds);
        Map<String, List<DataItemVerification>> byDataItemId = verifications.stream()
                .filter(verification -> ErpSecurityService.normalize(verification.getDataItemId()) != null)
                .collect(Collectors.groupingBy(DataItemVerification::getDataItemId));

        List<Map<String, Object>> refreshed = new ArrayList<>();
        for (DataItem dataItem : dataItems) {
            String dataItemId = ErpSecurityService.normalize(dataItem.getId());
            if (dataItemId == null) {
                continue;
            }
            ErpRecord record = recordByDataItem.get(dataItemId);
            if (record == null) {
                continue;
            }

            List<DataItemVerification> itemVerifications = byDataItemId.getOrDefault(dataItemId, List.of());
            boolean hasRecipients = dataItem.getRecipients() != null && !dataItem.getRecipients().isEmpty();
            boolean hasSuccessfulVerification = itemVerifications.stream()
                    .anyMatch(verification -> Boolean.TRUE.equals(verification.getVerified()));

            String reason;
            String status;
            if (!hasRecipients) {
                reason = "Data item has no recipients; verification may be optional.";
                status = "IGNORED";
            } else if (hasSuccessfulVerification) {
                reason = "Data item already has successful verification.";
                status = "RESOLVED";
            } else if (itemVerifications.isEmpty()) {
                reason = "Recipients exist but no verification records found.";
                status = "OPEN";
            } else {
                reason = "Verification records exist but none are successful yet.";
                status = "OPEN";
            }

            ErpVerificationCandidate candidate = candidateRepository
                    .findByIntegrationIdAndDataItemId(integration.getId(), dataItemId)
                    .orElseGet(ErpVerificationCandidate::new);
            candidate.setIntegrationId(integration.getId());
            candidate.setRecordId(record.getId());
            candidate.setContainerId(dataItem.getContainerId());
            candidate.setDataTypeId(dataItem.getDataTypeId());
            candidate.setDataItemId(dataItemId);
            candidate.setReason(reason);
            candidate.setSuggestedRecipientsCsv(String.join(",", dedupeCsv(dataItem.getRecipients())));
            candidate.setExistingVerificationIdsCsv(
                    itemVerifications.stream()
                            .map(DataItemVerification::getId)
                            .filter(Objects::nonNull)
                            .distinct()
                            .collect(Collectors.joining(","))
            );
            candidate.setStatus(status);
            ErpVerificationCandidate saved = candidateRepository.save(candidate);
            refreshed.add(toResponse(saved));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("integrationId", integration.getId());
        response.put("refreshed", refreshed.size());
        response.put("candidates", refreshed);
        return response;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCandidates(String integrationId, String status) {
        String normalizedStatus = ErpSecurityService.normalize(status);
        List<ErpVerificationCandidate> rows = normalizedStatus == null
                ? candidateRepository.findByIntegrationIdOrderByUpdatedAtDesc(integrationId)
                : candidateRepository.findByIntegrationIdAndStatusOrderByUpdatedAtDesc(
                integrationId,
                normalizedStatus.toUpperCase()
        );
        return rows.stream().map(this::toResponse).toList();
    }

    @Transactional
    public Map<String, Object> updateCandidateStatus(
            String integrationId,
            String candidateId,
            String status
    ) {
        ErpVerificationCandidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ERP verification candidate not found"));
        if (!integrationId.equals(candidate.getIntegrationId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Candidate/integration mismatch");
        }

        String normalizedStatus = ErpSecurityService.normalize(status);
        if (normalizedStatus == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status is required");
        }
        String finalStatus = normalizedStatus.toUpperCase();
        if (!List.of("OPEN", "RESOLVED", "IGNORED").contains(finalStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported candidate status");
        }
        candidate.setStatus(finalStatus);
        return toResponse(candidateRepository.save(candidate));
    }

    private List<DataItem> loadDataItemsByIds(List<String> ids) {
        TypedQuery<DataItem> query = entityManager.createQuery(
                "SELECT di FROM DataItem di WHERE di.id IN :ids",
                DataItem.class
        );
        query.setParameter("ids", ids);
        return query.getResultList();
    }

    private List<String> dedupeCsv(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Set<String> deduped = new LinkedHashSet<>();
        for (String value : values) {
            String normalized = ErpSecurityService.normalize(value);
            if (normalized != null) {
                deduped.add(normalized);
            }
        }
        return new ArrayList<>(deduped);
    }

    private Map<String, Object> toResponse(ErpVerificationCandidate candidate) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", candidate.getId());
        map.put("integrationId", candidate.getIntegrationId());
        map.put("recordId", candidate.getRecordId());
        map.put("containerId", candidate.getContainerId());
        map.put("dataTypeId", candidate.getDataTypeId());
        map.put("dataItemId", candidate.getDataItemId());
        map.put("reason", candidate.getReason());
        map.put("suggestedRecipientsCsv", candidate.getSuggestedRecipientsCsv());
        map.put("existingVerificationIdsCsv", candidate.getExistingVerificationIdsCsv());
        map.put("status", candidate.getStatus());
        map.put("createdAt", candidate.getCreatedAt());
        map.put("updatedAt", candidate.getUpdatedAt());
        return map;
    }
}

