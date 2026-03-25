package com.easypublish.entities.sync;

import jakarta.persistence.*;

import java.math.BigInteger;
import java.time.Instant;

@Entity
@Table(name = "sync_progress")
public class SyncProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "last_sequence_index")
    private BigInteger lastSequenceIndex;

    @Column(name = "last_sync_ts")
    private Instant lastSyncTs;

    @Column(name = "chain_object_id", columnDefinition = "TEXT")
    private String chainObjectId;

    @Column(name = "chain_type", columnDefinition = "TEXT")
    private String chainType;

    @Column(name = "last_synced_object_id", columnDefinition = "TEXT")
    private String lastSyncedObjectId;

    @Column(name = "last_sync_error")
    private Boolean lastSyncError; // true if last sync failed

    // Getters / Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public BigInteger getLastSequenceIndex() { return lastSequenceIndex; }
    public void setLastSequenceIndex(BigInteger lastSequenceIndex) { this.lastSequenceIndex = lastSequenceIndex; }

    public Instant getLastSyncTs() { return lastSyncTs; }
    public void setLastSyncTs(Instant lastSyncTs) { this.lastSyncTs = lastSyncTs; }

    public String getChainObjectId() { return chainObjectId; }
    public void setChainObjectId(String chainObjectId) { this.chainObjectId = chainObjectId; }

    public String getChainType() { return chainType; }
    public void setChainType(String chainType) { this.chainType = chainType; }

    public String getLastSyncedObjectId() { return lastSyncedObjectId; }
    public void setLastSyncedObjectId(String lastSyncedObjectId) { this.lastSyncedObjectId = lastSyncedObjectId; }

    public Boolean getLastSyncError() { return lastSyncError; }
    public void setLastSyncError(Boolean lastSyncError) { this.lastSyncError = lastSyncError; }
}
