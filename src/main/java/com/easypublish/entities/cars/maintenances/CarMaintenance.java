package com.easypublish.entities.cars.maintenances;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "parsed_car_maintenance")
public class CarMaintenance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_item_id", columnDefinition = "TEXT")
    private String dataItemId;

    @Column(name = "parsed_date", columnDefinition = "TEXT")
    private LocalDate parsedDate;
    @Column(name = "date", columnDefinition = "TEXT")
    private String date;

    @Column(name = "distance", columnDefinition = "TEXT")
    private String distance;

    @Column(name = "service", columnDefinition = "TEXT")
    private String service;

    @Column(name = "cost", columnDefinition = "TEXT")
    private String cost;

    @Column(name = "parts", columnDefinition = "TEXT")
    private String parts;

    @Column(name = "performed_by", columnDefinition = "TEXT")
    private String performedBy;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    public CarMaintenance() {}

    public CarMaintenance(String dataItemId,
                          String date,
                          LocalDate parsedDate,
                          String distance,
                          String service,
                          String cost,
                          String parts,
                          String performedBy,
                          String note) {
        this.dataItemId = dataItemId;
        this.date = date;
        this.parsedDate = parsedDate;
        this.distance = distance;
        this.service = service;
        this.cost = cost;
        this.parts = parts;
        this.performedBy = performedBy;
        this.note = note;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDataItemId() { return dataItemId; }
    public void setDataItemId(String dataItemId) { this.dataItemId = dataItemId; }

    public LocalDate getParsedDate() { return parsedDate; }
    public void setParsedDate(LocalDate parsedDate) { this.parsedDate = parsedDate; }

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

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }
}
