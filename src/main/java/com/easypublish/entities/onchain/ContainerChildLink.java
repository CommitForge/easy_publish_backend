package com.easypublish.entities.onchain;

import jakarta.persistence.*;

import java.math.BigInteger;

@Entity
@Table(name = "onchain_container_child_link")
public class ContainerChildLink {

    @Id
    @Column(name = "id", nullable = false, length = 256)
    private String id;

    @Column(name = "container_parent_id")
    private String containerParentId;

    @Column(name = "container_child_id")
    private String containerChildId;

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

    @Column(name = "sequence_index")
    private BigInteger sequenceIndex;

    @Column(name = "external_index")
    private BigInteger externalIndex;

    @Column(name = "prev_id")
    private String prevId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContainerParentId() {
        return containerParentId;
    }

    public void setContainerParentId(String containerParentId) {
        this.containerParentId = containerParentId;
    }

    public String getContainerChildId() {
        return containerChildId;
    }

    public void setContainerChildId(String containerChildId) {
        this.containerChildId = containerChildId;
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

    public String getPrevId() {
        return prevId;
    }

    public void setPrevId(String prevId) {
        this.prevId = prevId;
    }

// getters / setters
}
