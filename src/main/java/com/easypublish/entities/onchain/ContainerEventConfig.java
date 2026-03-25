package com.easypublish.entities.onchain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ContainerEventConfig {

    @Column(name = "event_create")
    private boolean eventCreate;

    @Column(name = "event_publish")
    private boolean eventPublish;

    @Column(name = "event_attach")
    private boolean eventAttach;

    @Column(name = "event_add")
    private boolean eventAdd;

    @Column(name = "event_remove")
    private boolean eventRemove;

    @Column(name = "event_update")
    private boolean eventUpdate;

    public boolean isEventCreate() {
        return eventCreate;
    }

    public void setEventCreate(boolean eventCreate) {
        this.eventCreate = eventCreate;
    }

    public boolean isEventPublish() {
        return eventPublish;
    }

    public void setEventPublish(boolean eventPublish) {
        this.eventPublish = eventPublish;
    }

    public boolean isEventAttach() {
        return eventAttach;
    }

    public void setEventAttach(boolean eventAttach) {
        this.eventAttach = eventAttach;
    }

    public boolean isEventAdd() {
        return eventAdd;
    }

    public void setEventAdd(boolean eventAdd) {
        this.eventAdd = eventAdd;
    }

    public boolean isEventRemove() {
        return eventRemove;
    }

    public void setEventRemove(boolean eventRemove) {
        this.eventRemove = eventRemove;
    }

    public boolean isEventUpdate() {
        return eventUpdate;
    }

    public void setEventUpdate(boolean eventUpdate) {
        this.eventUpdate = eventUpdate;
    }

// getters / setters
}
