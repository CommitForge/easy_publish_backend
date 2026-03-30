package com.easypublish.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OnchainSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(OnchainSyncScheduler.class);

    private final NodeSyncService nodeSyncService;

    @Value("${app.onchain-sync.enabled:true}")
    private boolean enabled;

    @Value("${app.onchain-sync.smart-contract-id:}")
    private String smartContractId;

    @Value("${app.onchain-sync.container-chain-id:}")
    private String containerChainId;

    @Value("${app.onchain-sync.update-chain-id:}")
    private String updateChainId;

    @Value("${app.onchain-sync.data-item-chain-id:}")
    private String dataItemChainId;

    @Value("${app.onchain-sync.data-item-verification-chain-id:}")
    private String dataItemVerificationChainId;

    public OnchainSyncScheduler(NodeSyncService nodeSyncService) {
        this.nodeSyncService = nodeSyncService;
    }

    @Scheduled(
            fixedDelayString = "${app.onchain-sync.fixed-delay-ms:60000}",
            initialDelayString = "${app.onchain-sync.initial-delay-ms:15000}"
    )
    public void scheduledOnchainSync() {
        if (!enabled) {
            return;
        }

        if (isBlank(containerChainId) || isBlank(updateChainId) || isBlank(dataItemChainId) || isBlank(dataItemVerificationChainId)) {
            log.warn("[ONCHAIN-SYNC] skipped: missing required chain IDs in application.properties");
            return;
        }

        try {
            nodeSyncService.syncUpdateChainRecursively(
                    containerChainId,
                    updateChainId,
                    dataItemChainId,
                    dataItemVerificationChainId
            );

            log.info(
                    "[ONCHAIN-SYNC] run finished (smartContractId={}, updateChainId={})",
                    isBlank(smartContractId) ? "n/a" : smartContractId,
                    updateChainId
            );
        } catch (Exception ex) {
            log.error("[ONCHAIN-SYNC] run failed", ex);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

