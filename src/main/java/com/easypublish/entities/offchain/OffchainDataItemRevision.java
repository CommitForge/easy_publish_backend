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
        name = "offchain_data_item_revision",
        indexes = {
                @Index(name = "idx_offchain_revision_data_item", columnList = "data_item_id"),
                @Index(name = "idx_offchain_revision_replaced_item", columnList = "replaced_data_item_id")
        }
)
public class OffchainDataItemRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_item_id", nullable = false, columnDefinition = "TEXT")
    private String dataItemId;

    @Column(name = "replaced_data_item_id", columnDefinition = "TEXT")
    private String replacedDataItemId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "change_description", columnDefinition = "TEXT")
    private String changeDescription;

    @Column(name = "source", columnDefinition = "TEXT")
    private String source;

    @Column(name = "sequence_index")
    private BigInteger sequenceIndex;

    public OffchainDataItemRevision() {
    }

    public OffchainDataItemRevision(
            String dataItemId,
            String replacedDataItemId,
            boolean enabled,
            String changeDescription,
            String source,
            BigInteger sequenceIndex
    ) {
        this.dataItemId = dataItemId;
        this.replacedDataItemId = replacedDataItemId;
        this.enabled = enabled;
        this.changeDescription = changeDescription;
        this.source = source;
        this.sequenceIndex = sequenceIndex;
    }

    public Long getId() {
        return id;
    }

    public String getDataItemId() {
        return dataItemId;
    }

    public void setDataItemId(String dataItemId) {
        this.dataItemId = dataItemId;
    }

    public String getReplacedDataItemId() {
        return replacedDataItemId;
    }

    public void setReplacedDataItemId(String replacedDataItemId) {
        this.replacedDataItemId = replacedDataItemId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getChangeDescription() {
        return changeDescription;
    }

    public void setChangeDescription(String changeDescription) {
        this.changeDescription = changeDescription;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public BigInteger getSequenceIndex() {
        return sequenceIndex;
    }

    public void setSequenceIndex(BigInteger sequenceIndex) {
        this.sequenceIndex = sequenceIndex;
    }
}
