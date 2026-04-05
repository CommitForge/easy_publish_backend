package com.easypublish.erp.dto;

public class ErpRecordUpsertRequest {
    private String integrationId;
    private String externalRecordId;
    private String recordName;
    private String recordDescription;
    private String containerId;
    private String dataTypeId;
    private String contentRaw;
    private String metadataJson;
    private String recipientsCsv;
    private String referencesCsv;
    private String tagsCsv;
    private Boolean shouldPublish;

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

    public Boolean getShouldPublish() {
        return shouldPublish;
    }

    public void setShouldPublish(Boolean shouldPublish) {
        this.shouldPublish = shouldPublish;
    }
}
