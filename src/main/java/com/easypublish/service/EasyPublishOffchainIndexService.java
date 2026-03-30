package com.easypublish.service;

import com.easypublish.entities.offchain.FollowedContainer;
import com.easypublish.entities.onchain.Container;
import com.easypublish.entities.onchain.DataItem;
import com.easypublish.repositories.CarMaintenanceRepository;
import com.easypublish.repositories.ContainerRepository;
import com.easypublish.repositories.DataItemRepository;
import com.easypublish.repositories.FollowedContainerRepository;
import com.easypublish.repositories.OffchainDataItemRevisionRepository;
import com.easypublish.repositories.OffchainFollowContainerRepository;
import com.easypublish.service.easypublishindex.EasyPublishIndexAccumulator;
import com.easypublish.service.easypublishindex.EasyPublishIndexContext;
import com.easypublish.service.easypublishindex.EasyPublishIndexFeature;
import com.easypublish.service.easypublishindex.EasyPublishIndexUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class EasyPublishOffchainIndexService {

    private static final Logger log = LoggerFactory.getLogger(EasyPublishOffchainIndexService.class);

    private final DataItemRepository dataItemRepository;
    private final ContainerRepository containerRepository;
    private final CarMaintenanceRepository carMaintenanceRepository;
    private final FollowedContainerRepository followedContainerRepository;
    private final OffchainFollowContainerRepository offchainFollowContainerRepository;
    private final OffchainDataItemRevisionRepository offchainDataItemRevisionRepository;
    private final ObjectMapper mapper;
    private final List<EasyPublishIndexFeature> indexFeatures;
    private final ReentrantLock reindexLock = new ReentrantLock();

    public EasyPublishOffchainIndexService(
            DataItemRepository dataItemRepository,
            ContainerRepository containerRepository,
            CarMaintenanceRepository carMaintenanceRepository,
            FollowedContainerRepository followedContainerRepository,
            OffchainFollowContainerRepository offchainFollowContainerRepository,
            OffchainDataItemRevisionRepository offchainDataItemRevisionRepository,
            List<EasyPublishIndexFeature> indexFeatures,
            ObjectMapper mapper
    ) {
        this.dataItemRepository = dataItemRepository;
        this.containerRepository = containerRepository;
        this.carMaintenanceRepository = carMaintenanceRepository;
        this.followedContainerRepository = followedContainerRepository;
        this.offchainFollowContainerRepository = offchainFollowContainerRepository;
        this.offchainDataItemRevisionRepository = offchainDataItemRevisionRepository;
        this.mapper = mapper;
        this.indexFeatures = indexFeatures.stream()
                .sorted(Comparator.comparing(EasyPublishIndexFeature::featureKey))
                .toList();
    }

    @Transactional
    public IndexSummary reindexAllDataItems() {
        if (!reindexLock.tryLock()) {
            return IndexSummary.skipped("Offchain easy_publish index rebuild already running");
        }

        try {
            return rebuildIndexLocked();
        } finally {
            reindexLock.unlock();
        }
    }

    @Transactional
    protected IndexSummary rebuildIndexLocked() {
        List<DataItem> dataItems = new ArrayList<>(dataItemRepository.findAll());
        dataItems.sort(Comparator
                .comparing(DataItem::getSequenceIndex, Comparator.nullsFirst(BigInteger::compareTo))
                .thenComparing(DataItem::getId, Comparator.nullsLast(String::compareTo)));

        Map<String, DataItem> dataItemById = dataItems.stream()
                .filter(di -> EasyPublishIndexUtils.normalize(di.getId()) != null)
                .collect(LinkedHashMap::new, (map, di) -> map.put(di.getId(), di), Map::putAll);

        Map<String, String> containerCreatorById = containerRepository.findAll().stream()
                .collect(LinkedHashMap::new, (map, container) -> {
                    String containerId = EasyPublishIndexUtils.normalize(container.getId());
                    if (containerId == null) {
                        return;
                    }
                    String creatorAddr = container.getCreator() == null
                            ? null
                            : EasyPublishIndexUtils.normalize(container.getCreator().getCreatorAddr());
                    map.put(containerId, creatorAddr);
                }, Map::putAll);

        EasyPublishIndexContext context = new EasyPublishIndexContext(dataItemById, containerCreatorById);
        EasyPublishIndexAccumulator accumulator = new EasyPublishIndexAccumulator();

        offchainFollowContainerRepository.deleteAllInBatch();
        offchainDataItemRevisionRepository.deleteAllInBatch();
        carMaintenanceRepository.deleteAllInBatch();
        followedContainerRepository.deleteAllInBatch();

        for (DataItem dataItem : dataItems) {
            Map<String, Object> easyPublishMap =
                    EasyPublishIndexUtils.parseEasyPublishMap(dataItem.getContent(), mapper);
            if (easyPublishMap == null) {
                continue;
            }

            for (EasyPublishIndexFeature indexFeature : indexFeatures) {
                try {
                    indexFeature.index(dataItem, easyPublishMap, context, accumulator);
                } catch (Exception featureError) {
                    log.warn(
                            "[OFFCHAIN-INDEX] feature '{}' failed for dataItem {}: {}",
                            indexFeature.featureKey(),
                            dataItem.getId(),
                            featureError.getMessage()
                    );
                }
            }
        }

        if (!accumulator.followActionRows().isEmpty()) {
            offchainFollowContainerRepository.saveAll(accumulator.followActionRows());
        }

        if (!accumulator.revisionRows().isEmpty()) {
            offchainDataItemRevisionRepository.saveAll(accumulator.revisionRows());
        }

        if (!accumulator.maintenanceRows().isEmpty()) {
            carMaintenanceRepository.saveAll(accumulator.maintenanceRows());
        }

        List<FollowedContainer> followedContainers = accumulator.buildActiveFollowedContainers();
        if (!followedContainers.isEmpty()) {
            followedContainerRepository.saveAll(followedContainers);
        }

        return IndexSummary.completed(
                dataItems.size(),
                accumulator.followActionRows().size(),
                followedContainers.size(),
                accumulator.revisionRows().size(),
                accumulator.maintenanceRows().size()
        );
    }

    public record IndexSummary(
            boolean skipped,
            String message,
            int scannedDataItems,
            int followActions,
            int activeFollows,
            int revisionRows,
            int maintenanceRows
    ) {
        public static IndexSummary skipped(String message) {
            return new IndexSummary(true, message, 0, 0, 0, 0, 0);
        }

        public static IndexSummary completed(
                int scannedDataItems,
                int followActions,
                int activeFollows,
                int revisionRows,
                int maintenanceRows
        ) {
            return new IndexSummary(
                    false,
                    "ok",
                    scannedDataItems,
                    followActions,
                    activeFollows,
                    revisionRows,
                    maintenanceRows
            );
        }
    }
}
