package com.easypublish.entities.onchain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "onchain_container")
public class Container {

    @Id
    @Column(name = "id", nullable = false, length = 256)
    private String id;

    @Column(name = "external_id", columnDefinition = "TEXT")
    private String externalId;

    @Embedded
    private Creator creator;

    // This maps your owners vector
    // Map the owners inside the container
    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "container_id", referencedColumnName = "id")
    @JsonIgnore
    private List<Owner> owners = new ArrayList<>();

    @Column(name = "owners_active_count")
    private BigInteger ownersActiveCount;

    @Column(name="name", columnDefinition = "TEXT")
    private String name;

    @Column(name="description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Embedded
    private Specification specification;

    @Embedded
    private ContainerPermission permission;

    @Embedded
    private ContainerEventConfig eventConfig;

    @Column(name = "sequence_index")
    private BigInteger sequenceIndex;

    @Column(name = "external_index")
    private BigInteger externalIndex;

    @Column(name = "last_owner_index")
    private BigInteger lastOwnerIndex;

    @Column(name = "last_container_child_index")
    private BigInteger lastContainerChildIndex;

    @Column(name = "last_data_type_index")
    private BigInteger lastDataTypeIndex;

    @Column(name = "last_data_item_index")
    private BigInteger lastDataItemIndex;

    @Column(name = "last_owner_id")
    private String lastOwnerId;

    @Column(name = "last_container_child_id")
    private String lastContainerChildId;

    @Column(name = "last_data_type_id")
    private String lastDataTypeId;

    @Column(name = "last_data_item_id")
    private String lastDataItemId;

    @Column(name = "prev_container_chain_id")
    private String prevContainerChainId;

    @Column(name = "last_update_record_index")
    private BigInteger lastUpdateRecordIndex;

    @Column(name = "last_update_record_id")
    private String lastUpdateRecordId;


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public List<Owner> getOwners() {
        return owners;
    }

    public void setOwners(List<Owner> owners) {
        this.owners = owners != null ? owners : new ArrayList<>();
    }

    public BigInteger getOwnersActiveCount() {
        return ownersActiveCount;
    }

    public void setOwnersActiveCount(BigInteger ownersActiveCount) {
        this.ownersActiveCount = ownersActiveCount;
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

    public ContainerPermission getPermission() {
        return permission;
    }

    public void setPermission(ContainerPermission permission) {
        this.permission = permission;
    }

    public ContainerEventConfig getEventConfig() {
        return eventConfig;
    }

    public void setEventConfig(ContainerEventConfig eventConfig) {
        this.eventConfig = eventConfig;
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

    public BigInteger getLastOwnerIndex() {
        return lastOwnerIndex;
    }

    public void setLastOwnerIndex(BigInteger lastOwnerIndex) {
        this.lastOwnerIndex = lastOwnerIndex;
    }

    public BigInteger getLastContainerChildIndex() {
        return lastContainerChildIndex;
    }

    public void setLastContainerChildIndex(BigInteger lastContainerChildIndex) {
        this.lastContainerChildIndex = lastContainerChildIndex;
    }

    public BigInteger getLastDataTypeIndex() {
        return lastDataTypeIndex;
    }

    public void setLastDataTypeIndex(BigInteger lastDataTypeIndex) {
        this.lastDataTypeIndex = lastDataTypeIndex;
    }

    public BigInteger getLastDataItemIndex() {
        return lastDataItemIndex;
    }

    public void setLastDataItemIndex(BigInteger lastDataItemIndex) {
        this.lastDataItemIndex = lastDataItemIndex;
    }

    public String getLastOwnerId() {
        return lastOwnerId;
    }

    public void setLastOwnerId(String lastOwnerId) {
        this.lastOwnerId = lastOwnerId;
    }

    public String getLastContainerChildId() {
        return lastContainerChildId;
    }

    public void setLastContainerChildId(String lastContainerChildId) {
        this.lastContainerChildId = lastContainerChildId;
    }

    public String getLastDataTypeId() {
        return lastDataTypeId;
    }

    public void setLastDataTypeId(String lastDataTypeId) {
        this.lastDataTypeId = lastDataTypeId;
    }

    public String getLastDataItemId() {
        return lastDataItemId;
    }

    public void setLastDataItemId(String lastDataItemId) {
        this.lastDataItemId = lastDataItemId;
    }

    public String getPrevContainerChainId() {
        return prevContainerChainId;
    }

    public void setPrevContainerChainId(String prevContainerChainId) {
        this.prevContainerChainId = prevContainerChainId;
    }

    public BigInteger getLastUpdateRecordIndex() {
        return lastUpdateRecordIndex;
    }

    public void setLastUpdateRecordIndex(BigInteger lastUpdateRecordIndex) {
        this.lastUpdateRecordIndex = lastUpdateRecordIndex;
    }

    public String getLastUpdateRecordId() {
        return lastUpdateRecordId;
    }

    public void setLastUpdateRecordId(String lastUpdateRecordId) {
        this.lastUpdateRecordId = lastUpdateRecordId;
    }
}
