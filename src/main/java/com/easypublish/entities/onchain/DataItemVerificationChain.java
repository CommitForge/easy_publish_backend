package com.easypublish.entities.onchain;

import jakarta.persistence.*;

import java.math.BigInteger;
import java.time.Instant;

@Entity
@Table(name = "onchain_data_item_verification_chain")
public class DataItemVerificationChain {

    @Id
    @Column(length = 66) // assuming UID / on-chain ID length
    private String id;

    @Column
    private BigInteger lastDataItemVerificationIndex; // <-- default
    @Column
    private String lastDataItemVerificationId;

    @Column
    private Instant lastSyncTs;

    public DataItemVerificationChain(String updateChainId) {
        this.id = updateChainId;
    }

    public DataItemVerificationChain() {
    }
    // --- Getters / Setters ---

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public BigInteger getLastDataItemVerificationIndex() {
        return lastDataItemVerificationIndex;
    }

    public void setLastDataItemVerificationIndex(BigInteger lastDataItemVerificationIndex) {
        this.lastDataItemVerificationIndex = lastDataItemVerificationIndex;
    }

    public String getLastDataItemVerificationId() {
        return lastDataItemVerificationId;
    }

    public void setLastDataItemVerificationId(String lastDataItemVerificationId) {
        this.lastDataItemVerificationId = lastDataItemVerificationId;
    }

    public Instant getLastSyncTs() {
        return lastSyncTs;
    }

    public void setLastSyncTs(Instant lastSyncTs) {
        this.lastSyncTs = lastSyncTs;
    }
}
