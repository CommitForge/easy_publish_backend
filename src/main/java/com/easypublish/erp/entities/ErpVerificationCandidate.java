package com.easypublish.erp.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "erp_verification_candidate",
        indexes = {
                @Index(name = "idx_erp_verif_candidate_integration", columnList = "integration_id"),
                @Index(name = "idx_erp_verif_candidate_data_item", columnList = "data_item_id"),
                @Index(name = "idx_erp_verif_candidate_status", columnList = "status")
        }
)
public class ErpVerificationCandidate {

    @Id
    @Column(name = "id", nullable = false, length = 40)
    private String id;

    @Column(name = "integration_id", nullable = false, length = 40)
    private String integrationId;

    @Column(name = "record_id", length = 40)
    private String recordId;

    @Column(name = "container_id", length = 256)
    private String containerId;

    @Column(name = "data_type_id", length = 256)
    private String dataTypeId;

    @Column(name = "data_item_id", nullable = false, length = 256)
    private String dataItemId;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "suggested_recipients_csv", columnDefinition = "TEXT")
    private String suggestedRecipientsCsv;

    @Column(name = "existing_verification_ids_csv", columnDefinition = "TEXT")
    private String existingVerificationIdsCsv;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        if (status == null || status.isBlank()) {
            status = "OPEN";
        }
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getIntegrationId() {
        return integrationId;
    }

    public void setIntegrationId(String integrationId) {
        this.integrationId = integrationId;
    }

    public String getRecordId() {
        return recordId;
    }

    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }

    public String getContainerId() {
        return containerId;
    }

    public void setContainerId(String containerId) {
        this.containerId = containerId;
    }

    public String getDataTypeId() {
        return dataTypeId;
    }

    public void setDataTypeId(String dataTypeId) {
        this.dataTypeId = dataTypeId;
    }

    public String getDataItemId() {
        return dataItemId;
    }

    public void setDataItemId(String dataItemId) {
        this.dataItemId = dataItemId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getSuggestedRecipientsCsv() {
        return suggestedRecipientsCsv;
    }

    public void setSuggestedRecipientsCsv(String suggestedRecipientsCsv) {
        this.suggestedRecipientsCsv = suggestedRecipientsCsv;
    }

    public String getExistingVerificationIdsCsv() {
        return existingVerificationIdsCsv;
    }

    public void setExistingVerificationIdsCsv(String existingVerificationIdsCsv) {
        this.existingVerificationIdsCsv = existingVerificationIdsCsv;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

