package com.easypublish.parsed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Cars {

    @JsonProperty("maintenances")
    private List<CarMaintenance> maintenances;

    public Cars() {}

    public List<CarMaintenance> getMaintenances() {
        return maintenances;
    }

    public void setMaintenances(List<CarMaintenance> maintenances) {
        this.maintenances = maintenances;
    }
}