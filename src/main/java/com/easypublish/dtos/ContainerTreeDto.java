package com.easypublish.dtos;

import com.easypublish.entities.onchain.Container;
import com.easypublish.entities.onchain.DataItem;
import com.easypublish.entities.onchain.DataType;


import java.util.List;

public class ContainerTreeDto{

    List<ContainerNodeDto> containers;

    public ContainerTreeDto(List<ContainerNodeDto> containers) {
        this.containers = containers;

    }

    public List<ContainerNodeDto> getContainers() {
        return containers;
    }

    public void setContainers(List<ContainerNodeDto> containers) {
        this.containers = containers;
    }
}
