package com.easypublish.service;

import com.easypublish.entities.cars.maintenances.CarMaintenance;
import com.easypublish.entities.offchain.OffchainDataItemRevision;
import com.easypublish.entities.onchain.DataItem;
import com.easypublish.repositories.CarMaintenanceRepository;
import com.easypublish.repositories.DataItemRepository;
import com.easypublish.repositories.OffchainDataItemRevisionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Read service used by report endpoints.
 */
@Service
public class ReportService {

    private static final Pattern OBJECT_ID_PATTERN = Pattern.compile("^0x[a-fA-F0-9]{64}$");
    private static final List<String> REVISION_ID_KEYS = List.of(
            "replaces",
            "replaced",
            "previous",
            "previousIds",
            "previous_ids",
            "previousDataItemIds",
            "previous_data_item_ids",
            "of",
            "items",
            "references",
            "revisionOf",
            "revision_of"
    );

    private record RevisionExtraction(boolean enabled, List<String> replaces) {}

    private record RevisionProjection(Set<String> latestIds, Set<String> supersededIds) {}

    private final CarMaintenanceRepository carMaintenanceRepository;
    private final DataItemRepository dataItemRepository;
    private final OffchainDataItemRevisionRepository offchainDataItemRevisionRepository;
    private final ObjectMapper mapper;

    public ReportService(
            CarMaintenanceRepository carMaintenanceRepository,
            DataItemRepository dataItemRepository,
            OffchainDataItemRevisionRepository offchainDataItemRevisionRepository,
            ObjectMapper mapper
    ) {
        this.carMaintenanceRepository = carMaintenanceRepository;
        this.dataItemRepository = dataItemRepository;
        this.offchainDataItemRevisionRepository = offchainDataItemRevisionRepository;
        this.mapper = mapper;
    }

    /**
     * Returns data items for a single data type.
     */
    public List<DataItem> getDataByType(String dataTypeId) {
        return dataItemRepository.findByDataTypeId(dataTypeId);
    }

    /**
     * Returns maintenance entries for a single data item.
     */
    public List<CarMaintenance> getCarMaintenances(String dataItemId) {
        return carMaintenanceRepository.findByDataItemId(dataItemId);
    }

    /**
     * Returns all maintenance entries for all data items of a data type.
     */
    public List<CarMaintenance> getCarMaintenancesByType(String dataTypeId) {
        return carMaintenanceRepository.findMaintenancesByDataTypeId(dataTypeId);
    }

    /**
     * Builds data-type scoped report payload:
     * - maintenance content from latest revisions only
     * - metadata list of superseded revisions (date, id, name)
     */
    public CarReportPayload getCarReportByType(String dataTypeId) {
        List<DataItem> dataItems = getDataByType(dataTypeId);
        RevisionProjection projection = computeRevisionProjection(dataItems);

        List<CarMaintenance> latestMaintenances = getCarMaintenancesByType(dataTypeId).stream()
                .filter(m -> projection.latestIds().contains(m.getDataItemId()))
                .toList();

        Map<String, DataItem> dataItemsById = dataItems.stream()
                .filter(item -> item.getId() != null && !item.getId().isBlank())
                .collect(LinkedHashMap::new, (map, item) -> map.put(item.getId(), item), Map::putAll);

        List<RevisionMetadata> previousRevisions = projection.supersededIds().stream()
                .map(dataItemsById::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingLong(this::timestampOrZero).reversed())
                .map(this::toRevisionMetadata)
                .toList();

        return new CarReportPayload(latestMaintenances, previousRevisions);
    }

    /**
     * Builds data-item scoped report payload:
     * - maintenance content for selected data item
     * - metadata list of revisions replaced by that selected data item
     */
    public CarReportPayload getCarReportByDataItem(String dataItemId) {
        List<CarMaintenance> maintenances = getCarMaintenances(dataItemId);
        DataItem dataItem = dataItemRepository.findById(dataItemId).orElse(null);

        if (dataItem == null) {
            return new CarReportPayload(maintenances, List.of());
        }

        RevisionExtraction extraction = extractIndexedRevisionExtraction(dataItem);
        if (!extraction.enabled()) {
            List<String> referenceIds = normalizeObjectIds(dataItem.getReferences());
            extraction = extractRevisionExtraction(dataItem.getContent(), referenceIds);
        }

        if (!extraction.enabled() || extraction.replaces().isEmpty()) {
            return new CarReportPayload(maintenances, List.of());
        }

        List<String> orderedIds = new ArrayList<>(new LinkedHashSet<>(extraction.replaces()));
        Map<String, DataItem> replacedItemsById = dataItemRepository.findAllById(orderedIds).stream()
                .collect(LinkedHashMap::new, (map, item) -> map.put(item.getId(), item), Map::putAll);
        String currentContainerId = dataItem.getContainerId();

        List<RevisionMetadata> previousRevisions = orderedIds.stream()
                .map(replacedItemsById::get)
                .filter(Objects::nonNull)
                .filter(replaced -> Objects.equals(replaced.getContainerId(), currentContainerId))
                .map(this::toRevisionMetadata)
                .toList();

        return new CarReportPayload(maintenances, previousRevisions);
    }

