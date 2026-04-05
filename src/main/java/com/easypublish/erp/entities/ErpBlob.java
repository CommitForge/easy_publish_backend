package com.easypublish.erp.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "erp_blob",
        indexes = {
                @Index(name = "idx_erp_blob_integration", columnList = "integration_id"),
                @Index(name = "idx_erp_blob_record", columnList = "record_id"),
                @Index(name = "idx_erp_blob_type", columnList = "blob_type")
        }
)
public class ErpBlob {

    @Id
    @Column(name = "id", nullable = false, length = 40)
    private String id;

    @Column(name = "integration_id", nullable = false, length = 40)
    private String integrationId;

    @Column(name = "record_id", length = 40)
    private String recordId;

    @Column(name = "blob_type", nullable = false, length = 32)
    private String blobType;

    @Column(name = "compression", length = 32)
    private String compression;

    @Column(name = "file_name", columnDefinition = "TEXT")
    private String fileName;

    @Column(name = "checksum_sha256", columnDefinition = "TEXT")
    private String checksumSha256;

    @Column(name = "original_size")
    private Long originalSize;

    @Column(name = "compressed_size")
    private Long compressedSize;

    @Lob
    @Column(name = "payload", nullable = false)
    private byte[] payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
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

    public String getBlobType() {
        return blobType;
    }

    public void setBlobType(String blobType) {
        this.blobType = blobType;
    }

    public String getCompression() {
        return compression;
    }

    public void setCompression(String compression) {
        this.compression = compression;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getChecksumSha256() {
        return checksumSha256;
    }

    public void setChecksumSha256(String checksumSha256) {
        this.checksumSha256 = checksumSha256;
    }

    public Long getOriginalSize() {
        return originalSize;
    }

    public void setOriginalSize(Long originalSize) {
        this.originalSize = originalSize;
    }

    public Long getCompressedSize() {
        return compressedSize;
    }

    public void setCompressedSize(Long compressedSize) {
        this.compressedSize = compressedSize;
    }

    public byte[] getPayload() {
        return payload;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
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

