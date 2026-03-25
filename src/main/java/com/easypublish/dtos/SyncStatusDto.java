package com.easypublish.dtos;

import java.math.BigInteger;
import java.time.Instant;

public class SyncStatusDto {

    private BigInteger lastSequenceIndex;
    private Instant lastSyncTs;
    private Instant nextSyncTs;
    private Boolean lastSyncError;

    // NEW
    private BigInteger onchainLastDataItemIndex;
    private String onchainLastDataItemId;

    public SyncStatusDto(
            BigInteger lastSequenceIndex,
            Instant lastSyncTs,
            Instant nextSyncTs,
            Boolean lastSyncError,
            BigInteger onchainLastDataItemIndex,
            String onchainLastDataItemId
    ) {
        this.lastSequenceIndex = lastSequenceIndex;
        this.lastSyncTs = lastSyncTs;
        this.nextSyncTs = nextSyncTs;
        this.lastSyncError = lastSyncError;
        this.onchainLastDataItemIndex = onchainLastDataItemIndex;
        this.onchainLastDataItemId = onchainLastDataItemId;
    }

    public BigInteger getLastSequenceIndex() { return lastSequenceIndex; }
    public Instant getLastSyncTs() { return lastSyncTs; }
    public Instant getNextSyncTs() { return nextSyncTs; }
    public Boolean getLastSyncError() { return lastSyncError; }

    public BigInteger getOnchainLastDataItemIndex() { return onchainLastDataItemIndex; }
    public String getOnchainLastDataItemId() { return onchainLastDataItemId; }

    public void setLastSequenceIndex(BigInteger lastSequenceIndex) {
        this.lastSequenceIndex = lastSequenceIndex;
    }

    public void setLastSyncTs(Instant lastSyncTs) {
        this.lastSyncTs = lastSyncTs;
    }

    public void setNextSyncTs(Instant nextSyncTs) {
        this.nextSyncTs = nextSyncTs;
    }

    public void setLastSyncError(Boolean lastSyncError) {
        this.lastSyncError = lastSyncError;
    }

    public void setOnchainLastDataItemIndex(BigInteger onchainLastDataItemIndex) {
        this.onchainLastDataItemIndex = onchainLastDataItemIndex;
    }

    public void setOnchainLastDataItemId(String onchainLastDataItemId) {
        this.onchainLastDataItemId = onchainLastDataItemId;
    }
}
