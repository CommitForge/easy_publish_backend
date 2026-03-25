package com.easypublish.controller;

import com.easypublish.service.NodeSyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/update-chain")
public class UpdateChainSyncController {

    private final NodeSyncService nodeSyncService;

    @Autowired
    public UpdateChainSyncController(NodeSyncService nodeSyncService) {
        this.nodeSyncService = nodeSyncService;
    }

    /**
     * Recursively sync all containers from an UpdateChain
     * Example: POST /sync/update-chain/0x19478eb24bcedc2f1398939ebedcdd72bcd50b7d1a8ef3fff9aac7286f71d64e
     */
    @PostMapping("/sync")
    public String syncUpdateChain(
            @RequestParam("containerChainId") String containerChainId,
            @RequestParam("updateChainId") String updateChainId,
            @RequestParam("dataItemChainId") String dataItemChainId,
            @RequestParam("dataItemVerificationChainId") String dataItemVerificationChainId
    ) throws Exception {
        nodeSyncService.syncUpdateChainRecursively(
                containerChainId, updateChainId, dataItemChainId, dataItemVerificationChainId
        );
        return "Sync started for all update chains";
    }

}
