package com.easypublish.service;

import com.easypublish.dtos.ContainerNodeDto;
import com.easypublish.dtos.ContainerTreeDto;
import com.easypublish.dtos.ContainerTreeIncludeEnum;
import com.easypublish.dtos.DataItemNodeDto;
import com.easypublish.dtos.DataItemRevisionDto;
import com.easypublish.dtos.DataTypeNodeDto;
import com.easypublish.entities.offchain.OffchainDataItemRevision;
import com.easypublish.entities.UserDataEntity;
import com.easypublish.entities.onchain.Container;
import com.easypublish.entities.onchain.DataItem;
import com.easypublish.entities.onchain.DataItemVerification;
import com.easypublish.entities.onchain.DataType;
import com.easypublish.repositories.ContainerRepository;
import com.easypublish.repositories.DataItemRepository;
import com.easypublish.repositories.DataItemVerificationRepository;
import com.easypublish.repositories.DataTypeRepository;
import com.easypublish.repositories.OffchainDataItemRevisionRepository;
import com.easypublish.repositories.PublishTargetRepository;
import com.easypublish.repositories.UserDataRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service responsible for:
 * 1. executing Node.js helper scripts for dynamic fetches
 * 2. serving normalized tree views from DB-backed entities
 */
@Service
public class NodeService {

    private static final Logger log = LoggerFactory.getLogger(NodeService.class);

    private static final String TYPE_CONTAINER = "container";
    private static final String TYPE_DATA_TYPE = "data_type";
    private static final String TYPE_DATA_ITEM = "data_item";
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

    private final UserDataRepository userRepository;
    private final ObjectMapper mapper;
    private final DataItemRepository dataItemRepository;
    private final DataTypeRepository dataTypeRepository;
    private final ContainerRepository containerRepository;
    private final DataItemVerificationRepository dataItemVerificationRepository;
    private final OffchainDataItemRevisionRepository offchainDataItemRevisionRepository;
    private final PublishTargetRepository publishTargetRepository;
    private final NodeQueryService nodeQueryService;

    // Extracted to properties for environment portability:
    // app.node.binary, app.node.items-script, app.node.directory
    private final String nodeBinary;
    private final String nodeScript;
    private final File nodeDirectory;
    private final String iotaNetwork;
    private final String iotaRpcUrls;
    private final int iotaRpcAttemptsPerUrl;
    private final int iotaRpcRetryDelayMs;

    public NodeService(
            UserDataRepository userRepository,
            ObjectMapper mapper,
            DataItemRepository dataItemRepository,
            DataTypeRepository dataTypeRepository,
            ContainerRepository containerRepository,
            DataItemVerificationRepository dataItemVerificationRepository,
            OffchainDataItemRevisionRepository offchainDataItemRevisionRepository,
            PublishTargetRepository publishTargetRepository,
            NodeQueryService nodeQueryService,
            @Value("${app.node.binary:node}") String nodeBinary,
            @Value("${app.node.items-script:getItems.js}") String nodeScript,
            @Value("${app.node.directory:./node}") String nodeDirectory,
            @Value("${app.iota.network:testnet}") String iotaNetwork,
            @Value("${app.iota.rpc-urls:}") String iotaRpcUrls,
            @Value("${app.iota.rpc-attempts-per-url:2}") int iotaRpcAttemptsPerUrl,
            @Value("${app.iota.rpc-retry-delay-ms:400}") int iotaRpcRetryDelayMs
    ) {
        this.userRepository = userRepository;
        this.mapper = mapper;
        this.dataItemRepository = dataItemRepository;
        this.dataTypeRepository = dataTypeRepository;
        this.containerRepository = containerRepository;
        this.dataItemVerificationRepository = dataItemVerificationRepository;
        this.offchainDataItemRevisionRepository = offchainDataItemRevisionRepository;
        this.publishTargetRepository = publishTargetRepository;
        this.nodeQueryService = nodeQueryService;
        this.nodeBinary = nodeBinary;
        this.nodeScript = nodeScript;
        this.nodeDirectory = new File(nodeDirectory);
        this.iotaNetwork = iotaNetwork;
        this.iotaRpcUrls = iotaRpcUrls;
        this.iotaRpcAttemptsPerUrl = iotaRpcAttemptsPerUrl;
        this.iotaRpcRetryDelayMs = iotaRpcRetryDelayMs;
    }

