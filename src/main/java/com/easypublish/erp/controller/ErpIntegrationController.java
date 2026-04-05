package com.easypublish.erp.controller;

import com.easypublish.erp.dto.ErpIntegrationCreateRequest;
import com.easypublish.erp.dto.ErpIntegrationUpdateRequest;
import com.easypublish.erp.dto.ErpPublishRequest;
import com.easypublish.erp.dto.ErpRecordBulkRequest;
import com.easypublish.erp.dto.ErpRecordUpsertRequest;
import com.easypublish.erp.dto.ErpStatusUpdateRequest;
import com.easypublish.erp.entities.ErpIntegration;
import com.easypublish.erp.service.ErpIntegrationService;
import com.easypublish.erp.service.ErpPublishService;
import com.easypublish.erp.service.ErpRecordService;
import com.easypublish.erp.service.ErpSecurityService;
import com.easypublish.erp.service.ErpVerificationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@ConditionalOnProperty(name = "app.erp.api.enabled", havingValue = "true")
@RequestMapping("/api/erp")
public class ErpIntegrationController {

    private final ErpIntegrationService integrationService;
    private final ErpSecurityService securityService;
    private final ErpRecordService recordService;
    private final ErpPublishService publishService;
    private final ErpVerificationService verificationService;

    public ErpIntegrationController(
            ErpIntegrationService integrationService,
            ErpSecurityService securityService,
            ErpRecordService recordService,
            ErpPublishService publishService,
            ErpVerificationService verificationService
    ) {
        this.integrationService = integrationService;
        this.securityService = securityService;
        this.recordService = recordService;
        this.publishService = publishService;
        this.verificationService = verificationService;
    }

    @PostMapping("/integrations")
    public Map<String, Object> createIntegration(@RequestBody ErpIntegrationCreateRequest request) {
        return integrationService.createIntegration(request);
    }

    @GetMapping("/integrations")
    public List<Map<String, Object>> listIntegrations(@RequestParam String ownerAddress) {
        return integrationService.listIntegrations(ownerAddress);
    }

    @GetMapping("/integrations/{integrationId}")
    public Map<String, Object> getIntegration(
            @PathVariable String integrationId,
            @RequestParam(required = false, defaultValue = "false") boolean includeSecret,
            @RequestParam(required = false) String ownerAddress,
            @RequestHeader(value = "X-ERP-API-KEY", required = false) String apiKey
    ) {
        authorize(integrationId, ownerAddress, apiKey);
        return integrationService.getIntegration(integrationId, includeSecret);
    }

    @PatchMapping("/integrations/{integrationId}")
    public Map<String, Object> updateIntegration(
            @PathVariable String integrationId,
            @RequestBody ErpIntegrationUpdateRequest request,
            @RequestParam(required = false) String ownerAddress,
            @RequestHeader(value = "X-ERP-API-KEY", required = false) String apiKey
    ) {
        authorize(integrationId, ownerAddress, apiKey);
        return integrationService.updateIntegration(integrationId, request);
    }

    @PostMapping("/integrations/{integrationId}/rotate-key")
    public Map<String, Object> rotateIntegrationApiKey(
            @PathVariable String integrationId,
            @RequestParam(required = false) String ownerAddress,
            @RequestHeader(value = "X-ERP-API-KEY", required = false) String apiKey
    ) {
        authorize(integrationId, ownerAddress, apiKey);
        return integrationService.rotateApiKey(integrationId);
    }

    @PostMapping("/records")
    public Map<String, Object> upsertRecord(
            @RequestBody ErpRecordUpsertRequest request,
            @RequestParam(required = false) String ownerAddress,
            @RequestHeader(value = "X-ERP-API-KEY", required = false) String apiKey
    ) {
        ErpIntegration integration = authorize(request.getIntegrationId(), ownerAddress, apiKey);
        return recordService.upsertRecord(integration, request);
    }

    @PostMapping("/records/bulk")
    public Map<String, Object> upsertRecordsBulk(
            @RequestBody ErpRecordBulkRequest request,
            @RequestParam(required = false) String ownerAddress,
            @RequestHeader(value = "X-ERP-API-KEY", required = false) String apiKey
    ) {
        ErpIntegration integration = authorize(request.getIntegrationId(), ownerAddress, apiKey);
        return recordService.upsertBulk(integration, request);
    }

    @GetMapping("/records")
    public List<Map<String, Object>> listRecords(
            @RequestParam String integrationId,
            @RequestParam(required = false) String publishStatus,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String ownerAddress,
            @RequestHeader(value = "X-ERP-API-KEY", required = false) String apiKey
    ) {
        authorize(integrationId, ownerAddress, apiKey);
        return recordService.listRecords(integrationId, publishStatus, query);
    }

    @GetMapping("/records/{recordId}")
    public Map<String, Object> getRecord(
            @PathVariable String recordId,
            @RequestParam String integrationId,
            @RequestParam(required = false) String ownerAddress,
            @RequestHeader(value = "X-ERP-API-KEY", required = false) String apiKey
    ) {
        authorize(integrationId, ownerAddress, apiKey);
        return recordService.getRecord(integrationId, recordId);
    }

    @PostMapping("/records/{recordId}/check")
    public Map<String, Object> checkRecord(
            @PathVariable String recordId,
            @RequestParam String integrationId,
            @RequestParam(required = false) String ownerAddress,
            @RequestHeader(value = "X-ERP-API-KEY", required = false) String apiKey
    ) {
        ErpIntegration integration = authorize(integrationId, ownerAddress, apiKey);
        return recordService.checkRecord(integration, recordId);
    }

