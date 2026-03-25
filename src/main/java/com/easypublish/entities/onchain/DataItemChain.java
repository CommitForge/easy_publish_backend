package com.easypublish.entities.onchain;

import jakarta.persistence.*;

import java.math.BigInteger;

@Entity
@Table(name = "onchain_data_item_chain")
public class DataItemChain {

    @Id
    @Column(name = "id", nullable = false, length = 256)
    private String id;

    @Column(name = "last_data_item_index")
    private BigInteger lastDataItemIndex;

    @Column(name = "last_data_item_id")
    private String lastDataItemId;

    public DataItemChain(String updateChainId) {
        this.id = updateChainId;
    }

    public DataItemChain() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public BigInteger getLastDataItemIndex() {
        return lastDataItemIndex;
    }

    public void setLastDataItemIndex(BigInteger lastDataItemIndex) {
        this.lastDataItemIndex = lastDataItemIndex;
    }

    public String getLastDataItemId() {
        return lastDataItemId;
    }

    public void setLastDataItemId(String lastDataItemId) {
        this.lastDataItemId = lastDataItemId;
    }
}