    private RevisionProjection computeRevisionProjection(List<DataItem> dataItems) {
        Map<String, DataItem> dataItemsById = dataItems.stream()
                .filter(item -> item.getId() != null && !item.getId().isBlank())
                .collect(LinkedHashMap::new, (map, item) -> map.put(item.getId(), item), Map::putAll);

        List<String> dataItemIds = new ArrayList<>(dataItemsById.keySet());
        Map<String, List<OffchainDataItemRevision>> indexedRevisionsByDataItemId =
                offchainDataItemRevisionRepository.findByDataItemIdIn(dataItemIds).stream()
                        .collect(
                                LinkedHashMap::new,
                                (map, row) -> map.computeIfAbsent(row.getDataItemId(), ignored -> new ArrayList<>()).add(row),
                                Map::putAll
                        );

        Set<String> supersededIds = new LinkedHashSet<>();

        for (DataItem item : dataItemsById.values()) {
            RevisionExtraction extraction = extractIndexedRevisionExtraction(item, indexedRevisionsByDataItemId);
            if (!extraction.enabled()) {
                List<String> referenceIds = normalizeObjectIds(item.getReferences());
                extraction = extractRevisionExtraction(item.getContent(), referenceIds);
            }
            if (!extraction.enabled()) {
                continue;
            }

            for (String previousId : extraction.replaces()) {
                if (dataItemsById.containsKey(previousId) && !Objects.equals(previousId, item.getId())) {
                    supersededIds.add(previousId);
                }
            }
        }

        Set<String> latestIds = new LinkedHashSet<>(dataItemsById.keySet());
        latestIds.removeAll(supersededIds);

        if (latestIds.isEmpty()) {
            latestIds.addAll(dataItemsById.keySet());
        }

        return new RevisionProjection(Set.copyOf(latestIds), Set.copyOf(supersededIds));
    }

    private RevisionExtraction extractIndexedRevisionExtraction(DataItem dataItem) {
        List<OffchainDataItemRevision> rows =
                offchainDataItemRevisionRepository.findByDataItemIdOrderByIdAsc(dataItem.getId());
        return extractIndexedRevisionExtraction(rows);
    }

    private RevisionExtraction extractIndexedRevisionExtraction(
            DataItem dataItem,
            Map<String, List<OffchainDataItemRevision>> rowsByDataItemId
    ) {
        List<OffchainDataItemRevision> rows =
                rowsByDataItemId.getOrDefault(dataItem.getId(), List.of());
        return extractIndexedRevisionExtraction(rows);
    }

    private RevisionExtraction extractIndexedRevisionExtraction(List<OffchainDataItemRevision> rows) {
        if (rows == null || rows.isEmpty()) {
            return new RevisionExtraction(false, List.of());
        }

        boolean enabled = rows.stream().anyMatch(OffchainDataItemRevision::isEnabled);
        if (!enabled) {
            return new RevisionExtraction(false, List.of());
        }

        List<String> replaces = rows.stream()
                .map(OffchainDataItemRevision::getReplacedDataItemId)
                .filter(Objects::nonNull)
                .filter(id -> !id.isBlank())
                .distinct()
                .toList();

        return new RevisionExtraction(true, replaces);
    }

