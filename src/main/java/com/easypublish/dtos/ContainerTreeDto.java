package com.easypublish.dtos;

import java.util.List;
import java.util.Map;

public class ContainerTreeDto {

    private List<ContainerNodeDto> containers;
    private Map<String, Object> meta;

    public ContainerTreeDto(List<ContainerNodeDto> containers) {
        this(containers, Map.of());
    }

    public ContainerTreeDto(List<ContainerNodeDto> containers, Map<String, Object> meta) {
        this.containers = containers;
        this.meta = meta;
    }

    public List<ContainerNodeDto> getContainers() {
        return containers;
    }

    public void setContainers(List<ContainerNodeDto> containers) {
        this.containers = containers;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }

    public void setMeta(Map<String, Object> meta) {
        this.meta = meta;
    }
}
