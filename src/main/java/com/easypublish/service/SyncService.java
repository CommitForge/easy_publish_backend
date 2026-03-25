package com.easypublish.service;


import com.easypublish.dtos.SyncStatusDto;
import com.easypublish.entities.sync.SyncProgress;
import com.easypublish.entities.onchain.DataItemChain;
import com.easypublish.repositories.DataItemChainRepository;
import com.easypublish.repositories.SyncProgressRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.Instant;
import java.time.Duration;
import java.util.Optional;

@Service
public class SyncService {
    @Value("${sync.chain.last}")
    private String lastSyncChain;

    @Value("${sync.chain.DataItem}")
    private String dataItemChain;
    private final SyncProgressRepository repository;

    private final DataItemChainRepository dataItemChainRepository;

    private static Instant previousSync = null;
    private static Instant nextSyncCache = null; // <-- keep nextSync across calls


    public SyncService(SyncProgressRepository repository, DataItemChainRepository dataItemChainRepository) {
        this.repository = repository;
        this.dataItemChainRepository = dataItemChainRepository;
    }

    public SyncStatusDto getSyncStatus(String chainObjectId) {
        SyncProgress lastSync = repository
                .findTopByChainObjectIdOrderByLastSyncTsDesc(lastSyncChain)
                .orElse(null);

        Optional<DataItemChain> chainOpt = dataItemChainRepository.findById(dataItemChain);

        BigInteger backendIndex = BigInteger.ZERO;
        Instant lastSyncTs = null;
        Instant nextSync = nextSyncCache; // start with cached value
        Boolean error = false;

        BigInteger onchainIndex = null;
        String onchainLastId = null;

        if (chainOpt.isPresent()) {
            DataItemChain chain = chainOpt.get();
            onchainIndex = chain.getLastDataItemIndex();
            onchainLastId = chain.getLastDataItemId();

            if (lastSync != null) {
                backendIndex = lastSync.getLastSequenceIndex() != null
                        ? lastSync.getLastSequenceIndex()
                        : BigInteger.ZERO;
                lastSyncTs = lastSync.getLastSyncTs();
                error = lastSync.getLastSyncError();

                if (previousSync != null) {
                    // compute next sync based on last completed interval
                    Duration diff = Duration.between(previousSync, lastSyncTs);
                    if (diff.toMillis() > 0) {
                        nextSync = lastSyncTs.plus(diff);
                        nextSyncCache = nextSync; // update cache only if diff > 0
                    }
                } else {
                    // first real run
                    nextSync = lastSyncTs.plusSeconds(10); // default interval
                    nextSyncCache = nextSync;
                }

                previousSync = lastSyncTs;
            } else {
                // No previous sync exists yet
                lastSyncTs = Instant.now();
                if (nextSyncCache == null) {
                    nextSync = lastSyncTs.plusSeconds(10);
                    nextSyncCache = nextSync;
                } else {
                    nextSync = nextSyncCache; // keep previous cached value
                }
            }
        }

        return new SyncStatusDto(
                backendIndex,
                lastSyncTs,
                nextSync,
                error,
                onchainIndex,
                onchainLastId
        );
    }
}