    @PostMapping("/records/{recordId}/compact")
    public Map<String, Object> compactRecord(
            @PathVariable String recordId,
            @RequestParam String integrationId,
            @RequestParam(required = false) String ownerAddress,
            @RequestHeader(value = "X-ERP-API-KEY", required = false) String apiKey
    ) {
        ErpIntegration integration = authorize(integrationId, ownerAddress, apiKey);
        return recordService.compactRecord(integration, recordId);
    }

    @PostMapping("/records/{recordId}/zip")
    public Map<String, Object> zipRecord(
            @PathVariable String recordId,
            @RequestParam String integrationId,
            @RequestParam(required = false) String ownerAddress,
            @RequestHeader(value = "X-ERP-API-KEY", required = false) String apiKey
    ) {
        ErpIntegration integration = authorize(integrationId, ownerAddress, apiKey);
        return recordService.zipRecord(integration, recordId);
    }

    @PostMapping("/records/{recordId}/unzip")
    public Map<String, Object> unzipRecord(
            @PathVariable String recordId,
            @RequestParam String integrationId,
            @RequestParam(required = false) String ownerAddress,
            @RequestHeader(value = "X-ERP-API-KEY", required = false) String apiKey
    ) {
        ErpIntegration integration = authorize(integrationId, ownerAddress, apiKey);
        return recordService.unzipRecord(integration, recordId);
    }

    @PostMapping("/jobs/publish")
    public Map<String, Object> publish(
            @RequestBody ErpPublishRequest request,
            @RequestParam(required = false) String ownerAddress,
            @RequestHeader(value = "X-ERP-API-KEY", required = false) String apiKey
    ) {
        ErpIntegration integration = authorize(request.getIntegrationId(), ownerAddress, apiKey);
        return publishService.publishRecords(integration, request);
    }

    @GetMapping("/jobs")
    public List<Map<String, Object>> listJobs(
            @RequestParam String integrationId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String ownerAddress,
            @RequestHeader(value = "X-ERP-API-KEY", required = false) String apiKey
    ) {
        authorize(integrationId, ownerAddress, apiKey);
        return publishService.listJobs(integrationId, status);
    }

    @GetMapping("/jobs/{jobId}")
    public Map<String, Object> getJob(
            @PathVariable String jobId,
            @RequestParam String integrationId,
            @RequestParam(required = false) String ownerAddress,
            @RequestHeader(value = "X-ERP-API-KEY", required = false) String apiKey
    ) {
        authorize(integrationId, ownerAddress, apiKey);
        return publishService.getJob(integrationId, jobId);
    }

    @PostMapping("/jobs/{jobId}/retry")
    public Map<String, Object> retryJob(
            @PathVariable String jobId,
            @RequestParam String integrationId,
            @RequestParam(required = false) String ownerAddress,
            @RequestHeader(value = "X-ERP-API-KEY", required = false) String apiKey
    ) {
        ErpIntegration integration = authorize(integrationId, ownerAddress, apiKey);
        return publishService.retryJob(integration, jobId);
    }

    @PostMapping("/jobs/{jobId}/sync-check")
    public Map<String, Object> syncCheckJob(
            @PathVariable String jobId,
            @RequestParam String integrationId,
            @RequestParam(required = false) String ownerAddress,
            @RequestHeader(value = "X-ERP-API-KEY", required = false) String apiKey
    ) {
        ErpIntegration integration = authorize(integrationId, ownerAddress, apiKey);
        return publishService.refreshSyncForJob(integration, jobId);
    }

    @PostMapping("/jobs/sync-refresh")
    public Map<String, Object> syncRefreshIntegration(
            @RequestParam String integrationId,
            @RequestParam(required = false) String ownerAddress,
            @RequestHeader(value = "X-ERP-API-KEY", required = false) String apiKey
    ) {
        ErpIntegration integration = authorize(integrationId, ownerAddress, apiKey);
        return publishService.refreshSyncForIntegration(integration);
    }

    @PostMapping("/jobs/diagnostics")
    public Map<String, Object> runCliDiagnostics(
            @RequestParam String integrationId,
            @RequestParam(required = false) String ownerAddress,
            @RequestHeader(value = "X-ERP-API-KEY", required = false) String apiKey
    ) {
        ErpIntegration integration = authorize(integrationId, ownerAddress, apiKey);
        return publishService.runCliDiagnostics(integration);
    }

    @PostMapping("/verifications/candidates/refresh")
    public Map<String, Object> refreshCandidates(
            @RequestParam String integrationId,
            @RequestParam(required = false) String ownerAddress,
            @RequestHeader(value = "X-ERP-API-KEY", required = false) String apiKey
    ) {
        ErpIntegration integration = authorize(integrationId, ownerAddress, apiKey);
        return verificationService.refreshCandidates(integration);
    }

    @GetMapping("/verifications/candidates")
    public List<Map<String, Object>> listCandidates(
            @RequestParam String integrationId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String ownerAddress,
            @RequestHeader(value = "X-ERP-API-KEY", required = false) String apiKey
    ) {
        authorize(integrationId, ownerAddress, apiKey);
        return verificationService.listCandidates(integrationId, status);
    }

    @PatchMapping("/verifications/candidates/{candidateId}")
    public Map<String, Object> updateCandidateStatus(
            @PathVariable String candidateId,
            @RequestParam String integrationId,
            @RequestBody ErpStatusUpdateRequest request,
            @RequestParam(required = false) String ownerAddress,
            @RequestHeader(value = "X-ERP-API-KEY", required = false) String apiKey
    ) {
        authorize(integrationId, ownerAddress, apiKey);
        return verificationService.updateCandidateStatus(integrationId, candidateId, request.getStatus());
    }

    private ErpIntegration authorize(
            String integrationId,
            String ownerAddress,
            String apiKey
    ) {
        return securityService.requireAuthorizedIntegration(integrationId, apiKey, ownerAddress);
    }
}
