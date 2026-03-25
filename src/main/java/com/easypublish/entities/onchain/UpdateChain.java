package com.easypublish.entities.onchain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigInteger;

@Entity
@Table(name = "onchain_update_chain")
public class UpdateChain {

    @Id
    @Column(name = "id", nullable = false, length = 256)
    private String id;

    @Column(name = "last_update_record_index")
    private BigInteger lastContainerIndex;

    @Column(name = "last_update_record_id")
    private String lastContainerId;

    public UpdateChain(String updateChainId) {
        this.id = updateChainId;
    }

    public UpdateChain() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public BigInteger getLastContainerIndex() {
        return lastContainerIndex;
    }

    public void setLastContainerIndex(BigInteger lastContainerIndex) {
        this.lastContainerIndex = lastContainerIndex;
    }

    public String getLastContainerId() {
        return lastContainerId;
    }

    public void setLastContainerId(String lastContainerId) {
        this.lastContainerId = lastContainerId;
    }
}
