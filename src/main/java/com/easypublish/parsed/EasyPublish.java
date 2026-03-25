package com.easypublish.parsed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EasyPublish {

    private PublishWrapper publishWrapper;

    public EasyPublish() {}

    @JsonProperty("easy_publish")
    public PublishWrapper getPublishWrapper() {
        return publishWrapper;
    }

    @JsonProperty("easy_publish")
    public void setPublishWrapper(PublishWrapper publishWrapper) {
        this.publishWrapper = publishWrapper;
    }
}