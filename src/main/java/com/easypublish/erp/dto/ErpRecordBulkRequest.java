package com.easypublish.erp.dto;

import java.util.ArrayList;
import java.util.List;

public class ErpRecordBulkRequest {
    private String integrationId;
    private List<ErpRecordUpsertRequest> records = new ArrayList<>();

    public String getIntegrationId() {
        return integrationId;
    }

    public void setIntegrationId(String integrationId) {
        this.integrationId = integrationId;
    }

    public List<ErpRecordUpsertRequest> getRecords() {
        return records;
    }

    public void setRecords(List<ErpRecordUpsertRequest> records) {
        this.records = records != null ? records : new ArrayList<>();
    }
}
