package com.easypublish.service.easypublishindex;

import com.easypublish.entities.onchain.DataItem;

import java.util.Map;

/**
 * Shared immutable context used by all easy_publish index features.
 */
public record EasyPublishIndexContext(
        Map<String, DataItem> dataItemById,
        Map<String, String> containerCreatorById
) {
}
