package com.easypublish.websocket.sync;

import com.easypublish.entities.sync.SyncProgress;
import org.springframework.context.ApplicationEvent;

/**
 * Spring application event wrapper for sync progress updates.
 */
public class SyncProgressEvent extends ApplicationEvent {

    private final SyncProgress syncProgress;

    public SyncProgressEvent(Object source, SyncProgress syncProgress) {
        super(source);
        this.syncProgress = syncProgress;
    }

    public SyncProgress getSyncProgress() {
        return syncProgress;
    }
}
