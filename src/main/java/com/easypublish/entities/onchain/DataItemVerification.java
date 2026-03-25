package com.easypublish.entities.onchain;

import jakarta.persistence.*;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "onchain_data_item_verification")
public class DataItemVerification {

    @Id
    @Column(name = "id", nullable = false, length = 256)
    private String id;

    // Scope
    @NotNull
    @Column(name = "container_id", nullable = false, length = 256)
    private String containerId;

    @NotNull
    @Column(name = "data_item_id", nullable = false, length = 256)
    private String dataItemId;

    // External linkage
    @Column(name = "external_id", columnDefinition = "TEXT")
    private String externalId;

    // Creator
    @Embedded
    private Creator creator;

    // Recipients
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "onchain_data_item_verif_recipients",
            joinColumns = @JoinColumn(name = "data_item_verification_id")
    )
    @Column(name = "recipient_addr", length = 256)
    private List<String> recipients = new ArrayList<>();

    // Metadata
    @Column(name = "name", columnDefinition = "TEXT")
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "sequence_index")
    private BigInteger sequenceIndex;

    @Column(name = "external_index")
    private BigInteger externalIndex;

    // References
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "onchain_data_item_verif_references",
            joinColumns = @JoinColumn(name = "data_item_verification_id")
    )
    @Column(name = "reference_id", length = 256)
    private List<String> references = new ArrayList<>();

    // Verification results (addresses)
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "onchain_data_item_verif_success_addr",
            joinColumns = @JoinColumn(name = "data_item_verification_id")
    )
    @Column(name = "addr", length = 256)
    private List<String> verificationSuccess = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "onchain_data_item_verif_failure_addr",
            joinColumns = @JoinColumn(name = "data_item_verification_id")
    )
    @Column(name = "addr", length = 256)
    private List<String> verificationFailure = new ArrayList<>();

    @Column(name = "verified")
    private Boolean verified = false;

    // Chain pointers
    @Column(name = "prev_data_item_verification_chain_id", length = 256)
    private String prevDataItemVerificationChainId;

    @Column(name = "prev_id", length = 256)
    private String prevId;

    // ============================
    // Getters & setters
    // ============================

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getContainerId() { return containerId; }
    public void setContainerId(String containerId) { this.containerId = containerId; }

    public String getDataItemId() { return dataItemId; }
    public void setDataItemId(String dataItemId) { this.dataItemId = dataItemId; }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public Creator getCreator() { return creator; }
    public void setCreator(Creator creator) { this.creator = creator; }

    public List<String> getRecipients() { return recipients; }
    public void setRecipients(List<String> recipients) {
        this.recipients = recipients != null ? recipients : new ArrayList<>();
    }

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

    public List<String> getReferences() { return references; }
    public void setReferences(List<String> references) {
        this.references = references != null ? references : new ArrayList<>();
    }

    public List<String> getVerificationSuccess() { return verificationSuccess; }
    public void setVerificationSuccess(List<String> verificationSuccess) {
        this.verificationSuccess = verificationSuccess != null ? verificationSuccess : new ArrayList<>();
    }

    public List<String> getVerificationFailure() { return verificationFailure; }
    public void setVerificationFailure(List<String> verificationFailure) {
        this.verificationFailure = verificationFailure != null ? verificationFailure : new ArrayList<>();
    }

    public Boolean getVerified() { return verified; }
    public void setVerified(Boolean verified) { this.verified = verified; }

    public String getPrevDataItemVerificationChainId() { return prevDataItemVerificationChainId; }
    public void setPrevDataItemVerificationChainId(String prevDataItemVerificationChainId) {
        this.prevDataItemVerificationChainId = prevDataItemVerificationChainId;
    }

    public String getPrevId() { return prevId; }
    public void setPrevId(String prevId) { this.prevId = prevId; }
}