    /**
     * Wrapper for frontend: validates required inputs before delegating to Node script.
     */
    public List<Map<String, Object>> getContainerItemsAsList(
            String containerId,
            String type,
            String userAddress
    ) throws Exception {

        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type is required");
        }

        if (userAddress == null || userAddress.isBlank()) {
            throw new IllegalArgumentException("userAddress is required");
        }

        if (!TYPE_CONTAINER.equals(type) && isBlankOrUndefined(containerId)) {
            throw new IllegalArgumentException("containerId is required for type=" + type);
        }

        return getContainerItems(containerId, type, userAddress);
    }

    /**
     * Executes the configured Node.js script and parses JSON response.
     */
    public List<Map<String, Object>> getContainerItems(
            String containerId,
            String type,
            String userAddress
    ) throws Exception {

        UserDataEntity user = userRepository.findByAddress(userAddress)
                .orElseThrow(() -> new RuntimeException("User not found: " + userAddress));

        List<String> command = new ArrayList<>();
        command.add(nodeBinary);
        command.add(nodeScript);

        if (!TYPE_CONTAINER.equals(type)) {
            command.add(containerId);
        }

        command.add(type);
        command.add(user.getAddress());

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(nodeDirectory);
        pb.redirectErrorStream(true);
        if (normalizeBlank(iotaNetwork) != null) {
            pb.environment().put("IOTA_NETWORK", iotaNetwork.trim());
        }
        if (normalizeBlank(iotaRpcUrls) != null) {
            pb.environment().put("IOTA_RPC_URLS", iotaRpcUrls.trim());
        }
        if (iotaRpcAttemptsPerUrl > 0) {
            pb.environment().put("IOTA_RPC_ATTEMPTS_PER_URL", String.valueOf(iotaRpcAttemptsPerUrl));
        }
        if (iotaRpcRetryDelayMs >= 0) {
            pb.environment().put("IOTA_RPC_RETRY_DELAY_MS", String.valueOf(iotaRpcRetryDelayMs));
        }

        Process process = pb.start();
        String output = collectProcessOutput(process);
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            log.error("Node process failed (exitCode={}):\n{}", exitCode, output);
            throw new RuntimeException("Node process failed, exit code: " + exitCode);
        }

        try {
            JsonNode root = mapper.readTree(output);
            JsonNode itemsNode = root;
            if (!root.isArray()) {
                itemsNode = root.path("items");
            }

            if (!itemsNode.isArray()) {
                throw new IllegalStateException("Expected JSON array or object with array field 'items'");
            }

            return mapper.convertValue(itemsNode, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.error("Invalid JSON from Node:\n{}", output);
            throw new RuntimeException("Invalid JSON from Node", e);
        }
    }

    /**
     * Reserved switch for future DB-native mode.
     */
    public List<Map<String, Object>> getContainerItems(
            String containerId,
            String type,
            String userAddress,
            boolean useDb
    ) throws Exception {
        return getContainerItems(containerId, type, userAddress);
    }

    /**
     * Returns DB objects normalized into a common frontend-friendly map format.
     */
    public List<?> getContainerItemsFromDBNormalized(
            EnumSet<ContainerTreeIncludeEnum> includes,
            String userAddr,
            String containerId,
            String dataTypeId
    ) {
        if (includes == null || includes.isEmpty()) {
            throw new IllegalArgumentException("includes must not be empty");
        }

        String type;
        if (includes.contains(ContainerTreeIncludeEnum.DATA_ITEM)) {
            type = TYPE_DATA_ITEM;
        } else if (includes.contains(ContainerTreeIncludeEnum.DATA_TYPE)) {
            type = TYPE_DATA_TYPE;
        } else if (includes.contains(ContainerTreeIncludeEnum.CONTAINER)) {
            type = TYPE_CONTAINER;
        } else {
            throw new IllegalArgumentException("Unsupported include set: " + includes);
        }

        List<?> items = getContainerItemsFromDB(type, userAddr, containerId, dataTypeId);
        List<Map<String, Object>> normalized = new ArrayList<>();

        for (Object item : items) {
            if (item == null) {
                continue;
            }

            Map<String, Object> fieldsMap = new HashMap<>();
            String objectId = "unknown";

            try {
                if (item instanceof DataType dt) {
                    fieldsMap.put("id", dt.getId());
                    fieldsMap.put("containerId", dt.getContainerId());
                    fieldsMap.put("name", dt.getName());
                    fieldsMap.put("description", dt.getDescription());
                    fieldsMap.put("creator", dt.getCreator());
                    fieldsMap.put("specification", dt.getSpecification());
                    objectId = dt.getId();
                } else if (item instanceof DataItem di) {
                    Map<String, Object> diMap = new HashMap<>();
                    diMap.put("id", di.getId());
                    diMap.put("containerId", di.getContainerId());
                    diMap.put("dataTypeId", di.getDataTypeId());
                    diMap.put("name", di.getName());
                    diMap.put("description", di.getDescription());
                    diMap.put("content", di.getContent());
                    diMap.put("references", di.getReferences());
                    diMap.put("revision", buildRevisionDto(di));
                    diMap.put("creator", di.getCreator());
                    diMap.put("prevId", di.getPrevId());
                    diMap.put("prevDataTypeItemId", di.getPrevDataTypeItemId());
                    diMap.put("sequenceIndex", di.getSequenceIndex());
                    diMap.put("externalIndex", di.getExternalIndex());
                    diMap.put("prevDataItemChainId", di.getPrevDataItemChainId());

                    if (includes.contains(ContainerTreeIncludeEnum.DATA_TYPE) && di.getDataType() != null) {
                        Map<String, Object> dtMap = new HashMap<>();
                        dtMap.put("id", di.getDataType().getId());
                        dtMap.put("containerId", di.getDataType().getContainerId());
                        dtMap.put("name", di.getDataType().getName());
                        dtMap.put("description", di.getDataType().getDescription());
                        dtMap.put("content", di.getDataType().getContent());
                        dtMap.put("specification", di.getDataType().getSpecification());
                        dtMap.put("sequenceIndex", di.getDataType().getSequenceIndex());
                        dtMap.put("externalIndex", di.getDataType().getExternalIndex());
                        dtMap.put("lastDataItemId", di.getDataType().getLastDataItemId());
                        dtMap.put("prevId", di.getDataType().getPrevId());
                        diMap.put("dataType", dtMap);
                    }

                    fieldsMap.put("dataItem", diMap);
                    objectId = di.getId();
                } else if (item instanceof Container c) {
                    fieldsMap.put("id", c.getId());
                    fieldsMap.put("name", c.getName());
                    fieldsMap.put("externalId", c.getExternalId());
                    fieldsMap.put("creator", c.getCreator());
                    objectId = c.getId();
                } else {
                    fieldsMap.put("object", item);
                }
            } catch (Exception e) {
                log.warn("Failed to normalize item: {}", item, e);
                continue;
            }

            Map<String, Object> map = new HashMap<>();
            map.put("object_id", objectId != null ? objectId : "unknown");
            map.put("fields", fieldsMap);
            normalized.add(map);
        }

        return normalized;
    }

    @Transactional(readOnly = true)
    public Container getContainer(String id) {
        return containerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Container not found: " + id));
    }

    @Transactional(readOnly = true)
    public DataType getDataType(String id) {
        return dataTypeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("DataType not found: " + id));
    }

    @Transactional(readOnly = true)
    public DataItem getDataItem(String id) {
        return dataItemRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("DataItem not found: " + id));
    }

    /**
     * Returns paginated container tree payload for the API.
     */
    @Transactional(readOnly = true)
    public ContainerTreeDto getContainerTree(
            String containerId,
            String dataTypeId,
            String dataItemId,
            String dataItemVerificationId,
            Boolean dataItemVerificationVerified,
            String creatorAddr,
            String domain,
            int page,
            int pageSize,
            EnumSet<ContainerTreeIncludeEnum> includes
    ) {
        EnumSet<ContainerTreeIncludeEnum> safeIncludes =
                includes == null
                        ? EnumSet.of(ContainerTreeIncludeEnum.CONTAINER)
                        : EnumSet.copyOf(includes);
        safeIncludes.add(ContainerTreeIncludeEnum.CONTAINER);

        boolean includeDataTypes = safeIncludes.contains(ContainerTreeIncludeEnum.DATA_TYPE)
                || safeIncludes.contains(ContainerTreeIncludeEnum.DATA_ITEM);
        boolean includeDataItems = safeIncludes.contains(ContainerTreeIncludeEnum.DATA_ITEM);
        boolean includeDataItemVerifications = safeIncludes.contains(ContainerTreeIncludeEnum.DATA_ITEM_VERIFICATION)
                && includeDataItems;

        String normalizedDomain = (domain == null || domain.isBlank()) ? null : domain;
        String normalizedContainerId = normalizeBlank(containerId);
        String normalizedDataTypeId = normalizeBlank(dataTypeId);
        String normalizedDataItemId = normalizeBlank(dataItemId);
        String normalizedDataItemVerificationId = normalizeBlank(dataItemVerificationId);

        if (normalizedContainerId == null) {
            Pageable pageable = PageRequest.of(page, pageSize);
            Page<Container> containerPage = normalizedDomain != null
                    ? containerRepository.findByPublishTargetDomainPage(normalizedDomain, pageable)
                    : containerRepository.findAccessibleContainersPage(creatorAddr, pageable);

            List<ContainerNodeDto> containerNodes = containerPage.getContent().stream()
                    .map(container -> new ContainerNodeDto(container, List.of()))
                    .toList();

            Map<String, Object> meta = buildMeta(
                    "container",
                    page,
                    pageSize,
                    safeIncludes,
                    normalizedContainerId,
                    normalizedDataTypeId,
                    normalizedDataItemId,
                    normalizedDataItemVerificationId,
                    dataItemVerificationVerified,
                    normalizedDomain,
                    containerPage.getTotalElements(),
                    containerNodes.size(),
                    0L,
                    0L,
                    0L,
                    containerPage.getTotalPages(),
                    containerPage.hasNext(),
                    false
            );

            return new ContainerTreeDto(containerNodes, meta);
        }

        Container container = containerRepository.findById(normalizedContainerId)
                .orElseThrow(() -> new IllegalArgumentException("Container not found: " + normalizedContainerId));

        if (!includeDataTypes) {
            Map<String, Object> meta = buildMeta(
                    "container",
                    page,
                    pageSize,
                    safeIncludes,
                    normalizedContainerId,
                    normalizedDataTypeId,
                    normalizedDataItemId,
                    normalizedDataItemVerificationId,
                    dataItemVerificationVerified,
                    normalizedDomain,
                    1L,
                    1L,
                    0L,
                    0L,
                    0L,
                    1,
                    false,
                    false
            );
            return new ContainerTreeDto(List.of(new ContainerNodeDto(container, List.of())), meta);
        }

        List<DataType> allMatchingDataTypes = resolveDataTypes(
                normalizedContainerId,
                normalizedDataTypeId,
                normalizedDomain
        );
        List<String> dataTypeIds = allMatchingDataTypes.stream()
                .map(DataType::getId)
                .toList();

        if (!includeDataItems) {
            Pageable pageable = PageRequest.of(page, pageSize);
            Page<DataType> dataTypePage;

            if (normalizedDataTypeId != null) {
                List<DataType> single = allMatchingDataTypes;
                boolean outOfRange = page > 0;
                List<DataType> pagedContent = outOfRange ? List.of() : single;
                dataTypePage = new org.springframework.data.domain.PageImpl<>(
                        pagedContent,
                        pageable,
                        single.size()
                );
            } else if (normalizedDomain != null) {
                dataTypePage = dataTypeRepository.findByContainerAndPublishTargetDomainPage(
                        normalizedContainerId,
                        normalizedDomain,
                        pageable
                );
            } else {
                dataTypePage = dataTypeRepository.findByContainerPage(normalizedContainerId, pageable);
            }

            List<DataTypeNodeDto> typeNodes = dataTypePage.getContent().stream()
                    .map(dt -> new DataTypeNodeDto(dt, List.of()))
                    .toList();

            Map<String, Object> meta = buildMeta(
                    "data_type",
                    page,
                    pageSize,
                    safeIncludes,
                    normalizedContainerId,
                    normalizedDataTypeId,
                    normalizedDataItemId,
                    normalizedDataItemVerificationId,
                    dataItemVerificationVerified,
                    normalizedDomain,
                    1L,
                    1L,
                    dataTypePage.getTotalElements(),
                    0L,
                    0L,
                    dataTypePage.getTotalPages(),
                    dataTypePage.hasNext(),
                    false
            );

            return new ContainerTreeDto(List.of(new ContainerNodeDto(container, typeNodes)), meta);
        }

        List<DataItem> items;
        long totalDataItems;
        int totalPages;
        boolean hasNext;

        if (normalizedDataItemId != null) {
            items = resolveSingleDataItem(
                    normalizedContainerId,
                    dataTypeIds,
                    normalizedDataItemId,
                    normalizedDomain
            );
            totalDataItems = items.size();
            totalPages = totalDataItems > 0 ? 1 : 0;
            hasNext = false;
        } else if (dataTypeIds.isEmpty()) {
            items = List.of();
            totalDataItems = 0;
            totalPages = 0;
            hasNext = false;
        } else {
            Page<DataItem> itemPage = dataItemRepository.findByContainerIdAndDataTypeIdInAndOptionalDomain(
                    normalizedContainerId,
                    dataTypeIds,
                    normalizedDomain,
                    PageRequest.of(page, pageSize)
            );
            items = itemPage.getContent();
            totalDataItems = itemPage.getTotalElements();
            totalPages = itemPage.getTotalPages();
            hasNext = itemPage.hasNext();
        }

        List<String> itemIds = items.stream().map(DataItem::getId).toList();
        List<DataItemVerification> dataItemVerifications = includeDataItemVerifications && !itemIds.isEmpty()
                ? dataItemVerificationRepository.findByDataItemIdIn(itemIds)
                : List.of();

        if (normalizedDataItemVerificationId != null) {
            dataItemVerifications = dataItemVerifications.stream()
                    .filter(v -> normalizedDataItemVerificationId.equals(v.getId()))
                    .toList();
        }

        if (dataItemVerificationVerified != null) {
            dataItemVerifications = dataItemVerifications.stream()
                    .filter(v -> Objects.equals(dataItemVerificationVerified, v.getVerified()))
                    .toList();
        }

        Map<String, List<DataItemVerification>> dataItemVerificationsByItemId = dataItemVerifications.stream()
                .collect(Collectors.groupingBy(DataItemVerification::getDataItemId));

        boolean dataItemVerificationFiltered =
                normalizedDataItemVerificationId != null || dataItemVerificationVerified != null;
        if (dataItemVerificationFiltered) {
            items = items.stream()
                    .filter(di -> !dataItemVerificationsByItemId.getOrDefault(di.getId(), List.of()).isEmpty())
                    .toList();
        }
        Map<String, List<DataItem>> itemsByTypeId = items.stream()
                .collect(Collectors.groupingBy(DataItem::getDataTypeId));

        List<DataType> responseDataTypes = allMatchingDataTypes;
        if (normalizedDataItemId != null && !items.isEmpty()) {
            String selectedTypeId = items.get(0).getDataTypeId();
            responseDataTypes = allMatchingDataTypes.stream()
                    .filter(dt -> selectedTypeId.equals(dt.getId()))
                    .toList();
        }

        if (responseDataTypes.isEmpty() && normalizedDataItemId != null) {
            responseDataTypes = allMatchingDataTypes;
        }

        List<DataTypeNodeDto> typeNodes = responseDataTypes.stream()
                .map(dt -> {
                    List<DataItemNodeDto> itemDtos = itemsByTypeId.getOrDefault(dt.getId(), List.of()).stream()
                            .map(di -> new DataItemNodeDto(
                                    di,
                                    includeDataItemVerifications
                                            ? dataItemVerificationsByItemId.getOrDefault(di.getId(), List.of())
                                            : List.of(),
                                    buildRevisionDto(di)
                            ))
                            .toList();

                    return new DataTypeNodeDto(dt, itemDtos);
                })
                .toList();

        long returnedItems = items.size();
        long returnedDataItemVerifications = typeNodes.stream()
                .flatMap(dt -> dt.getDataItems().stream())
                .mapToLong(di -> di.getDataItemVerifications().size())
                .sum();

        Map<String, Object> meta = buildMeta(
                "data_item",
                page,
                pageSize,
                safeIncludes,
                normalizedContainerId,
                normalizedDataTypeId,
                normalizedDataItemId,
                normalizedDataItemVerificationId,
                dataItemVerificationVerified,
                normalizedDomain,
                1L,
                1L,
                responseDataTypes.size(),
                totalDataItems,
                returnedDataItemVerifications,
                totalPages,
                hasNext,
                dataItemVerificationFiltered
        );
        meta.put("returnedDataItems", returnedItems);

        return new ContainerTreeDto(List.of(new ContainerNodeDto(container, typeNodes)), meta);
    }

    /**
     * DB-native query branch used by normalization endpoint.
     */
    public List<?> getContainerItemsFromDB(
            String type,
            String creatorAddr,
            String containerId,
            String dataTypeId
    ) {
        return nodeQueryService.findByType(type, creatorAddr, containerId, dataTypeId);
    }

    private static boolean isBlankOrUndefined(String value) {
        return value == null || value.isBlank() || "undefined".equals(value);
    }

    private List<DataType> resolveDataTypes(String containerId, String dataTypeId, String domain) {
        if (dataTypeId != null) {
            return dataTypeRepository.findByIdAndContainer(dataTypeId, containerId)
                    .map(List::of)
                    .orElse(List.of());
        }
        if (domain != null) {
            return dataTypeRepository.findByContainerAndPublishTargetDomain(containerId, domain);
        }
        return dataTypeRepository.findByContainer(containerId);
    }

    private List<DataItem> resolveSingleDataItem(
            String containerId,
            List<String> dataTypeIds,
            String dataItemId,
            String domain
    ) {
        return dataItemRepository.findByIdAndContainerId(dataItemId, containerId)
                .filter(di -> dataTypeIds.isEmpty() || dataTypeIds.contains(di.getDataTypeId()))
                .filter(di -> domain == null || publishTargetRepository.findByDomainAndDataItemId(domain, di.getId()).isPresent())
                .map(List::of)
                .orElse(List.of());
    }

    private DataItemRevisionDto buildRevisionDto(DataItem dataItem) {
        List<OffchainDataItemRevision> indexedRows =
                offchainDataItemRevisionRepository.findByDataItemIdOrderByIdAsc(dataItem.getId());

        if (!indexedRows.isEmpty()) {
            boolean enabled = indexedRows.stream().anyMatch(OffchainDataItemRevision::isEnabled);
            List<String> replaces = indexedRows.stream()
                    .map(OffchainDataItemRevision::getReplacedDataItemId)
                    .filter(Objects::nonNull)
                    .filter(id -> !id.isBlank())
                    .distinct()
                    .toList();
            return new DataItemRevisionDto(enabled, replaces);
        }

        List<String> referenceIds = normalizeObjectIds(dataItem.getReferences());
        RevisionExtraction extraction = extractRevisionExtraction(dataItem.getContent(), referenceIds);
        return new DataItemRevisionDto(
                extraction.enabled(),
                extraction.replaces()
        );
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
                boolean enabled = true;
                Object enabledRaw = revisionsMap.containsKey("enabled")
                        ? revisionsMap.get("enabled")
                        : revisionsMap.get("active");
                if (enabledRaw instanceof Boolean enabledBoolean) {
                    enabled = enabledBoolean;
                }

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

    private static String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static Map<String, Object> buildMeta(
            String paginationLevel,
            int page,
            int pageSize,
            EnumSet<ContainerTreeIncludeEnum> includes,
            String containerId,
            String dataTypeId,
            String dataItemId,
            String dataItemVerificationId,
            Boolean dataItemVerificationVerified,
            String domain,
            long totalContainers,
            long returnedContainers,
            long totalDataTypes,
            long totalDataItems,
            long totalDataItemVerifications,
            int totalPages,
            boolean hasNext,
            boolean dataItemVerificationFilteredAfterPagination
    ) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("containerId", containerId);
        filters.put("dataTypeId", dataTypeId);
        filters.put("dataItemId", dataItemId);
        filters.put("dataItemVerificationId", dataItemVerificationId);
        filters.put("dataItemVerificationVerified", dataItemVerificationVerified);
        filters.put("domain", domain);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("paginationLevel", paginationLevel);
        meta.put("page", page);
        meta.put("pageSize", pageSize);
        meta.put("totalPages", totalPages);
        meta.put("hasNext", hasNext);
        meta.put("includes", includes.stream().map(Enum::name).toList());
        meta.put("filters", filters);
        meta.put("totalContainers", totalContainers);
        meta.put("returnedContainers", returnedContainers);
        meta.put("totalDataTypes", totalDataTypes);
        meta.put("totalDataItems", totalDataItems);
        meta.put("totalDataItemVerifications", totalDataItemVerifications);
        meta.put("dataItemVerificationFilteredAfterPagination", dataItemVerificationFilteredAfterPagination);
        return meta;
    }

    private static String collectProcessOutput(Process process) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}
