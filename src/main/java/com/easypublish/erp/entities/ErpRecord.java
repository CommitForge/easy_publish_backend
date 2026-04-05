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
        name = "erp_record",
        indexes = {
                @Index(name = "idx_erp_record_integration", columnList = "integration_id"),
                @Index(name = "idx_erp_record_external", columnList = "external_record_id"),
                @Index(name = "idx_erp_record_publish_status", columnList = "publish_status"),
                @Index(name = "idx_erp_record_linked_data_item", columnList = "linked_data_item_id")
        }
)
public class ErpRecord {

    @Id
    @Column(name = "id", nullable = false, length = 40)
    private String id;

    @Column(name = "integration_id", nullable = false, length = 40)
    private String integrationId;

    @Column(name = "external_record_id", columnDefinition = "TEXT")
    private String externalRecordId;

    @Column(name = "record_name", columnDefinition = "TEXT")
    private String recordName;

    @Column(name = "record_description", columnDefinition = "TEXT")
    private String recordDescription;

    @Column(name = "container_id", length = 256)
    private String containerId;

    @Column(name = "data_type_id", length = 256)
    private String dataTypeId;

    @Column(name = "content_raw", columnDefinition = "TEXT")
    private String contentRaw;

    @Column(name = "content_compacted", columnDefinition = "TEXT")
    private String contentCompacted;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "recipients_csv", columnDefinition = "TEXT")
    private String recipientsCsv;

    @Column(name = "references_csv", columnDefinition = "TEXT")
    private String referencesCsv;

    @Column(name = "tags_csv", columnDefinition = "TEXT")
    private String tagsCsv;

    @Column(name = "should_publish", nullable = false)
    private boolean shouldPublish;

    @Column(name = "validation_status", nullable = false, length = 32)
    private String validationStatus;

    @Column(name = "validation_message", columnDefinition = "TEXT")
    private String validationMessage;

    @Column(name = "publish_status", nullable = false, length = 32)
    private String publishStatus;

    @Column(name = "zip_blob_id", length = 40)
    private String zipBlobId;

    @Column(name = "linked_data_item_id", length = 256)
    private String linkedDataItemId;

    @Column(name = "linked_verification_id", length = 256)
    private String linkedVerificationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        if (validationStatus == null || validationStatus.isBlank()) {
            validationStatus = "NOT_CHECKED";
        }
        if (publishStatus == null || publishStatus.isBlank()) {
            publishStatus = "NEW";
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

    public String getExternalRecordId() {
        return externalRecordId;
    }

    public void setExternalRecordId(String externalRecordId) {
        this.externalRecordId = externalRecordId;
    }

    public String getRecordName() {
        return recordName;
    }

    public void setRecordName(String recordName) {
        this.recordName = recordName;
    }

    public String getRecordDescription() {
        return recordDescription;
    }

    public void setRecordDescription(String recordDescription) {
        this.recordDescription = recordDescription;
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

    public String getContentRaw() {
        return contentRaw;
    }

    public void setContentRaw(String contentRaw) {
        this.contentRaw = contentRaw;
    }

    public String getContentCompacted() {
        return contentCompacted;
    }

    public void setContentCompacted(String contentCompacted) {
        this.contentCompacted = contentCompacted;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
    }

    public String getRecipientsCsv() {
        return recipientsCsv;
    }

    public void setRecipientsCsv(String recipientsCsv) {
        this.recipientsCsv = recipientsCsv;
    }

    public String getReferencesCsv() {
        return referencesCsv;
    }

    public void setReferencesCsv(String referencesCsv) {
        this.referencesCsv = referencesCsv;
    }

    public String getTagsCsv() {
        return tagsCsv;
    }

    public void setTagsCsv(String tagsCsv) {
        this.tagsCsv = tagsCsv;
    }

    public boolean isShouldPublish() {
        return shouldPublish;
    }

    public void setShouldPublish(boolean shouldPublish) {
        this.shouldPublish = shouldPublish;
    }

    public String getValidationStatus() {
        return validationStatus;
    }

    public void setValidationStatus(String validationStatus) {
        this.validationStatus = validationStatus;
    }

    public String getValidationMessage() {
        return validationMessage;
    }

    public void setValidationMessage(String validationMessage) {
        this.validationMessage = validationMessage;
    }

    public String getPublishStatus() {
        return publishStatus;
    }

    public void setPublishStatus(String publishStatus) {
        this.publishStatus = publishStatus;
    }

    public String getZipBlobId() {
        return zipBlobId;
    }

    public void setZipBlobId(String zipBlobId) {
        this.zipBlobId = zipBlobId;
    }

    public String getLinkedDataItemId() {
        return linkedDataItemId;
    }

    public void setLinkedDataItemId(String linkedDataItemId) {
        this.linkedDataItemId = linkedDataItemId;
    }

    public String getLinkedVerificationId() {
        return linkedVerificationId;
    }

    public void setLinkedVerificationId(String linkedVerificationId) {
        this.linkedVerificationId = linkedVerificationId;
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

