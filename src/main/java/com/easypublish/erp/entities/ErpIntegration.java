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
        name = "erp_integration",
        indexes = {
                @Index(name = "idx_erp_integration_owner", columnList = "owner_address"),
                @Index(name = "idx_erp_integration_status", columnList = "status"),
                @Index(name = "idx_erp_integration_api_key", columnList = "api_key")
        }
)
public class ErpIntegration {

    @Id
    @Column(name = "id", nullable = false, length = 40)
    private String id;

    @Column(name = "owner_address", nullable = false, columnDefinition = "TEXT")
    private String ownerAddress;

    @Column(name = "name", nullable = false, columnDefinition = "TEXT")
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "api_key", nullable = false, columnDefinition = "TEXT")
    private String apiKey;

    @Column(name = "webhook_url", columnDefinition = "TEXT")
    private String webhookUrl;

    @Column(name = "default_container_id", length = 256)
    private String defaultContainerId;

    @Column(name = "default_data_type_id", length = 256)
    private String defaultDataTypeId;

    @Column(name = "cli_binary", columnDefinition = "TEXT")
    private String cliBinary;

    @Column(name = "cli_script", columnDefinition = "TEXT")
    private String cliScript;

    @Column(name = "cli_working_directory", columnDefinition = "TEXT")
    private String cliWorkingDirectory;

    @Column(name = "cli_network", length = 64)
    private String cliNetwork;

    @Column(name = "cli_private_key_env_var", length = 128)
    private String cliPrivateKeyEnvVar;

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
            status = "ACTIVE";
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

    public String getOwnerAddress() {
        return ownerAddress;
    }

    public void setOwnerAddress(String ownerAddress) {
        this.ownerAddress = ownerAddress;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public String getDefaultContainerId() {
        return defaultContainerId;
    }

    public void setDefaultContainerId(String defaultContainerId) {
        this.defaultContainerId = defaultContainerId;
    }

    public String getDefaultDataTypeId() {
        return defaultDataTypeId;
    }

    public void setDefaultDataTypeId(String defaultDataTypeId) {
        this.defaultDataTypeId = defaultDataTypeId;
    }

    public String getCliBinary() {
        return cliBinary;
    }

    public void setCliBinary(String cliBinary) {
        this.cliBinary = cliBinary;
    }

    public String getCliScript() {
        return cliScript;
    }

    public void setCliScript(String cliScript) {
        this.cliScript = cliScript;
    }

    public String getCliWorkingDirectory() {
        return cliWorkingDirectory;
    }

    public void setCliWorkingDirectory(String cliWorkingDirectory) {
        this.cliWorkingDirectory = cliWorkingDirectory;
    }

    public String getCliNetwork() {
        return cliNetwork;
    }

    public void setCliNetwork(String cliNetwork) {
        this.cliNetwork = cliNetwork;
    }

    public String getCliPrivateKeyEnvVar() {
        return cliPrivateKeyEnvVar;
    }

    public void setCliPrivateKeyEnvVar(String cliPrivateKeyEnvVar) {
        this.cliPrivateKeyEnvVar = cliPrivateKeyEnvVar;
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

