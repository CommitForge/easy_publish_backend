package com.easypublish.entities.onchain;

import jakarta.persistence.*;

@Entity
@Table(name = "owner_audit")
public class OwnerAudit {

    @Id
    @Column(name = "id", nullable = false, unique = true)
    private String id; // UID

    @Column(name = "object_id", nullable = false)
    private String objectId; // ID

    @Column(name = "container_id", nullable = false)
    private String containerId; // ID

    @Embedded
    private Creator creator;

    @Column(name = "addr", nullable = false)
    private String addr; // address

    @Column(name = "role", columnDefinition = "TEXT")
    private String role;

    @Column(name = "removed")
    private boolean removed;

    // ============================
    // Getters and setters
    // ============================

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

    public String getContainerId() {
        return containerId;
    }

    public void setContainerId(String containerId) {
        this.containerId = containerId;
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
}
