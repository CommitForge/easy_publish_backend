package com.easypublish.parsed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CarMaintenance {

    private String date;
    private String distance;
    private String service;
    private String cost;
    private String parts;

    @JsonProperty("performed_by")
    private String performedBy;

    private String note;

    public CarMaintenance() {}

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getDistance() { return distance; }
    public void setDistance(String distance) { this.distance = distance; }

    public String getService() { return service; }
    public void setService(String service) { this.service = service; }

    public String getCost() { return cost; }
    public void setCost(String cost) { this.cost = cost; }

    public String getParts() { return parts; }
    public void setParts(String parts) { this.parts = parts; }

    public String getPerformedBy() { return performedBy; }
    public void setPerformedBy(String performedBy) { this.performedBy = performedBy; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}