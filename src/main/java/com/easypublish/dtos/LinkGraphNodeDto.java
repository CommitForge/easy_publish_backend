package com.easypublish.dtos;

public class LinkGraphNodeDto {

    private String id;
    private String label;
    private int level;
    private String kind;

    public LinkGraphNodeDto() {
    }

    public LinkGraphNodeDto(String id, String label, int level, String kind) {
        this.id = id;
        this.label = label;
        this.level = level;
        this.kind = kind;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }
}
