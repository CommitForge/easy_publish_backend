package com.easypublish.entities.onchain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ContainerPermission {

    @Column(name = "public_update_container")
    private boolean publicUpdateContainer;

    @Column(name = "public_attach_container_child")
    private boolean publicAttachContainerChild;

    @Column(name = "public_create_data_type")
    private boolean publicCreateDataType;

    @Column(name = "public_publish_data_item")
    private boolean publicPublishDataItem;

    public boolean isPublicUpdateContainer() {
        return publicUpdateContainer;
    }

    public void setPublicUpdateContainer(boolean publicUpdateContainer) {
        this.publicUpdateContainer = publicUpdateContainer;
    }

    public boolean isPublicAttachContainerChild() {
        return publicAttachContainerChild;
    }

    public void setPublicAttachContainerChild(boolean publicAttachContainerChild) {
        this.publicAttachContainerChild = publicAttachContainerChild;
    }

    public boolean isPublicCreateDataType() {
        return publicCreateDataType;
    }

    public void setPublicCreateDataType(boolean publicCreateDataType) {
        this.publicCreateDataType = publicCreateDataType;
    }

    public boolean isPublicPublishDataItem() {
        return publicPublishDataItem;
    }

    public void setPublicPublishDataItem(boolean publicPublishDataItem) {
        this.publicPublishDataItem = publicPublishDataItem;
    }

// getters / setters
}
