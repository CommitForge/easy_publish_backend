package com.easypublish.entities.onchain;

import jakarta.persistence.*;

import java.math.BigInteger;

@Entity
@Table(name = "onchain_data_type")
public class DataType {

    @Id
    @Column(name = "id", nullable = false, length = 256)
    private String id;

    @Column(name = "container_id")
    private String containerId;

    @Column(name = "external_id", columnDefinition = "TEXT")
    private String externalId;

    @Embedded
    private Creator creator;

    @Column(name = "name", columnDefinition = "TEXT")
    private String name;
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Embedded
    private Specification specification;

    @Column(name = "sequence_index")
    private BigInteger sequenceIndex;

    @Column(name = "external_index")
    private BigInteger externalIndex;

    @Column(name = "last_data_item_id")
    private String lastDataItemId;

    @Column(name = "prev_id")
    private String prevId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContainerId() {
        return containerId;
    }

    public void setContainerId(String containerId) {
        this.containerId = containerId;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public Creator getCreator() {
        return creator;
    }

    public void setCreator(Creator creator) {
        this.creator = creator;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Specification getSpecification() {
        return specification;
    }

    public void setSpecification(Specification specification) {
        this.specification = specification;
    }

    public BigInteger getSequenceIndex() {
        return sequenceIndex;
    }

    public void setSequenceIndex(BigInteger sequenceIndex) {
        this.sequenceIndex = sequenceIndex;
    }

    public BigInteger getExternalIndex() {
        return externalIndex;
    }

    public void setExternalIndex(BigInteger externalIndex) {
        this.externalIndex = externalIndex;
    }

    public String getLastDataItemId() {
        return lastDataItemId;
    }

    public void setLastDataItemId(String lastDataItemId) {
        this.lastDataItemId = lastDataItemId;
    }

    public String getPrevId() {
        return prevId;
    }

    public void setPrevId(String prevId) {
        this.prevId = prevId;
    }

// getters / setters
}
