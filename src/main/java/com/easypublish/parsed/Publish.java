package com.easypublish.parsed;

import com.easypublish.entities.publish.PublishTarget;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Publish {

    @JsonProperty("targets")
    private List<PublishTarget> targets;

    public Publish() {}

    public List<PublishTarget> getTargets() {
        return targets;
    }

    public void setTargets(List<PublishTarget> targets) {
        this.targets = targets;
    }
}