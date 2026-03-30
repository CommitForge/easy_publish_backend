package com.easypublish.dtos;

import com.easypublish.entities.onchain.DataItem;
import com.easypublish.entities.onchain.DataItemVerification;

import java.util.List;

public class DataItemNodeDto {

    private final DataItem dataItem;
    private final List<DataItemVerification> dataItemVerifications;
    private final DataItemRevisionDto revision;

    public DataItemNodeDto(
            DataItem dataItem,
            List<DataItemVerification> dataItemVerifications,
            DataItemRevisionDto revision
    ) {
        this.dataItem = dataItem;
        this.dataItemVerifications = dataItemVerifications;
        this.revision = revision;
    }

    public DataItem getDataItem() {
        return dataItem;
    }

    public List<DataItemVerification> getDataItemVerifications() {
        return dataItemVerifications;
    }

    public DataItemRevisionDto getRevision() {
        return revision;
    }
}
