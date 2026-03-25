package com.easypublish.controller;

import com.easypublish.batch.BlockEmitterIndexer;
import com.easypublish.batch.IotaDataSync;
import com.easypublish.dtos.SyncStatusDto;
import com.easypublish.entities.onchain.Container;
import com.easypublish.service.NodeSyncService;
import com.easypublish.service.SyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sync")
public class SyncController {
    private final SyncService ss;
    private final NodeSyncService syncService;
    private final EntityManager em;

    @Autowired
    public SyncController(NodeSyncService syncService, SyncService ss, EntityManager em) {
        this.syncService = syncService;
        this.ss = ss;
        this.em = em;
    }

    @GetMapping("/{chainObjectId}")
    public SyncStatusDto getSyncStatus(@PathVariable String chainObjectId) {
        return ss.getSyncStatus(chainObjectId);
    }
}
