package com.easypublish.dtos;

import java.util.ArrayList;
import java.util.List;

public class LinkGraphRequestDto {

    private String mode;
    private String sourceType;
    private String sourceContainerId;
    private String sourceDataItemId;
    private List<String> seeds = new ArrayList<>();
    private Integer maxDepth;
    private Integer maxNodes;
    private Boolean preventCycles;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceContainerId() {
        return sourceContainerId;
    }

    public void setSourceContainerId(String sourceContainerId) {
        this.sourceContainerId = sourceContainerId;
    }

    public String getSourceDataItemId() {
        return sourceDataItemId;
    }

    public void setSourceDataItemId(String sourceDataItemId) {
        this.sourceDataItemId = sourceDataItemId;
    }

    public List<String> getSeeds() {
        return seeds;
    }

    public void setSeeds(List<String> seeds) {
        this.seeds = seeds != null ? seeds : new ArrayList<>();
    }

    public Integer getMaxDepth() {
        return maxDepth;
    }

    public void setMaxDepth(Integer maxDepth) {
        this.maxDepth = maxDepth;
    }

    public Integer getMaxNodes() {
        return maxNodes;
    }

    public void setMaxNodes(Integer maxNodes) {
        this.maxNodes = maxNodes;
    }

    public Boolean getPreventCycles() {
        return preventCycles;
    }

    public void setPreventCycles(Boolean preventCycles) {
        this.preventCycles = preventCycles;
    }
}
