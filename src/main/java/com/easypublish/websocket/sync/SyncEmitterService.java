package com.easypublish.websocket.sync;

import com.easypublish.dtos.SyncStatusDto;
import com.easypublish.service.SyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Periodically emits sync status over websocket topic.
 */
@Service
public class SyncEmitterService {

    private static final Logger log = LoggerFactory.getLogger(SyncEmitterService.class);

    // Extracted to properties: sync.chain.last
    @Value("${sync.chain.last}")
    private String lastSyncChain;

    // Extracted to properties: app.websocket.sync-topic
    @Value("${app.websocket.sync-topic:/topic/sync-status}")
    private String syncTopic;

    private final SyncService syncService;
    private final SimpMessagingTemplate messagingTemplate;

    public SyncEmitterService(SyncService syncService, SimpMessagingTemplate messagingTemplate) {
        this.syncService = syncService;
        this.messagingTemplate = messagingTemplate;
    }

    // Extracted to properties: app.sync.status-push-rate-ms
    @Scheduled(fixedRateString = "${app.sync.status-push-rate-ms:5000}")
    public void pushSyncStatus() {
        SyncStatusDto status = syncService.getSyncStatus(lastSyncChain);
        messagingTemplate.convertAndSend(syncTopic, status);
        log.debug("Published sync status to topic {}", syncTopic);
    }
}
