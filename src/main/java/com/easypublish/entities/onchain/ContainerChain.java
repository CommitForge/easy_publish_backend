package com.easypublish.entities.onchain;

import jakarta.persistence.*;

import java.math.BigInteger;

@Entity
@Table(name = "onchain_container_chain")
public class ContainerChain {

    @Id
    @Column(name = "id", nullable = false, length = 256)
    private String id;

    @Column(name = "last_container_index")
    private BigInteger lastContainerIndex;

    @Column(name = "last_container_id")
    private String lastContainerId;

    public ContainerChain(String updateChainId) {
        this.id = updateChainId;
    }

    public ContainerChain() {
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
