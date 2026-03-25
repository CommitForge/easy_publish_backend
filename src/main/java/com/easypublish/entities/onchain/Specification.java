package com.easypublish.entities.onchain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class Specification {

    @Column(name = "spec_version", columnDefinition = "TEXT")
    private String version;


    @Column(name = "spec_schemas", columnDefinition = "TEXT")
    private String schemas;

    @Column(name = "spec_apis", columnDefinition = "TEXT")
    private String apis;

    @Column(name = "spec_resources", columnDefinition = "TEXT")
    private String resources;

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getSchemas() {
        return schemas;
    }

    public void setSchemas(String schemas) {
        this.schemas = schemas;
    }

    public String getApis() {
        return apis;
    }

    public void setApis(String apis) {
        this.apis = apis;
    }

    public String getResources() {
        return resources;
    }

    public void setResources(String resources) {
        this.resources = resources;
    }

// getters / setters
}
