package com.easypublish.entities.offchain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigInteger;

@Entity
@Table(
        name = "offchain_follow_container",
        indexes = {
                @Index(name = "idx_offchain_follow_container_actor", columnList = "actor_address"),
                @Index(name = "idx_offchain_follow_container_target", columnList = "target_container_id"),
                @Index(name = "idx_offchain_follow_container_source", columnList = "source_data_item_id")
        }
)
public class OffchainFollowContainer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_data_item_id", nullable = false, columnDefinition = "TEXT")
    private String sourceDataItemId;

    @Column(name = "actor_address", nullable = false, columnDefinition = "TEXT")
    private String actorAddress;

    @Column(name = "target_container_id", nullable = false, columnDefinition = "TEXT")
    private String targetContainerId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "sequence_index")
    private BigInteger sequenceIndex;

    public OffchainFollowContainer() {
    }

    public OffchainFollowContainer(
            String sourceDataItemId,
            String actorAddress,
            String targetContainerId,
            boolean enabled,
            BigInteger sequenceIndex
    ) {
        this.sourceDataItemId = sourceDataItemId;
        this.actorAddress = actorAddress;
        this.targetContainerId = targetContainerId;
        this.enabled = enabled;
        this.sequenceIndex = sequenceIndex;
    }

    public Long getId() {
        return id;
    }

    public String getSourceDataItemId() {
        return sourceDataItemId;
    }

    public void setSourceDataItemId(String sourceDataItemId) {
        this.sourceDataItemId = sourceDataItemId;
    }

    public String getActorAddress() {
        return actorAddress;
    }

    public void setActorAddress(String actorAddress) {
        this.actorAddress = actorAddress;
    }

    public String getTargetContainerId() {
        return targetContainerId;
    }

    public void setTargetContainerId(String targetContainerId) {
        this.targetContainerId = targetContainerId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public BigInteger getSequenceIndex() {
        return sequenceIndex;
    }

    public void setSequenceIndex(BigInteger sequenceIndex) {
        this.sequenceIndex = sequenceIndex;
    }
}
