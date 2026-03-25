package com.easypublish.entities.onchain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigInteger;

@Embeddable
public class Creator {

    @Column(name = "creator_addr", columnDefinition = "TEXT")
    private String creatorAddr;

    @Column(name = "creator_update_addr", columnDefinition = "TEXT")
    private String creatorUpdateAddr;

    @Column(name = "creator_timestamp_ms")
    private BigInteger creatorTimestampMs;

    @Column(name = "creator_update_timestamp_ms")
    private BigInteger creatorUpdateTimestampMs;

    public String getCreatorAddr() {
        return creatorAddr;
    }

    public void setCreatorAddr(String creatorAddr) {
        this.creatorAddr = creatorAddr;
    }

    public String getCreatorUpdateAddr() {
        return creatorUpdateAddr;
    }

    public void setCreatorUpdateAddr(String creatorUpdateAddr) {
        this.creatorUpdateAddr = creatorUpdateAddr;
    }

    public BigInteger getCreatorTimestampMs() {
        return creatorTimestampMs;
    }

    public void setCreatorTimestampMs(BigInteger creatorTimestampMs) {
        this.creatorTimestampMs = creatorTimestampMs;
    }

    public BigInteger getCreatorUpdateTimestampMs() {
        return creatorUpdateTimestampMs;
    }

    public void setCreatorUpdateTimestampMs(BigInteger creatorUpdateTimestampMs) {
        this.creatorUpdateTimestampMs = creatorUpdateTimestampMs;
    }

// getters / setters
}
