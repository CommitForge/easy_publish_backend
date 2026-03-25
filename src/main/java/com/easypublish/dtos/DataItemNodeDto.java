package com.easypublish.dtos;

import com.easypublish.entities.onchain.DataItem;
import com.easypublish.entities.onchain.DataItemVerification;

import java.util.List;

public class DataItemNodeDto {

    private final DataItem dataItem;
    private final List<DataItemVerification> verifications;

    public DataItemNodeDto(DataItem dataItem,
                           List<DataItemVerification> verifications) {
        this.dataItem = dataItem;
        this.verifications = verifications;
    }

    public DataItem getDataItem() {
        return dataItem;
    }

    public List<DataItemVerification> getVerifications() {
        return verifications;
    }
}