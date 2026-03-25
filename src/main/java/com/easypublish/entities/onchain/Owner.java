package com.easypublish.entities.onchain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.math.BigInteger;

@Entity
@Table(name = "onchain_owner")
public class Owner {

    @Id
    @Column(name = "id", nullable = false, length = 256)
    private String id;

    @Embedded
    private Creator creator;

    @Column(name = "addr")
    private String addr;

    @Column(name = "role", columnDefinition = "TEXT")
    private String role;
    private boolean removed;

    @Column(name = "sequence_index")
    private BigInteger sequenceIndex;

    @Column(name = "prev_id")
    private String prevId;

    @ManyToOne
    @JoinColumn(name = "container_id")
    private Container container;

    public Container getContainer() {
        return container;
    }

    public void setContainer(Container container) {
        this.container = container;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Creator getCreator() {
        return creator;
    }

    public void setCreator(Creator creator) {
        this.creator = creator;
    }

    public String getAddr() {
        return addr;
    }

    public void setAddr(String addr) {
        this.addr = addr;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isRemoved() {
        return removed;
    }

    public void setRemoved(boolean removed) {
        this.removed = removed;
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

// getters / setters
}
