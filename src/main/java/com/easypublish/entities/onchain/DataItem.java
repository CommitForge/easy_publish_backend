package com.easypublish.entities.onchain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "onchain_data_item",
        indexes = {
                @Index(
                        name = "idx_data_item_container_creator",
                        columnList = "container_id, creator_addr"
                ),
                @Index(
                        name = "idx_data_item_container_type_creator",
                        columnList = "container_id, data_type_id, creator_addr"
                )
        }
)
public class DataItem {

    // -------------------------
    // Identity & scope
    // -------------------------

    @Id
    @Column(name = "id", nullable = false, length = 256)
    private String id;

    @Column(name = "container_id", nullable = false, length = 256)
    private String containerId;

    @Column(name = "data_type_id", nullable = false, length = 256)
    private String dataTypeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "data_type_id", insertable = false, updatable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private DataType dataType;

    // -------------------------
    // External linkage
    // -------------------------

    @Column(name = "external_id", columnDefinition = "TEXT")
    private String externalId;

    // -------------------------
    // Creator
    // -------------------------

    @Embedded
    private Creator creator;

    // -------------------------
    // Metadata
    // -------------------------

    @Column(name = "name", columnDefinition = "TEXT")
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    // -------------------------
    // Ordering
    // -------------------------

    @Column(name = "sequence_index")
    private BigInteger sequenceIndex;

    @Column(name = "external_index")
    private BigInteger externalIndex;

    // -------------------------
    // Recipients (optional)
    // -------------------------

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "onchain_data_item_recipient",
            joinColumns = @JoinColumn(name = "data_item_id")
    )
    @Column(name = "recipient_addr", length = 256)
    private List<String> recipients = new ArrayList<>();

    // -------------------------
    // References
    // -------------------------

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "onchain_data_item_reference",
            joinColumns = @JoinColumn(name = "data_item_id")
    )
    @Column(name = "reference_id", length = 256, nullable = false)
    private List<String> references = new ArrayList<>();

    // -------------------------
    // Verification (addresses)
    // -------------------------

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "onchain_data_item_verif_success_addr",
            joinColumns = @JoinColumn(name = "data_item_id")
    )
    @Column(name = "addr", length = 256)
    private List<String> verificationSuccessAddresses = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "onchain_data_item_verif_failure_addr",
            joinColumns = @JoinColumn(name = "data_item_id")
    )
    @Column(name = "addr", length = 256)
    private List<String> verificationFailureAddresses = new ArrayList<>();

    // -------------------------
    // Verification (data items)
    // -------------------------

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "onchain_data_item_verif_success_item",
            joinColumns = @JoinColumn(name = "data_item_id")
    )
    @Column(name = "verification_data_item_id", length = 256)
    private List<String> verificationSuccessDataItem = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "onchain_data_item_verif_failure_item",
            joinColumns = @JoinColumn(name = "data_item_id")
    )
    @Column(name = "verification_data_item_id", length = 256)
    private List<String> verificationFailureDataItem = new ArrayList<>();

    @Column(name = "verified", nullable = true)
    private Boolean verified;

    // -------------------------
    // Chain pointers
    // -------------------------

    @Column(name = "prev_data_item_chain_id", length = 256)
    private String prevDataItemChainId;

    @Column(name = "prev_id", length = 256)
    private String prevId;

    @Column(name = "prev_data_type_item_id", length = 256)
    private String prevDataTypeItemId;

    // -------------------------
    // Getters / setters
    // -------------------------

    public void setVerificationFailureAddresses(List<String> verificationFailureAddresses) {
        this.verificationFailureAddresses = verificationFailureAddresses != null
                ? verificationFailureAddresses
                : new ArrayList<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getContainerId() { return containerId; }
    public void setContainerId(String containerId) { this.containerId = containerId; }

    public String getDataTypeId() { return dataTypeId; }
    public void setDataTypeId(String dataTypeId) { this.dataTypeId = dataTypeId; }

    public DataType getDataType() { return dataType; }
    public void setDataType(DataType dataType) { this.dataType = dataType; }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public Creator getCreator() { return creator; }
    public void setCreator(Creator creator) { this.creator = creator; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public BigInteger getSequenceIndex() { return sequenceIndex; }
    public void setSequenceIndex(BigInteger sequenceIndex) { this.sequenceIndex = sequenceIndex; }

    public BigInteger getExternalIndex() { return externalIndex; }
    public void setExternalIndex(BigInteger externalIndex) { this.externalIndex = externalIndex; }

    public List<String> getRecipients() { return recipients; }
    public void setRecipients(List<String> recipients) {
        this.recipients = recipients != null ? recipients : new ArrayList<>();
    }

    public List<String> getReferences() { return references; }
    public void setReferences(List<String> references) {
        this.references = references != null ? references : new ArrayList<>();
    }

    public Boolean isVerified() { return verified; }
    public void setVerified(Boolean verified) { this.verified = verified; }

    public String getPrevDataItemChainId() { return prevDataItemChainId; }
    public void setPrevDataItemChainId(String prevDataItemChainId) {
        this.prevDataItemChainId = prevDataItemChainId;
    }

    public String getPrevId() { return prevId; }
    public void setPrevId(String prevId) { this.prevId = prevId; }

    public String getPrevDataTypeItemId() { return prevDataTypeItemId; }
    public void setPrevDataTypeItemId(String prevDataTypeItemId) {
        this.prevDataTypeItemId = prevDataTypeItemId;
    }

    public List<String> getVerificationSuccessAddresses() {
        return verificationSuccessAddresses;
    }

    public void setVerificationSuccessAddresses(List<String> verificationSuccessAddresses) {
        this.verificationSuccessAddresses = verificationSuccessAddresses != null
                ? verificationSuccessAddresses
                : new ArrayList<>();
    }

    public List<String> getVerificationFailureAddresses() {
        return verificationFailureAddresses;
    }

    public List<String> getVerificationSuccessDataItem() {
        return verificationSuccessDataItem;
    }

    public void setVerificationSuccessDataItem(List<String> verificationSuccessDataItem) {
        this.verificationSuccessDataItem = verificationSuccessDataItem != null
                ? verificationSuccessDataItem
                : new ArrayList<>();
    }

    public List<String> getVerificationFailureDataItem() {
        return verificationFailureDataItem;
    }

    public void setVerificationFailureDataItem(List<String> verificationFailureDataItem) {
        this.verificationFailureDataItem = verificationFailureDataItem != null
                ? verificationFailureDataItem
                : new ArrayList<>();
    }
}
