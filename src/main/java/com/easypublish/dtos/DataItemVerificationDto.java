package com.easypublish.dtos;

import com.easypublish.entities.onchain.DataItemVerification;

import java.math.BigInteger;

public class DataItemVerificationDto {

    private final String id;
    private final boolean verified;
    private final String name;
    private final String description;
    private final BigInteger sequenceIndex;

    public DataItemVerificationDto(DataItemVerification v) {
        this.id = v.getId();
        this.verified = Boolean.TRUE.equals(v.getVerified());
        this.name = v.getName();
        this.description = v.getDescription();
        this.sequenceIndex = v.getSequenceIndex();
    }

    public String getId() {
        return id;
    }

    public boolean isVerified() {
        return verified;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigInteger getSequenceIndex() {
        return sequenceIndex;
    }
}
