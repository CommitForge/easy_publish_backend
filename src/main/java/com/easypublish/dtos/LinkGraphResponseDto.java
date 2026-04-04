package com.easypublish.dtos;

import java.util.ArrayList;
import java.util.List;

public class LinkGraphResponseDto {

    private List<LinkGraphNodeDto> nodes = new ArrayList<>();
    private List<LinkGraphEdgeDto> edges = new ArrayList<>();
    private String info;

    public LinkGraphResponseDto() {
    }

    public LinkGraphResponseDto(
            List<LinkGraphNodeDto> nodes,
            List<LinkGraphEdgeDto> edges,
            String info
    ) {
        this.nodes = nodes != null ? nodes : new ArrayList<>();
        this.edges = edges != null ? edges : new ArrayList<>();
        this.info = info;
    }

    public List<LinkGraphNodeDto> getNodes() {
        return nodes;
    }

    public void setNodes(List<LinkGraphNodeDto> nodes) {
        this.nodes = nodes != null ? nodes : new ArrayList<>();
    }

    public List<LinkGraphEdgeDto> getEdges() {
        return edges;
    }

    public void setEdges(List<LinkGraphEdgeDto> edges) {
        this.edges = edges != null ? edges : new ArrayList<>();
    }

    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info;
    }
}
