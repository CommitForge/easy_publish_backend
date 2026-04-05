package com.easypublish.service;

import com.easypublish.entities.offchain.FollowedContainer;
import com.easypublish.entities.offchain.OffchainFollowContainer;
import com.easypublish.entities.onchain.Container;
import com.easypublish.entities.onchain.Creator;
import com.easypublish.entities.onchain.DataItem;
import com.easypublish.entities.onchain.DataItemVerification;
import com.easypublish.entities.onchain.DataType;
import com.easypublish.entities.publish.PublishTarget;
import com.easypublish.repositories.ContainerRepository;
import com.easypublish.repositories.DataItemRepository;
import com.easypublish.repositories.DataItemVerificationRepository;
import com.easypublish.repositories.DataTypeRepository;
import com.easypublish.repositories.FollowedContainerRepository;
import com.easypublish.repositories.OffchainFollowContainerRepository;
import com.easypublish.repositories.PublishTargetRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AnalyticsDashboardService {

    private static final int DEFAULT_TOP_N = 8;
    private static final int MIN_TOP_N = 1;
    private static final int MAX_TOP_N = 25;
    private static final int DEFAULT_GRAPH_LIMIT = 100;
    private static final int MIN_GRAPH_LIMIT = 1;
    private static final int MAX_GRAPH_LIMIT = 300;

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter YEAR_LABEL = DateTimeFormatter.ofPattern("yyyy");

    private enum Granularity {
        DAY,
        MONTH,
        YEAR
    }

    private static final class BucketCounter {
        long containers;
        long dataTypes;
        long dataItems;
        long verifications;
        final Set<String> activeAddresses = new HashSet<>();
    }

    private final ContainerRepository containerRepository;
    private final DataTypeRepository dataTypeRepository;
    private final DataItemRepository dataItemRepository;
    private final DataItemVerificationRepository dataItemVerificationRepository;
    private final FollowedContainerRepository followedContainerRepository;
    private final OffchainFollowContainerRepository offchainFollowContainerRepository;
    private final PublishTargetRepository publishTargetRepository;

    public AnalyticsDashboardService(
            ContainerRepository containerRepository,
            DataTypeRepository dataTypeRepository,
            DataItemRepository dataItemRepository,
            DataItemVerificationRepository dataItemVerificationRepository,
            FollowedContainerRepository followedContainerRepository,
            OffchainFollowContainerRepository offchainFollowContainerRepository,
            PublishTargetRepository publishTargetRepository
    ) {
        this.containerRepository = containerRepository;
        this.dataTypeRepository = dataTypeRepository;
        this.dataItemRepository = dataItemRepository;
        this.dataItemVerificationRepository = dataItemVerificationRepository;
        this.followedContainerRepository = followedContainerRepository;
        this.offchainFollowContainerRepository = offchainFollowContainerRepository;
        this.publishTargetRepository = publishTargetRepository;
    }

    public Map<String, Object> buildDashboard(
            String userAddress,
            String granularityRaw,
            String fromRaw,
            String toRaw,
            String timezoneRaw,
            Integer topNRaw,
            String containerIdRaw,
            String dataTypeIdRaw,
            String drilldownDimensionRaw,
            String drilldownKeyRaw,
            String domainRaw,
            Integer graphLimitRaw
    ) {
        String user = normalizeBlank(userAddress);
        if (user == null) {
            throw new IllegalArgumentException("userAddress is required");
        }

        Granularity granularity = parseGranularity(granularityRaw);
        ZoneId zoneId = parseZoneId(timezoneRaw);
        int topN = clampTopN(topNRaw);
        int graphLimit = clampGraphLimit(graphLimitRaw);

        LocalDate from = parseIsoDateOrNull(fromRaw, "from");
        LocalDate to = parseIsoDateOrNull(toRaw, "to");
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from must be <= to");
        }

        String containerIdFilter = normalizeBlank(containerIdRaw);
        String dataTypeIdFilter = normalizeBlank(dataTypeIdRaw);
        String drilldownDimension = normalizeBlank(drilldownDimensionRaw);
        String drilldownKey = normalizeBlank(drilldownKeyRaw);
        String domain = normalizeBlank(domainRaw);

        Set<String> followedContainerIds = resolveFollowedContainerIds(user);
        Set<String> visibleContainerIds = resolveVisibleContainerIds(user, followedContainerIds);

        if (containerIdFilter != null) {
            if (!visibleContainerIds.contains(containerIdFilter)) {
                visibleContainerIds = new LinkedHashSet<>();
            } else {
                visibleContainerIds = new LinkedHashSet<>(List.of(containerIdFilter));
            }
        }

        if (domain != null && !visibleContainerIds.isEmpty()) {
            Set<String> domainContainerIds = publishTargetRepository
                    .findByDomainAndContainerIdIn(domain, visibleContainerIds)
                    .stream()
                    .map(PublishTarget::getContainerId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            visibleContainerIds.retainAll(domainContainerIds);
        }

        Map<String, Container> containersById = containerRepository.findAllById(visibleContainerIds)
                .stream()
                .collect(Collectors.toMap(
                        Container::getId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        List<Container> visibleContainers = visibleContainerIds.stream()
                .map(containersById::get)
                .filter(Objects::nonNull)
                .toList();

        List<Container> scopedContainers = visibleContainers.stream()
                .filter(container -> isInDateRange(extractCreatorDate(container.getCreator(), zoneId), from, to))
                .toList();

        Set<String> scopedVisibleContainerIds = Set.copyOf(visibleContainerIds);

        List<DataType> loadedDataTypes;
        if (dataTypeIdFilter != null) {
            loadedDataTypes = dataTypeRepository.findById(dataTypeIdFilter)
                    .stream()
                    .filter(dataType -> scopedVisibleContainerIds.contains(dataType.getContainerId()))
                    .toList();
        } else {
            List<DataType> all = new ArrayList<>();
            for (String containerId : visibleContainerIds) {
                all.addAll(dataTypeRepository.findByContainer(containerId));
            }
            loadedDataTypes = all;
        }

        if (domain != null && !loadedDataTypes.isEmpty()) {
            Set<String> domainTypeIds = publishTargetRepository
                    .findByDomainAndDataTypeIdIn(
                            domain,
                            loadedDataTypes.stream().map(DataType::getId).collect(Collectors.toSet())
                    )
                    .stream()
                    .map(PublishTarget::getDataTypeId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            loadedDataTypes = loadedDataTypes.stream()
                    .filter(dataType -> domainTypeIds.contains(dataType.getId()))
                    .toList();
        }

        List<DataType> scopedDataTypes = loadedDataTypes.stream()
                .filter(dataType -> isInDateRange(extractCreatorDate(dataType.getCreator(), zoneId), from, to))
                .toList();

        Map<String, List<String>> typeIdsByContainer = loadedDataTypes.stream()
                .collect(Collectors.groupingBy(
                        DataType::getContainerId,
                        LinkedHashMap::new,
                        Collectors.mapping(DataType::getId, Collectors.toList())
                ));

        List<DataItem> loadedDataItems = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : typeIdsByContainer.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            loadedDataItems.addAll(
                    dataItemRepository.findByContainerAndDataTypeIds(entry.getKey(), entry.getValue())
            );
        }

        if (domain != null && !loadedDataItems.isEmpty()) {
            Set<String> domainDataItemIds = publishTargetRepository
                    .findByDomainAndDataItemIdIn(
                            domain,
                            loadedDataItems.stream().map(DataItem::getId).collect(Collectors.toSet())
                    )
                    .stream()
                    .map(PublishTarget::getDataItemId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            loadedDataItems = loadedDataItems.stream()
                    .filter(dataItem -> domainDataItemIds.contains(dataItem.getId()))
                    .toList();
        }

        List<DataItem> scopedDataItems = loadedDataItems.stream()
                .filter(dataItem -> isInDateRange(extractCreatorDate(dataItem.getCreator(), zoneId), from, to))
                .toList();

        Map<String, DataItem> dataItemsById = scopedDataItems.stream()
                .collect(Collectors.toMap(
                        DataItem::getId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        List<DataItemVerification> loadedVerifications;
        if (dataItemsById.isEmpty()) {
            loadedVerifications = List.of();
        } else {
            loadedVerifications = dataItemVerificationRepository.findByDataItemIdIn(dataItemsById.keySet().stream().toList());
        }

        if (domain != null && !loadedVerifications.isEmpty()) {
            Set<String> domainVerificationIds = publishTargetRepository
                    .findByDomainAndDataItemVerificationIdIn(
                            domain,
                            loadedVerifications.stream().map(DataItemVerification::getId).collect(Collectors.toSet())
                    )
                    .stream()
                    .map(PublishTarget::getDataItemVerificationId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            loadedVerifications = loadedVerifications.stream()
                    .filter(verification -> domainVerificationIds.contains(verification.getId()))
                    .toList();
        }

        List<DataItemVerification> scopedVerifications = loadedVerifications.stream()
                .filter(verification -> isInDateRange(extractCreatorDate(verification.getCreator(), zoneId), from, to))
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("meta", buildMeta(granularity, zoneId, from, to));
        response.put(
                "totals",
                buildTotals(scopedContainers, scopedDataTypes, scopedDataItems, scopedVerifications, followedContainerIds.size())
        );
        response.put(
                "timeSeries",
                buildTimeSeries(scopedContainers, scopedDataTypes, scopedDataItems, scopedVerifications, granularity, zoneId)
        );

        response.put(
                "topDataTypes",
                buildTopDataTypes(loadedDataTypes, scopedDataItems, scopedVerifications, containersById, topN)
        );
        response.put(
                "topContainers",
                buildTopContainers(visibleContainers, loadedDataTypes, scopedDataItems, scopedVerifications, topN)
        );

        Map<String, Object> topAddresses = new LinkedHashMap<>();
        topAddresses.put("dataItems", buildTopAddresses(scopedDataItems, true, topN));
        topAddresses.put("verifications", buildTopAddresses(scopedVerifications, false, topN));
        response.put("topAddresses", topAddresses);
        response.put(
                "latestPublishedGraph",
                buildLatestPublishedGraph(
                        containersById,
                        loadedDataTypes,
                        scopedDataItems,
                        scopedVerifications,
                        graphLimit
                )
        );

        Map<String, Object> drilldown = buildDrilldown(
                drilldownDimension,
                drilldownKey,
                visibleContainers,
                loadedDataTypes,
                scopedDataItems,
                scopedVerifications,
                granularity,
                zoneId
        );
        if (!drilldown.isEmpty()) {
            response.put("drilldown", drilldown);
        }

        return response;
    }

    private Map<String, Object> buildDrilldown(
            String drilldownDimension,
            String drilldownKey,
            List<Container> scopedContainers,
            List<DataType> scopedDataTypes,
            List<DataItem> scopedDataItems,
            List<DataItemVerification> scopedVerifications,
            Granularity granularity,
            ZoneId zoneId
    ) {
        if (drilldownDimension == null || drilldownKey == null) {
            return Map.of();
        }

        String normalizedDimension = drilldownDimension.trim().toLowerCase(Locale.ROOT);
        String normalizedKey = drilldownKey.trim();

        List<Container> drillContainers = List.of();
        List<DataType> drillDataTypes = List.of();
        List<DataItem> drillDataItems = List.of();
        List<DataItemVerification> drillVerifications = List.of();
        String label = normalizedKey;

        if ("container".equals(normalizedDimension)) {
            Set<String> matchingContainerIds = scopedContainers.stream()
                    .filter(container ->
                            normalizedKey.equals(container.getId())
                                    || equalsIgnoreCase(normalizedKey, container.getName())
                    )
                    .map(Container::getId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            drillContainers = scopedContainers.stream()
                    .filter(container -> matchingContainerIds.contains(container.getId()))
                    .toList();
            if (!drillContainers.isEmpty()) {
                label = drillContainers.get(0).getName() == null
                        ? drillContainers.get(0).getId()
                        : drillContainers.get(0).getName();
            }

            drillDataTypes = scopedDataTypes.stream()
                    .filter(dataType -> matchingContainerIds.contains(dataType.getContainerId()))
                    .toList();

            drillDataItems = scopedDataItems.stream()
                    .filter(dataItem -> matchingContainerIds.contains(dataItem.getContainerId()))
                    .toList();

            Set<String> drillDataItemIds = drillDataItems.stream().map(DataItem::getId).collect(Collectors.toSet());
            drillVerifications = scopedVerifications.stream()
                    .filter(verification -> drillDataItemIds.contains(verification.getDataItemId()))
                    .toList();
        } else if ("datatype".equals(normalizedDimension)) {
            Set<String> matchingDataTypeIds = scopedDataTypes.stream()
                    .filter(dataType ->
                            normalizedKey.equals(dataType.getId())
                                    || equalsIgnoreCase(normalizedKey, dataType.getName())
                    )
                    .map(DataType::getId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            drillDataTypes = scopedDataTypes.stream()
                    .filter(dataType -> matchingDataTypeIds.contains(dataType.getId()))
                    .toList();
            if (!drillDataTypes.isEmpty()) {
                label = drillDataTypes.get(0).getName() == null
                        ? drillDataTypes.get(0).getId()
                        : drillDataTypes.get(0).getName();
            }

            drillDataItems = scopedDataItems.stream()
                    .filter(dataItem -> matchingDataTypeIds.contains(dataItem.getDataTypeId()))
                    .toList();

            Set<String> drillContainerIds = drillDataTypes.stream()
                    .map(DataType::getContainerId)
                    .collect(Collectors.toSet());
            drillContainers = scopedContainers.stream()
                    .filter(container -> drillContainerIds.contains(container.getId()))
                    .toList();

            Set<String> drillDataItemIds = drillDataItems.stream().map(DataItem::getId).collect(Collectors.toSet());
            drillVerifications = scopedVerifications.stream()
                    .filter(verification -> drillDataItemIds.contains(verification.getDataItemId()))
                    .toList();
        } else if ("address".equals(normalizedDimension)) {
            String normalizedAddress = normalizeAddress(normalizedKey);
            label = normalizedAddress == null ? normalizedKey : normalizedAddress;

            drillDataItems = scopedDataItems.stream()
                    .filter(dataItem -> equalsAddress(dataItem.getCreator() == null ? null : dataItem.getCreator().getCreatorAddr(), normalizedAddress))
                    .toList();

            drillVerifications = scopedVerifications.stream()
                    .filter(verification -> equalsAddress(
                            verification.getCreator() == null ? null : verification.getCreator().getCreatorAddr(),
                            normalizedAddress
                    ))
                    .toList();

            Set<String> drillTypeIds = drillDataItems.stream().map(DataItem::getDataTypeId).collect(Collectors.toSet());
            drillDataTypes = scopedDataTypes.stream()
                    .filter(dataType -> drillTypeIds.contains(dataType.getId()))
                    .toList();

            Set<String> drillContainerIds = drillDataItems.stream().map(DataItem::getContainerId).collect(Collectors.toSet());
            drillContainers = scopedContainers.stream()
                    .filter(container -> drillContainerIds.contains(container.getId()))
                    .toList();
        } else {
            return Map.of();
        }

        Map<String, Object> drilldown = new LinkedHashMap<>();
        drilldown.put("dimension", normalizedDimension);
        drilldown.put("key", normalizedKey);
        drilldown.put("label", label == null || label.isBlank() ? normalizedKey : label);
        drilldown.put("totals", buildTotals(drillContainers, drillDataTypes, drillDataItems, drillVerifications, null));
        drilldown.put(
                "timeSeries",
                buildTimeSeries(drillContainers, drillDataTypes, drillDataItems, drillVerifications, granularity, zoneId)
        );

        return drilldown;
    }

    private Map<String, Object> buildMeta(
            Granularity granularity,
            ZoneId zoneId,
            LocalDate from,
            LocalDate to
    ) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("generatedAt", Instant.now().toString());
        meta.put("timezone", zoneId.getId());
        meta.put("from", from == null ? null : from.format(ISO_DATE));
        meta.put("to", to == null ? null : to.format(ISO_DATE));
        meta.put("granularity", granularity.name().toLowerCase(Locale.ROOT));
        return meta;
    }

    private Map<String, Object> buildTotals(
            List<Container> containers,
            List<DataType> dataTypes,
            List<DataItem> dataItems,
            List<DataItemVerification> verifications,
            Integer followedContainersCount
    ) {
        Set<String> activeAddresses = collectActiveAddresses(dataItems, verifications);

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("containers", containers.size());
        totals.put("dataTypes", dataTypes.size());
        totals.put("dataItems", dataItems.size());
        totals.put("verifications", verifications.size());
        totals.put("activeAddresses", activeAddresses.size());

        if (followedContainersCount != null) {
            totals.put("followedContainers", followedContainersCount);
        }

        return totals;
    }

    private List<Map<String, Object>> buildTimeSeries(
            List<Container> containers,
            List<DataType> dataTypes,
            List<DataItem> dataItems,
            List<DataItemVerification> verifications,
            Granularity granularity,
            ZoneId zoneId
    ) {
        Map<LocalDate, BucketCounter> buckets = new TreeMap<>();

        for (Container container : containers) {
            addToBucket(
                    buckets,
                    granularity,
                    extractCreatorDate(container.getCreator(), zoneId),
                    bucket -> bucket.containers += 1
            );
        }

        for (DataType dataType : dataTypes) {
            addToBucket(
                    buckets,
                    granularity,
                    extractCreatorDate(dataType.getCreator(), zoneId),
                    bucket -> bucket.dataTypes += 1
            );
        }

        for (DataItem dataItem : dataItems) {
            addToBucket(
                    buckets,
                    granularity,
                    extractCreatorDate(dataItem.getCreator(), zoneId),
                    bucket -> {
                        bucket.dataItems += 1;
                        String address = normalizeAddress(dataItem.getCreator() == null ? null : dataItem.getCreator().getCreatorAddr());
                        if (address != null) {
                            bucket.activeAddresses.add(address);
                        }
                    }
            );
        }

        for (DataItemVerification verification : verifications) {
            addToBucket(
                    buckets,
                    granularity,
                    extractCreatorDate(verification.getCreator(), zoneId),
                    bucket -> {
                        bucket.verifications += 1;
                        String address = normalizeAddress(
                                verification.getCreator() == null ? null : verification.getCreator().getCreatorAddr()
                        );
                        if (address != null) {
                            bucket.activeAddresses.add(address);
                        }
                    }
            );
        }

        List<Map<String, Object>> series = new ArrayList<>();
        for (Map.Entry<LocalDate, BucketCounter> entry : buckets.entrySet()) {
            LocalDate bucketDate = entry.getKey();
            BucketCounter counter = entry.getValue();

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("bucket", bucketDate.format(ISO_DATE));
            row.put("label", formatBucketLabel(bucketDate, granularity));
            row.put("containers", counter.containers);
            row.put("dataTypes", counter.dataTypes);
            row.put("dataItems", counter.dataItems);
            row.put("verifications", counter.verifications);
            row.put("activeAddresses", counter.activeAddresses.size());
            series.add(row);
        }

        return series;
    }

    private List<Map<String, Object>> buildTopDataTypes(
            List<DataType> dataTypes,
            List<DataItem> dataItems,
            List<DataItemVerification> verifications,
            Map<String, Container> containersById,
            int topN
    ) {
        Map<String, DataType> typesById = dataTypes.stream()
                .collect(Collectors.toMap(
                        DataType::getId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Map<String, Long> itemCountByType = dataItems.stream()
                .collect(Collectors.groupingBy(DataItem::getDataTypeId, Collectors.counting()));

        Map<String, String> itemTypeByItemId = dataItems.stream()
                .collect(Collectors.toMap(
                        DataItem::getId,
                        DataItem::getDataTypeId,
                        (left, right) -> left,
                        HashMap::new
                ));

        Map<String, Long> verificationCountByType = new HashMap<>();
        for (DataItemVerification verification : verifications) {
            String typeId = itemTypeByItemId.get(verification.getDataItemId());
            if (typeId == null) {
                continue;
            }
            verificationCountByType.merge(typeId, 1L, Long::sum);
        }

        Set<String> allTypeIds = new LinkedHashSet<>();
        allTypeIds.addAll(itemCountByType.keySet());
        allTypeIds.addAll(verificationCountByType.keySet());
        allTypeIds.addAll(typesById.keySet());

        return allTypeIds.stream()
                .map(typeId -> {
                    DataType dataType = typesById.get(typeId);
                    String containerId = dataType == null ? null : dataType.getContainerId();
                    Container container = containerId == null ? null : containersById.get(containerId);

                    long itemCount = itemCountByType.getOrDefault(typeId, 0L);
                    long verificationCount = verificationCountByType.getOrDefault(typeId, 0L);

                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("dataTypeId", typeId);
                    row.put("dataTypeName", dataType == null ? null : dataType.getName());
                    row.put("containerId", containerId);
                    row.put("containerName", container == null ? null : container.getName());
                    row.put("dataItems", itemCount);
                    row.put("verifications", verificationCount);
                    return row;
                })
                .sorted(Comparator
                        .comparingLong((Map<String, Object> row) -> asLong(row.get("dataItems"))).reversed()
                        .thenComparingLong((Map<String, Object> row) -> asLong(row.get("verifications"))).reversed()
                        .thenComparing(row -> Objects.toString(row.get("dataTypeName"), ""), String.CASE_INSENSITIVE_ORDER)
                )
                .limit(topN)
                .toList();
    }

    private List<Map<String, Object>> buildTopContainers(
            List<Container> containers,
            List<DataType> dataTypes,
            List<DataItem> dataItems,
            List<DataItemVerification> verifications,
            int topN
    ) {
        Map<String, Container> containersById = containers.stream()
                .collect(Collectors.toMap(
                        Container::getId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Map<String, Long> dataTypeCountByContainer = dataTypes.stream()
                .collect(Collectors.groupingBy(DataType::getContainerId, Collectors.counting()));

        Map<String, Long> itemCountByContainer = dataItems.stream()
                .collect(Collectors.groupingBy(DataItem::getContainerId, Collectors.counting()));

        Map<String, String> itemContainerByItemId = dataItems.stream()
                .collect(Collectors.toMap(
                        DataItem::getId,
                        DataItem::getContainerId,
                        (left, right) -> left,
                        HashMap::new
                ));

        Map<String, Long> verificationCountByContainer = new HashMap<>();
        for (DataItemVerification verification : verifications) {
            String containerId = itemContainerByItemId.get(verification.getDataItemId());
            if (containerId == null) {
                continue;
            }
            verificationCountByContainer.merge(containerId, 1L, Long::sum);
        }

        Set<String> allContainerIds = new LinkedHashSet<>();
        allContainerIds.addAll(containersById.keySet());
        allContainerIds.addAll(itemCountByContainer.keySet());
        allContainerIds.addAll(verificationCountByContainer.keySet());

        return allContainerIds.stream()
                .map(containerId -> {
                    Container container = containersById.get(containerId);

                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("containerId", containerId);
                    row.put("containerName", container == null ? null : container.getName());
                    row.put("dataTypes", dataTypeCountByContainer.getOrDefault(containerId, 0L));
                    row.put("dataItems", itemCountByContainer.getOrDefault(containerId, 0L));
                    row.put("verifications", verificationCountByContainer.getOrDefault(containerId, 0L));
                    return row;
                })
                .sorted(Comparator
                        .comparingLong((Map<String, Object> row) -> asLong(row.get("dataItems"))).reversed()
                        .thenComparingLong((Map<String, Object> row) -> asLong(row.get("verifications"))).reversed()
                        .thenComparing(row -> Objects.toString(row.get("containerName"), ""), String.CASE_INSENSITIVE_ORDER)
                )
                .limit(topN)
                .toList();
    }

    private List<Map<String, Object>> buildTopAddresses(
            List<?> rows,
            boolean items,
            int topN
    ) {
        Map<String, Long> counts = new HashMap<>();

        if (items) {
            for (Object row : rows) {
                DataItem dataItem = (DataItem) row;
                String address = normalizeAddress(dataItem.getCreator() == null ? null : dataItem.getCreator().getCreatorAddr());
                if (address == null) {
                    continue;
                }
                counts.merge(address, 1L, Long::sum);
            }
        } else {
            for (Object row : rows) {
                DataItemVerification verification = (DataItemVerification) row;
                String address = normalizeAddress(
                        verification.getCreator() == null ? null : verification.getCreator().getCreatorAddr()
                );
                if (address == null) {
                    continue;
                }
                counts.merge(address, 1L, Long::sum);
            }
        }

        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(topN)
                .map(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("address", entry.getKey());
                    row.put("count", entry.getValue());
                    if (items) {
                        row.put("dataItems", entry.getValue());
                    } else {
                        row.put("verifications", entry.getValue());
                    }
                    return row;
                })
                .toList();
    }

    private Map<String, Object> buildLatestPublishedGraph(
            Map<String, Container> containersById,
            List<DataType> dataTypes,
            List<DataItem> scopedDataItems,
            List<DataItemVerification> scopedVerifications,
            int graphLimit
    ) {
        List<DataItem> latestItems = scopedDataItems.stream()
                .sorted(AnalyticsDashboardService::compareDataItemsByRecency)
                .limit(graphLimit)
                .toList();

        Map<String, DataType> dataTypesById = dataTypes.stream()
                .filter(dataType -> normalizeBlank(dataType.getId()) != null)
                .collect(Collectors.toMap(
                        DataType::getId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Map<String, List<DataItemVerification>> verificationsByItemId = scopedVerifications.stream()
                .filter(verification -> normalizeBlank(verification.getDataItemId()) != null)
                .collect(Collectors.groupingBy(
                        verification -> normalizeBlank(verification.getDataItemId()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        Map<String, Map<String, Object>> nodesById = new LinkedHashMap<>();
        Map<String, Map<String, Object>> edgesById = new LinkedHashMap<>();

        for (DataItem dataItem : latestItems) {
            String dataItemId = normalizeBlank(dataItem.getId());
            if (dataItemId == null) {
                continue;
            }

            String containerId = normalizeBlank(dataItem.getContainerId());
            String dataTypeId = normalizeBlank(dataItem.getDataTypeId());
            Container container = containerId == null ? null : containersById.get(containerId);
            DataType dataType = dataTypeId == null ? null : dataTypesById.get(dataTypeId);

            if (containerId != null) {
                addLatestGraphNode(
                        nodesById,
                        containerId,
                        buildGraphNodeLabel(container == null ? null : container.getName(), containerId),
                        0,
                        "container"
                );
            }

            if (dataTypeId != null) {
                addLatestGraphNode(
                        nodesById,
                        dataTypeId,
                        buildGraphNodeLabel(dataType == null ? null : dataType.getName(), dataTypeId),
                        1,
                        "data_type"
                );
            }

            addLatestGraphNode(
                    nodesById,
                    dataItemId,
                    buildGraphNodeLabel(dataItem.getName(), dataItemId),
                    2,
                    "data_item"
            );

            if (containerId != null && dataTypeId != null) {
                addLatestGraphEdge(edgesById, containerId, dataTypeId, "contains_type");
            }

            if (dataTypeId != null) {
                addLatestGraphEdge(edgesById, dataTypeId, dataItemId, "contains_item");
            }

            for (DataItemVerification verification : verificationsByItemId.getOrDefault(dataItemId, List.of())) {
                String verificationId = normalizeBlank(verification.getId());
                if (verificationId == null) {
                    continue;
                }

                addLatestGraphNode(
                        nodesById,
                        verificationId,
                        buildGraphNodeLabel(verification.getName(), verificationId),
                        3,
                        "data_item_verification"
                );
                addLatestGraphEdge(edgesById, dataItemId, verificationId, "has_verification");
            }
        }

        long containerCount = countLatestGraphNodesByKind(nodesById, "container");
        long typeCount = countLatestGraphNodesByKind(nodesById, "data_type");
        long itemCount = countLatestGraphNodesByKind(nodesById, "data_item");
        long verificationCount = countLatestGraphNodesByKind(nodesById, "data_item_verification");

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("containers", containerCount);
        summary.put("dataTypes", typeCount);
        summary.put("dataItems", itemCount);
        summary.put("verifications", verificationCount);

        List<String> infoParts = new ArrayList<>();
        if (latestItems.isEmpty()) {
            infoParts.add("No published items in selected scope and date range.");
        } else if (scopedDataItems.size() > latestItems.size()) {
            infoParts.add("Showing latest " + latestItems.size() + " of " + scopedDataItems.size() + " items.");
        }
        if (!latestItems.isEmpty() && verificationCount == 0) {
            infoParts.add("No linked verifications found for this latest-item window.");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("limit", graphLimit);
        payload.put("windowDataItems", latestItems.size());
        payload.put("totalScopedDataItems", scopedDataItems.size());
        payload.put("nodes", new ArrayList<>(nodesById.values()));
        payload.put("edges", new ArrayList<>(edgesById.values()));
        payload.put("summary", summary);
        if (!infoParts.isEmpty()) {
            payload.put("info", String.join(" ", infoParts));
        }
        return payload;
    }

    private static void addLatestGraphNode(
            Map<String, Map<String, Object>> nodesById,
            String nodeIdRaw,
            String labelRaw,
            int level,
            String kind
    ) {
        String nodeId = normalizeBlank(nodeIdRaw);
        if (nodeId == null) {
            return;
        }

        String nodeKey = nodeId.toLowerCase(Locale.ROOT);
        if (nodesById.containsKey(nodeKey)) {
            return;
        }

        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", nodeId);
        node.put("label", normalizeBlank(labelRaw) == null ? nodeId : labelRaw.trim());
        node.put("level", level);
        node.put("kind", kind);
        nodesById.put(nodeKey, node);
    }

    private static void addLatestGraphEdge(
            Map<String, Map<String, Object>> edgesById,
            String fromRaw,
            String toRaw,
            String relationRaw
    ) {
        String from = normalizeBlank(fromRaw);
        String to = normalizeBlank(toRaw);
        String relation = normalizeBlank(relationRaw);
        if (from == null || to == null || relation == null) {
            return;
        }

        String edgeKey = from.toLowerCase(Locale.ROOT) + "->" + to.toLowerCase(Locale.ROOT) + ":" + relation;
        if (edgesById.containsKey(edgeKey)) {
            return;
        }

        Map<String, Object> edge = new LinkedHashMap<>();
        edge.put("from", from);
        edge.put("to", to);
        edge.put("relation", relation);
        edgesById.put(edgeKey, edge);
    }

    private static long countLatestGraphNodesByKind(
            Map<String, Map<String, Object>> nodesById,
            String kind
    ) {
        return nodesById.values().stream()
                .filter(node -> Objects.equals(kind, node.get("kind")))
                .count();
    }

    private static String buildGraphNodeLabel(String nameRaw, String id) {
        String name = normalizeBlank(nameRaw);
        if (name == null) {
            return id;
        }
        return name + " (" + abbreviateObjectId(id) + ")";
    }

    private static int compareDataItemsByRecency(DataItem left, DataItem right) {
        Long leftEpochMs = extractCreatorTimestampMs(left.getCreator());
        Long rightEpochMs = extractCreatorTimestampMs(right.getCreator());
        long leftTimestamp = leftEpochMs == null ? Long.MIN_VALUE : leftEpochMs;
        long rightTimestamp = rightEpochMs == null ? Long.MIN_VALUE : rightEpochMs;

        int byTimestamp = Long.compare(rightTimestamp, leftTimestamp);
        if (byTimestamp != 0) {
            return byTimestamp;
        }

        int bySequence = compareBigIntegerDesc(left.getSequenceIndex(), right.getSequenceIndex());
        if (bySequence != 0) {
            return bySequence;
        }

        int byExternalIndex = compareBigIntegerDesc(left.getExternalIndex(), right.getExternalIndex());
        if (byExternalIndex != 0) {
            return byExternalIndex;
        }

        return Objects.toString(left.getId(), "").compareToIgnoreCase(Objects.toString(right.getId(), ""));
    }

    private static int compareBigIntegerDesc(BigInteger left, BigInteger right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return right.compareTo(left);
    }

    private static String abbreviateObjectId(String value) {
        String normalized = normalizeBlank(value);
        if (normalized == null || normalized.length() <= 18) {
            return normalized == null ? "" : normalized;
        }
        return normalized.substring(0, 8) + "..." + normalized.substring(normalized.length() - 8);
    }

    private static Set<String> collectActiveAddresses(
            List<DataItem> dataItems,
            List<DataItemVerification> verifications
    ) {
        Set<String> addresses = new LinkedHashSet<>();

        for (DataItem dataItem : dataItems) {
            String address = normalizeAddress(dataItem.getCreator() == null ? null : dataItem.getCreator().getCreatorAddr());
            if (address != null) {
                addresses.add(address);
            }
        }

        for (DataItemVerification verification : verifications) {
            String address = normalizeAddress(
                    verification.getCreator() == null ? null : verification.getCreator().getCreatorAddr()
            );
            if (address != null) {
                addresses.add(address);
            }
        }

        return addresses;
    }

    private void addToBucket(
            Map<LocalDate, BucketCounter> buckets,
            Granularity granularity,
            LocalDate sourceDate,
            java.util.function.Consumer<BucketCounter> consumer
    ) {
        if (sourceDate == null) {
            return;
        }

        LocalDate bucketDate = switch (granularity) {
            case DAY -> sourceDate;
            case MONTH -> sourceDate.withDayOfMonth(1);
            case YEAR -> sourceDate.withDayOfYear(1);
        };

        BucketCounter bucket = buckets.computeIfAbsent(bucketDate, ignored -> new BucketCounter());
        consumer.accept(bucket);
    }

    private static String formatBucketLabel(LocalDate bucketDate, Granularity granularity) {
        return switch (granularity) {
            case DAY -> bucketDate.format(ISO_DATE);
            case MONTH -> bucketDate.format(MONTH_LABEL);
            case YEAR -> bucketDate.format(YEAR_LABEL);
        };
    }

    private Set<String> resolveVisibleContainerIds(String userAddress, Set<String> followedContainerIds) {
        Set<String> ids = new LinkedHashSet<>();

        List<Container> owned = containerRepository.findByCreator_creatorAddr(userAddress);
        for (Container container : owned) {
            if (container.getId() != null) {
                ids.add(container.getId());
            }
        }

        ids.addAll(followedContainerIds);

        List<Container> accessibleFromLegacyFollow = containerRepository.findAccessibleContainers(userAddress, Pageable.unpaged());
        for (Container container : accessibleFromLegacyFollow) {
            if (container.getId() != null) {
                ids.add(container.getId());
            }
        }

        return ids;
    }

    private Set<String> resolveFollowedContainerIds(String userAddress) {
        List<OffchainFollowContainer> actions =
                offchainFollowContainerRepository.findByActorAddressOrderBySequenceIndexDescIdDesc(userAddress);

        LinkedHashMap<String, Boolean> latestByContainer = new LinkedHashMap<>();
        for (OffchainFollowContainer action : actions) {
            String containerId = action.getTargetContainerId();
            if (containerId == null || latestByContainer.containsKey(containerId)) {
                continue;
            }
            latestByContainer.put(containerId, action.isEnabled());
        }

        Set<String> active = latestByContainer.entrySet().stream()
                .filter(entry -> Boolean.TRUE.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (!active.isEmpty()) {
            return active;
        }

        return followedContainerRepository.findByUserAddress(userAddress).stream()
                .map(FollowedContainer::getContainerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Long extractCreatorTimestampMs(Creator creator) {
        if (creator == null) {
            return null;
        }

        Long epochMs = normalizeEpochToMs(creator.getCreatorTimestampMs());
        if (epochMs == null || epochMs <= 0) {
            epochMs = normalizeEpochToMs(creator.getCreatorUpdateTimestampMs());
        }

        if (epochMs == null || epochMs <= 0) {
            return null;
        }

        return epochMs;
    }

    private static LocalDate extractCreatorDate(Creator creator, ZoneId zoneId) {
        Long epochMs = extractCreatorTimestampMs(creator);
        if (epochMs == null) {
            return null;
        }

        try {
            return Instant.ofEpochMilli(epochMs)
                    .atZone(zoneId)
                    .toLocalDate();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isInDateRange(LocalDate date, LocalDate from, LocalDate to) {
        if (date == null) {
            return from == null && to == null;
        }

        if (from != null && date.isBefore(from)) {
            return false;
        }

        if (to != null && date.isAfter(to)) {
            return false;
        }

        return true;
    }

    private static Long normalizeEpochToMs(BigInteger rawValue) {
        if (rawValue == null || rawValue.signum() <= 0) {
            return null;
        }

        long raw;
        try {
            raw = rawValue.longValueExact();
        } catch (ArithmeticException ignored) {
            return null;
        }

        if (raw > 100_000_000_000_000_000L) {
            return raw / 1_000_000L;
        }

        if (raw > 100_000_000_000_000L) {
            return raw / 1_000L;
        }

        if (raw < 1_000_000_000_000L) {
            return raw * 1_000L;
        }

        return raw;
    }

    private static ZoneId parseZoneId(String timezoneRaw) {
        String timezone = normalizeBlank(timezoneRaw);
        if (timezone == null) {
            return ZoneOffset.UTC;
        }

        try {
            return ZoneId.of(timezone);
        } catch (Exception ignored) {
            return ZoneOffset.UTC;
        }
    }

    private static LocalDate parseIsoDateOrNull(String raw, String fieldName) {
        String value = normalizeBlank(raw);
        if (value == null) {
            return null;
        }

        try {
            return LocalDate.parse(value, ISO_DATE);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(fieldName + " must use YYYY-MM-DD format");
        }
    }

    private static Granularity parseGranularity(String raw) {
        String normalized = normalizeBlank(raw);
        if (normalized == null) {
            return Granularity.MONTH;
        }

        return switch (normalized.toLowerCase(Locale.ROOT)) {
            case "day" -> Granularity.DAY;
            case "year" -> Granularity.YEAR;
            case "month" -> Granularity.MONTH;
            default -> Granularity.MONTH;
        };
    }

    private static int clampTopN(Integer rawTopN) {
        if (rawTopN == null) {
            return DEFAULT_TOP_N;
        }
        return Math.max(MIN_TOP_N, Math.min(MAX_TOP_N, rawTopN));
    }

    private static int clampGraphLimit(Integer rawGraphLimit) {
        if (rawGraphLimit == null) {
            return DEFAULT_GRAPH_LIMIT;
        }
        return Math.max(MIN_GRAPH_LIMIT, Math.min(MAX_GRAPH_LIMIT, rawGraphLimit));
    }

    private static String normalizeBlank(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeAddress(String value) {
        String normalized = normalizeBlank(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private static boolean equalsAddress(String actual, String expectedNormalized) {
        return Objects.equals(normalizeAddress(actual), expectedNormalized);
    }

    private static boolean equalsIgnoreCase(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return left.equalsIgnoreCase(right);
    }

    private static long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }
}
