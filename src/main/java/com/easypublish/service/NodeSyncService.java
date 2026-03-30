package com.easypublish.service;

import com.easypublish.batch.IotaDataSync;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.locks.ReentrantLock;

@Service
public class NodeSyncService {

    private static final Logger log = LoggerFactory.getLogger(NodeSyncService.class);

    private final IotaDataSync iotaDataSync;
    private final ReentrantLock syncLock = new ReentrantLock(true);

    public NodeSyncService(IotaDataSync iotaDataSync) {
        this.iotaDataSync = iotaDataSync;
    }

    public void syncUpdateChainRecursively(
            String containerChainId,
            String updateChainId,
            String dataItemChainId,
            String dataItemVerificationChainId
    ) throws Exception {
        boolean acquiredImmediately = syncLock.tryLock();
        if (!acquiredImmediately) {
            log.info("[ONCHAIN-SYNC] sync already running; waiting for current run to finish.");
            syncLock.lock();
        }

        try {
            log.info(
                    "[ONCHAIN-SYNC] start (containerChainId={}, updateChainId={}, dataItemChainId={}, dataItemVerificationChainId={})",
                    containerChainId,
                    updateChainId,
                    dataItemChainId,
                    dataItemVerificationChainId
            );
            iotaDataSync.syncUpdateChainRecords(
                    containerChainId,
                    updateChainId,
                    dataItemChainId,
                    dataItemVerificationChainId
            );
            log.info("[ONCHAIN-SYNC] completed successfully.");
        } finally {
            syncLock.unlock();
        }
    }

    public boolean isSyncRunning() {
        return syncLock.isLocked();
    }

}