    private RevisionExtraction extractRevisionExtraction(String content, List<String> referenceIds) {
        if (content == null || content.isBlank()) {
            return new RevisionExtraction(false, List.of());
        }

        try {
            Map<String, Object> contentMap = mapper.readValue(
                    content,
                    new TypeReference<Map<String, Object>>() {}
            );

            Object easyPublishObject = contentMap.get("easy_publish");
            if (easyPublishObject == null) {
                easyPublishObject = contentMap.get("easyPublish");
            }
            if (!(easyPublishObject instanceof Map<?, ?> easyPublishMap)) {
                return new RevisionExtraction(false, List.of());
            }

            Object revisionsObject = easyPublishMap.get("revisions");
            if (revisionsObject == null) {
                return new RevisionExtraction(false, List.of());
            }

            if (revisionsObject instanceof Boolean revisionsEnabled) {
                if (!revisionsEnabled) {
                    return new RevisionExtraction(false, List.of());
                }
                return new RevisionExtraction(true, referenceIds);
            }

            if (revisionsObject instanceof Map<?, ?> revisionsMap) {
                Object enabledRaw = revisionsMap.containsKey("enabled")
                        ? revisionsMap.get("enabled")
                        : revisionsMap.get("active");
                boolean enabled = enabledRaw instanceof Boolean enabledBoolean && enabledBoolean;

                if (!enabled) {
                    return new RevisionExtraction(false, List.of());
                }

                List<String> explicitPreviousIds = new ArrayList<>();
                for (String key : REVISION_ID_KEYS) {
                    explicitPreviousIds.addAll(normalizeObjectIds(revisionsMap.get(key)));
                }

                List<String> effectivePreviousIds = explicitPreviousIds.isEmpty()
                        ? referenceIds
                        : normalizeObjectIds(explicitPreviousIds);

                return new RevisionExtraction(true, effectivePreviousIds);
            }

            List<String> explicitPreviousIds = normalizeObjectIds(revisionsObject);
            List<String> effectivePreviousIds = explicitPreviousIds.isEmpty()
                    ? referenceIds
                    : explicitPreviousIds;

            return new RevisionExtraction(true, effectivePreviousIds);
        } catch (Exception ignored) {
            return new RevisionExtraction(false, List.of());
        }
    }

    private RevisionMetadata toRevisionMetadata(DataItem dataItem) {
        return new RevisionMetadata(
                safe(dataItem.getId()),
                safe(dataItem.getName()),
                formatDate(dataItem)
        );
    }

    private String formatDate(DataItem dataItem) {
        Long epochMs = getCreatorTimestampMs(dataItem);
        if (epochMs == null || epochMs <= 0) {
            return "-";
        }
        try {
            return Instant.ofEpochMilli(epochMs)
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate()
                    .toString();
        } catch (Exception ignored) {
            return "-";
        }
    }

    private long timestampOrZero(DataItem dataItem) {
        Long timestamp = getCreatorTimestampMs(dataItem);
        return timestamp != null ? timestamp : 0L;
    }

    private Long getCreatorTimestampMs(DataItem dataItem) {
        if (dataItem == null || dataItem.getCreator() == null) {
            return null;
        }
        BigInteger timestamp = dataItem.getCreator().getCreatorTimestampMs();
        if (timestamp == null) {
            return null;
        }
        try {
            return timestamp.longValueExact();
        } catch (ArithmeticException ignored) {
            return null;
        }
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static List<String> normalizeObjectIds(Object value) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        collectObjectIds(value, ids);
        return List.copyOf(ids);
    }

    private static void collectObjectIds(Object value, Set<String> sink) {
        if (value == null) {
            return;
        }

        if (value instanceof String stringValue) {
            for (String token : stringValue.split(",")) {
                String normalized = token.trim();
                if (OBJECT_ID_PATTERN.matcher(normalized).matches()) {
                    sink.add(normalized);
                }
            }
            return;
        }

        if (value instanceof List<?> listValue) {
            for (Object entry : listValue) {
                collectObjectIds(entry, sink);
            }
            return;
        }

        if (value instanceof Map<?, ?> mapValue) {
            collectObjectIds(mapValue.get("id"), sink);
            collectObjectIds(mapValue.get("object_id"), sink);
            collectObjectIds(mapValue.get("dataItemId"), sink);
            collectObjectIds(mapValue.get("data_item_id"), sink);
            collectObjectIds(mapValue.get("address"), sink);
            collectObjectIds(mapValue.get("value"), sink);
        }
    }

    public static final class CarReportPayload {
        private final List<CarMaintenance> maintenances;
        private final List<RevisionMetadata> previousRevisions;

        public CarReportPayload(List<CarMaintenance> maintenances, List<RevisionMetadata> previousRevisions) {
            this.maintenances = maintenances != null ? List.copyOf(maintenances) : List.of();
            this.previousRevisions = previousRevisions != null ? List.copyOf(previousRevisions) : List.of();
        }

        public List<CarMaintenance> getMaintenances() {
            return maintenances;
        }

        public List<RevisionMetadata> getPreviousRevisions() {
            return previousRevisions;
        }
    }

    public static final class RevisionMetadata {
        private final String id;
        private final String name;
        private final String date;

        public RevisionMetadata(String id, String name, String date) {
            this.id = id;
            this.name = name;
            this.date = date;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getDate() {
            return date;
        }
    }
}
