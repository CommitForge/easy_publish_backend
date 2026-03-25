package com.easypublish.dtos;

import com.easypublish.entities.onchain.DataItem;
import com.easypublish.entities.onchain.DataType;

import java.util.List;

public class DataTypeNodeDto {
    DataType dataType;
    List<DataItemNodeDto> dataItems;

    public DataTypeNodeDto(DataType dataType, List<DataItemNodeDto> dataItems) {
        this.dataType = dataType;
        this.dataItems = dataItems;
    }

    public DataType getDataType() {
        return dataType;
    }

    public void setDataType(DataType dataType) {
        this.dataType = dataType;
    }

    public List<DataItemNodeDto> getDataItems() {
        return dataItems;
    }

    public void setDataItems(List<DataItemNodeDto> dataItems) {
        this.dataItems = dataItems;
    }
}
