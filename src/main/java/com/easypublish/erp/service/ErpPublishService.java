package com.easypublish.erp.service;

import com.easypublish.entities.onchain.DataItem;
import com.easypublish.erp.dto.ErpPublishRequest;
import com.easypublish.erp.entities.ErpIntegration;
import com.easypublish.erp.entities.ErpPublishJob;
import com.easypublish.erp.entities.ErpRecord;
import com.easypublish.erp.entities.ErpSyncCursor;
import com.easypublish.erp.repositories.ErpPublishJobRepository;
import com.easypublish.erp.repositories.ErpRecordRepository;
import com.easypublish.erp.repositories.ErpSyncCursorRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ErpPublishService {

    private final ErpRecordRepository recordRepository;
    private final ErpPublishJobRepository publishJobRepository;
    private final ErpSyncCursorRepository syncCursorRepository;
    private final ErpRecordService recordService;
    private final ErpCliService cliService;
    private final EntityManager entityManager;

    public ErpPublishService(
            ErpRecordRepository recordRepository,
            ErpPublishJobRepository publishJobRepository,
            ErpSyncCursorRepository syncCursorRepository,
            ErpRecordService recordService,
            ErpCliService cliService,
            EntityManager entityManager
    ) {
        this.recordRepository = recordRepository;
        this.publishJobRepository = publishJobRepository;
        this.syncCursorRepository = syncCursorRepository;
        this.recordService = recordService;
        this.cliService = cliService;
        this.entityManager = entityManager;
    }

    @Transactional
    public Map<String, Object> publishRecords(ErpIntegration integration, ErpPublishRequest request) {
        if (request == null || request.getRecordIds() == null || request.getRecordIds().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "recordIds[] is required");
        }
        String requestIntegrationId = ErpSecurityService.normalize(request.getIntegrationId());
        if (requestIntegrationId != null && !requestIntegrationId.equals(integration.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "integrationId mismatch");
        }

        List<Map<String, Object>> jobs = new ArrayList<>();
        for (String recordIdRaw : request.getRecordIds()) {
            String recordId = ErpSecurityService.normalize(recordIdRaw);
            if (recordId == null) {
                continue;
            }
            ErpRecord record = recordService.requireRecord(integration.getId(), recordId);
            jobs.add(runSinglePublish(integration, record, request.isDryRun()));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("integrationId", integration.getId());
        response.put("count", jobs.size());
        response.put("jobs", jobs);
        return response;
    }

    @Transactional
    public Map<String, Object> retryJob(ErpIntegration integration, String jobId) {
        ErpPublishJob existing = requireJob(integration.getId(), jobId);
        ErpRecord record = recordService.requireRecord(integration.getId(), existing.getRecordId());
        Map<String, Object> rerun = runSinglePublish(integration, record, existing.isDryRun());
        rerun.put("retryOfJobId", existing.getId());
        return rerun;
    }

    @Transactional
    public Map<String, Object> refreshSyncForJob(ErpIntegration integration, String jobId) {
        ErpPublishJob job = requireJob(integration.getId(), jobId);
        updateSyncStatus(integration, job);
        return toJobResponse(job);
    }

    @Transactional
    public Map<String, Object> refreshSyncForIntegration(ErpIntegration integration) {
        List<ErpPublishJob> waiting = publishJobRepository.findByIntegrationIdAndStatusOrderByUpdatedAtDesc(
                integration.getId(),
                "WAITING_SYNC"
        );
        List<Map<String, Object>> results = new ArrayList<>();
        for (ErpPublishJob job : waiting) {
            updateSyncStatus(integration, job);
            results.add(toJobResponse(job));
        }

        ErpSyncCursor cursor = syncCursorRepository
                .findByIntegrationIdAndCursorType(integration.getId(), "PUBLISH_SYNC")
                .orElseGet(ErpSyncCursor::new);
        cursor.setIntegrationId(integration.getId());
        cursor.setCursorType("PUBLISH_SYNC");
        cursor.setCursorValue(String.valueOf(results.size()));
        cursor.setLastSyncedAt(Instant.now());
        cursor.setSyncMessage("Refreshed WAITING_SYNC jobs: " + results.size());
        syncCursorRepository.save(cursor);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("integrationId", integration.getId());
        response.put("refreshedJobs", results.size());
        response.put("jobs", results);
        return response;
    }

    public List<Map<String, Object>> listJobs(String integrationId, String status) {
        List<ErpPublishJob> jobs = ErpSecurityService.normalize(status) == null
                ? publishJobRepository.findByIntegrationIdOrderByUpdatedAtDesc(integrationId)
                : publishJobRepository.findByIntegrationIdAndStatusOrderByUpdatedAtDesc(
                integrationId,
                status.trim().toUpperCase()
        );
        return jobs.stream().map(this::toJobResponse).toList();
    }

    public Map<String, Object> getJob(String integrationId, String jobId) {
        return toJobResponse(requireJob(integrationId, jobId));
    }

    public Map<String, Object> runCliDiagnostics(ErpIntegration integration) {
        ErpCliService.CliExecutionResult result = cliService.runDiagnostics(integration);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("command", result.command());
        map.put("exitCode", result.exitCode());
        map.put("stdout", result.stdout());
        map.put("stderr", result.stderr());
        map.put("finishedAt", result.finishedAt());
        return map;
    }

    private Map<String, Object> runSinglePublish(
            ErpIntegration integration,
            ErpRecord record,
            boolean dryRun
    ) {
        recordService.checkRecord(integration, record.getId());
        record = recordService.requireRecord(integration.getId(), record.getId());

        ErpPublishJob job = new ErpPublishJob();
        job.setIntegrationId(integration.getId());
        job.setRecordId(record.getId());
        job.setDryRun(dryRun);
        job.setJobType("PUBLISH_DATA_ITEM");
        job.setStatus("RUNNING_CLI");
        job.setAttempts(1);
        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("recordId", record.getId());
        payloadMap.put("externalRecordId", record.getExternalRecordId());
        payloadMap.put("dryRun", dryRun);
        job.setRequestPayloadJson(payloadMap.toString());
        publishJobRepository.save(job);

        if (!dryRun && !record.isShouldPublish()) {
            job.setStatus("SKIPPED");
            job.setCliExitCode(0);
            job.setSyncCheckedAt(Instant.now());
            job.setSyncMessage("Record marked shouldPublish=false; skipped on-chain publish.");
            publishJobRepository.save(job);

            record.setPublishStatus("SKIPPED");
            recordRepository.save(record);
            return toJobResponse(job);
        }

        String containerId = recordService.effectiveContainerId(record, integration);
        String dataTypeId = recordService.effectiveDataTypeId(record, integration);
        String content = recordService.effectiveContent(record);
        if (containerId == null || dataTypeId == null || content == null) {
            job.setStatus("VALIDATION_FAILED");
            job.setCliExitCode(-1);
            job.setCliStderr("Missing required publish fields after effective resolution");
            publishJobRepository.save(job);
            record.setPublishStatus("FAILED");
            recordRepository.save(record);
            return toJobResponse(job);
        }

        ErpCliService.CliExecutionResult result = cliService.runPublishDataItem(
                integration,
                record,
                containerId,
                dataTypeId,
                content,
                dryRun
        );

        job.setCliCommand(result.command());
        job.setCliExitCode(result.exitCode());
        job.setCliStdout(result.stdout());
        job.setCliStderr(result.stderr());
        job.setTxDigest(result.txDigest());

        if (!result.isSuccess()) {
            job.setStatus("CLI_FAILED");
            record.setPublishStatus("FAILED");
            recordRepository.save(record);
            publishJobRepository.save(job);
            return toJobResponse(job);
        }

        if (dryRun) {
            job.setStatus("DRY_RUN_OK");
            job.setSyncCheckedAt(Instant.now());
            job.setSyncMessage("Dry run successful. No on-chain publish executed.");
            record.setPublishStatus("CHECKED");
            recordRepository.save(record);
            publishJobRepository.save(job);
            return toJobResponse(job);
        }

        job.setStatus("WAITING_SYNC");
        record.setPublishStatus("WAITING_SYNC");
        publishJobRepository.save(job);
        recordRepository.save(record);

        updateSyncStatus(integration, job);
        return toJobResponse(job);
    }

    private void updateSyncStatus(ErpIntegration integration, ErpPublishJob job) {
        ErpRecord record = recordService.requireRecord(integration.getId(), job.getRecordId());
        String externalRecordId = ErpSecurityService.normalize(record.getExternalRecordId());
        if (externalRecordId == null) {
            job.setSyncCheckedAt(Instant.now());
            job.setSyncMessage("No externalRecordId to match sync state.");
            publishJobRepository.save(job);
            return;
        }

        TypedQuery<DataItem> query = entityManager.createQuery(
                "SELECT di FROM DataItem di WHERE di.externalId = :externalId ORDER BY di.creator.creatorTimestampMs DESC",
                DataItem.class
        );
        query.setParameter("externalId", externalRecordId);
        List<DataItem> matches = query.setMaxResults(5).getResultList();
        if (matches.isEmpty()) {
            job.setStatus("WAITING_SYNC");
            job.setSyncCheckedAt(Instant.now());
            job.setSyncMessage("No synced on-chain data item found yet.");
            publishJobRepository.save(job);
            return;
        }

        DataItem match = matches.get(0);
        job.setStatus("SYNCED");
        job.setSyncCheckedAt(Instant.now());
        job.setSyncMessage("Matched synced on-chain data item by externalId.");
        job.setResultDataItemId(match.getId());
        publishJobRepository.save(job);

        record.setPublishStatus("PUBLISHED");
        record.setLinkedDataItemId(match.getId());
        recordRepository.save(record);
    }

    private ErpPublishJob requireJob(String integrationId, String jobId) {
        ErpPublishJob job = publishJobRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ERP publish job not found"));
        if (!integrationId.equals(job.getIntegrationId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Job/integration mismatch");
        }
        return job;
    }

    private Map<String, Object> toJobResponse(ErpPublishJob job) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", job.getId());
        map.put("integrationId", job.getIntegrationId());
        map.put("recordId", job.getRecordId());
        map.put("jobType", job.getJobType());
        map.put("status", job.getStatus());
        map.put("dryRun", job.isDryRun());
        map.put("attempts", job.getAttempts());
        map.put("requestPayloadJson", job.getRequestPayloadJson());
        map.put("cliCommand", job.getCliCommand());
        map.put("cliExitCode", job.getCliExitCode());
        map.put("cliStdout", job.getCliStdout());
        map.put("cliStderr", job.getCliStderr());
        map.put("txDigest", job.getTxDigest());
        map.put("resultDataItemId", job.getResultDataItemId());
        map.put("resultVerificationId", job.getResultVerificationId());
        map.put("syncCheckedAt", job.getSyncCheckedAt());
        map.put("syncMessage", job.getSyncMessage());
        map.put("createdAt", job.getCreatedAt());
        map.put("updatedAt", job.getUpdatedAt());
        return map;
    }
}
