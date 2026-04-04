package com.easypublish.dtos;

public class LinkGraphEdgeDto {

    private String from;
    private String to;
    private String relation;

    public LinkGraphEdgeDto() {
    }

    public LinkGraphEdgeDto(String from, String to, String relation) {
        this.from = from;
        this.to = to;
        this.relation = relation;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getRelation() {
        return relation;
    }

    public void setRelation(String relation) {
        this.relation = relation;
    }
}
