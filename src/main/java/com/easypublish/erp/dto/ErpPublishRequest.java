package com.easypublish.erp.dto;

import java.util.ArrayList;
import java.util.List;

public class ErpPublishRequest {
    private String integrationId;
    private List<String> recordIds = new ArrayList<>();
    private boolean dryRun;

    public String getIntegrationId() {
        return integrationId;
    }

    public void setIntegrationId(String integrationId) {
        this.integrationId = integrationId;
    }

    public List<String> getRecordIds() {
        return recordIds;
    }

    public void setRecordIds(List<String> recordIds) {
        this.recordIds = recordIds != null ? recordIds : new ArrayList<>();
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }
}
