package com.easypublish.dtos;

import com.easypublish.entities.onchain.Container;
import com.easypublish.entities.onchain.DataItem;
import com.easypublish.entities.onchain.DataType;

import java.util.List;

public class ContainerNodeDto {

    Container container;
    List<DataTypeNodeDto> dataTypes;

    public ContainerNodeDto(Container container, List<DataTypeNodeDto> dataTypes) {
        this.container = container;
        this.dataTypes = dataTypes;
    }

    public Container getContainer() {
        return container;
    }

    public void setContainer(Container container) {
        this.container = container;
    }

    public List<DataTypeNodeDto> getDataTypes() {
        return dataTypes;
    }

    public void setDataTypes(List<DataTypeNodeDto> dataTypes) {
        this.dataTypes = dataTypes;
    }
}
