package com.easypublish.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EasyPublishOffchainIndexScheduler {

    private static final Logger log = LoggerFactory.getLogger(EasyPublishOffchainIndexScheduler.class);

    private final EasyPublishOffchainIndexService easyPublishOffchainIndexService;

    @Value("${app.easy-publish.offchain-index-enabled:true}")
    private boolean enabled;

    public EasyPublishOffchainIndexScheduler(EasyPublishOffchainIndexService easyPublishOffchainIndexService) {
        this.easyPublishOffchainIndexService = easyPublishOffchainIndexService;
    }

    @Scheduled(
            fixedDelayString = "${app.easy-publish.offchain-index-ms:60000}",
            initialDelayString = "${app.easy-publish.offchain-index-initial-delay-ms:15000}"
    )
    public void scheduledReindex() {
        if (!enabled) {
            return;
        }

        EasyPublishOffchainIndexService.IndexSummary summary = easyPublishOffchainIndexService.reindexAllDataItems();
        if (summary.skipped()) {
            log.info("[OFFCHAIN-INDEX] skipped: {}", summary.message());
            return;
        }

        log.info(
                "[OFFCHAIN-INDEX] scanned={}, followActions={}, activeFollows={}, revisions={}, maintenances={}",
                summary.scannedDataItems(),
                summary.followActions(),
                summary.activeFollows(),
                summary.revisionRows(),
                summary.maintenanceRows()
        );
    }
}
