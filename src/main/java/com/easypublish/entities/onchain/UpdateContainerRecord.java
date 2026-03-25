package com.easypublish.entities.onchain;

import jakarta.persistence.*;
import java.math.BigInteger;

@Entity
@Table(name = "onchain_update_container_record")
public class UpdateContainerRecord {

    @Id
    @Column(name = "id", nullable = false, length = 256)
    private String id;

    @Column(name = "container_id")
    private String containerId;

    @Column(name = "object_id")
    private String objectId;

    @Embedded
    private Creator creator;

    @Column(name = "object_type", columnDefinition = "TEXT")
    private String objectType;

    /**
     * 1 = CREATE
     * 2 = UPDATE
     */
    @Column(name = "action")
    private BigInteger action;

    @Column(name = "sequence_index")
    private BigInteger sequenceIndex;

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

    public String getObjectId() {
        return objectId;
    }

    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }

    public Creator getCreator() {
        return creator;
    }

    public void setCreator(Creator creator) {
        this.creator = creator;
    }

    public String getObjectType() {
        return objectType;
    }

    public void setObjectType(String objectType) {
        this.objectType = objectType;
    }

    public BigInteger getAction() {
        return action;
    }

    public void setAction(BigInteger action) {
        this.action = action;
    }

    public BigInteger getSequenceIndex() {
        return sequenceIndex;
    }

    public void setSequenceIndex(BigInteger sequenceIndex) {
        this.sequenceIndex = sequenceIndex;
    }

    public String getPrevId() {
        return prevId;
    }

    public void setPrevId(String prevId) {
        this.prevId = prevId;
    }
}
