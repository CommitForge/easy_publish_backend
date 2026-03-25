package com.easypublish.dtos;

import java.math.BigInteger;

public class UserDataDTO {
    private String address;
    private BigInteger firstSeenAtMs;
    private BigInteger lastSeenAtMs;

    public UserDataDTO(String address, BigInteger firstSeenAtMs, BigInteger lastSeenAtMs) {
        this.address = address;
        this.firstSeenAtMs = firstSeenAtMs;
        this.lastSeenAtMs = lastSeenAtMs;
    }

    // getters / setters
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public BigInteger getFirstSeenAtMs() { return firstSeenAtMs; }
    public void setFirstSeenAtMs(BigInteger firstSeenAtMs) { this.firstSeenAtMs = firstSeenAtMs; }
    public BigInteger getLastSeenAtMs() { return lastSeenAtMs; }
    public void setLastSeenAtMs(BigInteger lastSeenAtMs) { this.lastSeenAtMs = lastSeenAtMs; }
}
