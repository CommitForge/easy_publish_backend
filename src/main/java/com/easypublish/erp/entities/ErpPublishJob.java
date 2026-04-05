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
        name = "erp_publish_job",
        indexes = {
                @Index(name = "idx_erp_publish_job_integration", columnList = "integration_id"),
                @Index(name = "idx_erp_publish_job_record", columnList = "record_id"),
                @Index(name = "idx_erp_publish_job_status", columnList = "status")
        }
)
public class ErpPublishJob {

    @Id
    @Column(name = "id", nullable = false, length = 40)
    private String id;

    @Column(name = "integration_id", nullable = false, length = 40)
    private String integrationId;

    @Column(name = "record_id", nullable = false, length = 40)
    private String recordId;

    @Column(name = "job_type", nullable = false, length = 32)
    private String jobType;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "dry_run", nullable = false)
    private boolean dryRun;

    @Column(name = "request_payload_json", columnDefinition = "TEXT")
    private String requestPayloadJson;

    @Column(name = "cli_command", columnDefinition = "TEXT")
    private String cliCommand;

    @Column(name = "cli_stdout", columnDefinition = "TEXT")
    private String cliStdout;

    @Column(name = "cli_stderr", columnDefinition = "TEXT")
    private String cliStderr;

    @Column(name = "cli_exit_code")
    private Integer cliExitCode;

    @Column(name = "tx_digest", length = 256)
    private String txDigest;

    @Column(name = "sync_checked_at")
    private Instant syncCheckedAt;

    @Column(name = "sync_message", columnDefinition = "TEXT")
    private String syncMessage;

    @Column(name = "result_data_item_id", length = 256)
    private String resultDataItemId;

    @Column(name = "result_verification_id", length = 256)
    private String resultVerificationId;

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
            status = "QUEUED";
        }
        if (jobType == null || jobType.isBlank()) {
            jobType = "PUBLISH_DATA_ITEM";
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

    public String getJobType() {
        return jobType;
    }

    public void setJobType(String jobType) {
        this.jobType = jobType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public String getRequestPayloadJson() {
        return requestPayloadJson;
    }

    public void setRequestPayloadJson(String requestPayloadJson) {
        this.requestPayloadJson = requestPayloadJson;
    }

    public String getCliCommand() {
        return cliCommand;
    }

    public void setCliCommand(String cliCommand) {
        this.cliCommand = cliCommand;
    }

    public String getCliStdout() {
        return cliStdout;
    }

    public void setCliStdout(String cliStdout) {
        this.cliStdout = cliStdout;
    }

    public String getCliStderr() {
        return cliStderr;
    }

    public void setCliStderr(String cliStderr) {
        this.cliStderr = cliStderr;
    }

    public Integer getCliExitCode() {
        return cliExitCode;
    }

    public void setCliExitCode(Integer cliExitCode) {
        this.cliExitCode = cliExitCode;
    }

    public String getTxDigest() {
        return txDigest;
    }

    public void setTxDigest(String txDigest) {
        this.txDigest = txDigest;
    }

    public Instant getSyncCheckedAt() {
        return syncCheckedAt;
    }

    public void setSyncCheckedAt(Instant syncCheckedAt) {
        this.syncCheckedAt = syncCheckedAt;
    }

    public String getSyncMessage() {
        return syncMessage;
    }

    public void setSyncMessage(String syncMessage) {
        this.syncMessage = syncMessage;
    }

    public String getResultDataItemId() {
        return resultDataItemId;
    }

    public void setResultDataItemId(String resultDataItemId) {
        this.resultDataItemId = resultDataItemId;
    }

    public String getResultVerificationId() {
        return resultVerificationId;
    }

    public void setResultVerificationId(String resultVerificationId) {
        this.resultVerificationId = resultVerificationId;
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

