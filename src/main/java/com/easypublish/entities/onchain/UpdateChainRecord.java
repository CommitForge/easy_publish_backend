package com.easypublish.entities.onchain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigInteger;

@Entity
@Table(name = "onchain_update_chain_record")
public class UpdateChainRecord implements Serializable {

    @Id
    @Column(name = "id", nullable = false, length = 256)
    private String id;

    @Column(name = "object_id", length = 256, nullable = false)
    private String objectId;

    @Embedded
    private Creator creator;

    @Column(name = "object_type", columnDefinition = "TEXT")
    private String objectType;

    /**
     * Action type: 1 = CREATE, 2 = UPDATE
     */
    @Column(name = "action")
    private BigInteger action;

    /**
     * Sequence index within the chain
     */
    @Column(name = "sequence_index")
    private BigInteger sequenceIndex;

    /**
     * Previous record in the chain
     */
    @Column(name = "prev_id", length = 256)
    private String prevId;

    public UpdateChainRecord() {}

    // -------------------------
    // Getters and Setters
    // -------------------------

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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
