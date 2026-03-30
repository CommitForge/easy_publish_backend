package com.easypublish.controller;

import com.easypublish.dtos.SyncStatusDto;
import com.easypublish.service.SyncService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sync")
public class SyncController {
    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @GetMapping("/{chainObjectId}")
    public SyncStatusDto getSyncStatus(@PathVariable String chainObjectId) {
        return syncService.getSyncStatus(chainObjectId);
    }
}
