package com.easypublish.erp.dto;

public class ErpIntegrationCreateRequest {
    private String ownerAddress;
    private String name;
    private String description;
    private String webhookUrl;
    private String defaultContainerId;
    private String defaultDataTypeId;
    private String cliBinary;
    private String cliScript;
    private String cliWorkingDirectory;
    private String cliNetwork;
    private String cliPrivateKeyEnvVar;

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
}
