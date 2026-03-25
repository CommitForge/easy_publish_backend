package com.easypublish.service;

import com.easypublish.batch.IotaDataSync;
import com.easypublish.batch.BlockEmitterIndexer;
import com.easypublish.entities.onchain.Container;
import com.easypublish.parsed.EasyPublishParser;
import com.easypublish.repositories.DataItemRepository;
import com.easypublish.repositories.SyncProgressRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;

@Service
public class NodeSyncService {

    private final BlockEmitterIndexer indexer;
    private final ObjectMapper mapper;
    private final EntityManager em;
    private final IotaDataSync iotaDataSync; // <-- inject instead of 'new'

    @Autowired
    public NodeSyncService(EntityManager em,
                           IotaDataSync iotaDataSync, EasyPublishParser easyPublishParser) {
        this.em = em;
        this.indexer = new BlockEmitterIndexer(em , easyPublishParser);
        this.mapper = new ObjectMapper();
        this.iotaDataSync = iotaDataSync;
    }

    @Transactional
    public void syncUpdateChainRecursively(
            String containerChainId,
            String updateChainId,
            String dataItemChainId,
            String dataItemVerificationChainId
    ) throws Exception {
        iotaDataSync.syncUpdateChainRecords(
                containerChainId, updateChainId, dataItemChainId, dataItemVerificationChainId
        );
    }

}
