package com.easypublish.dtos;

import java.util.List;

/**
 * On-chain-grounded revision metadata for one data item.
 */
public class DataItemRevisionDto {

    private final boolean enabled;
    private final List<String> replaces;

    public DataItemRevisionDto(boolean enabled, List<String> replaces) {
        this.enabled = enabled;
        this.replaces = replaces;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<String> getReplaces() {
        return replaces;
    }
}
