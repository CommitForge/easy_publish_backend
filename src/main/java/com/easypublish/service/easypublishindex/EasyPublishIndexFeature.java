package com.easypublish.service.easypublishindex;

import com.easypublish.entities.onchain.DataItem;

import java.util.Map;

/**
 * Pluggable offchain index contributor for one easy_publish feature namespace.
 */
public interface EasyPublishIndexFeature {

    /**
     * Stable key used for diagnostics and deterministic ordering.
     */
    String featureKey();

    /**
     * Index one synced on-chain data item's easy_publish payload.
     */
    void index(
            DataItem dataItem,
            Map<String, Object> easyPublishMap,
            EasyPublishIndexContext context,
            EasyPublishIndexAccumulator accumulator
    );
}
