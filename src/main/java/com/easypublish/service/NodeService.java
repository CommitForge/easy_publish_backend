package com.easypublish.service;

import com.easypublish.dtos.ContainerNodeDto;
import com.easypublish.dtos.ContainerTreeDto;
import com.easypublish.dtos.ContainerTreeIncludeEnum;
import com.easypublish.dtos.DataItemNodeDto;
import com.easypublish.dtos.DataItemRevisionDto;
import com.easypublish.dtos.DataTypeNodeDto;
import com.easypublish.dtos.LinkGraphEdgeDto;
import com.easypublish.dtos.LinkGraphNodeDto;
import com.easypublish.dtos.LinkGraphRequestDto;
import com.easypublish.dtos.LinkGraphResponseDto;
import com.easypublish.entities.offchain.OffchainDataItemRevision;
import com.easypublish.entities.UserDataEntity;
import com.easypublish.entities.onchain.Container;
import com.easypublish.entities.onchain.ContainerChildLink;
import com.easypublish.entities.onchain.DataItem;
import com.easypublish.entities.onchain.DataItemVerification;
import com.easypublish.entities.onchain.DataType;
import com.easypublish.entities.onchain.Owner;
import com.easypublish.repositories.ContainerChildLinkRepository;
import com.easypublish.repositories.ContainerRepository;
import com.easypublish.repositories.DataItemRepository;
import com.easypublish.repositories.DataItemVerificationRepository;
import com.easypublish.repositories.DataTypeRepository;
import com.easypublish.repositories.OffchainDataItemRevisionRepository;
import com.easypublish.repositories.OwnerRepository;
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
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
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
            "revisionOf",
            "revision_of"
    );
    private static final EnumSet<DataItemSearchField> DEFAULT_DATA_ITEM_SEARCH_FIELDS = EnumSet.of(
            DataItemSearchField.NAME,
            DataItemSearchField.DESCRIPTION,
            DataItemSearchField.EXTERNAL_ID,
            DataItemSearchField.EXTERNAL_INDEX
    );
    private static final EnumSet<ContainerChildLinkSearchField>
            DEFAULT_CONTAINER_CHILD_LINK_SEARCH_FIELDS = EnumSet.of(
            ContainerChildLinkSearchField.NAME,
            ContainerChildLinkSearchField.DESCRIPTION,
            ContainerChildLinkSearchField.EXTERNAL_ID,
            ContainerChildLinkSearchField.EXTERNAL_INDEX,
            ContainerChildLinkSearchField.PARENT_CONTAINER_ID,
            ContainerChildLinkSearchField.CHILD_CONTAINER_ID
    );
    private static final EnumSet<OwnerSearchField> DEFAULT_OWNER_SEARCH_FIELDS = EnumSet.of(
            OwnerSearchField.ADDRESS,
            OwnerSearchField.ROLE,
            OwnerSearchField.CONTAINER_ID,
            OwnerSearchField.CONTAINER_NAME
    );

    private enum DataItemSearchField {
        NAME,
        DESCRIPTION,
        CONTENT,
        EXTERNAL_ID,
        EXTERNAL_INDEX,
        OBJECT_ID,
        DATA_TYPE,
        CREATOR_ADDR
    }

    private enum DataItemSortBy {
        CREATED,
        NAME,
        EXTERNAL_INDEX,
        EXTERNAL_ID
    }

    private enum ContainerChildLinkSearchField {
        NAME,
        DESCRIPTION,
        CONTENT,
        EXTERNAL_ID,
        EXTERNAL_INDEX,
        OBJECT_ID,
        PARENT_CONTAINER_ID,
        CHILD_CONTAINER_ID,
        CREATOR_ADDR
    }

    private enum ContainerChildLinkSortBy {
        CREATED,
        NAME,
        EXTERNAL_INDEX,
        EXTERNAL_ID
    }

    private enum OwnerSearchField {
        ADDRESS,
        ROLE,
        CONTAINER_ID,
        CONTAINER_NAME,
        OBJECT_ID,
        CREATOR_ADDR,
        REMOVED
    }

    private enum OwnerSortBy {
        CREATED,
        ADDRESS,
        ROLE,
        CONTAINER_NAME
    }

    private enum OwnerStatus {
        ALL,
        ACTIVE,
        REMOVED
    }

    private enum RecipientScope {
        ALL,
        MINE,
        OTHERS,
        WITH_RECIPIENTS
    }

    private enum ContainerScope {
        ACCESSIBLE,
        ALL
    }

    private enum LinkGraphMode {
        RECIPIENTS,
        REFERENCES
    }

    private enum LinkSourceType {
        DATA_ITEM,
        DATA_ITEM_VERIFICATION
    }

    private static final int LINK_GRAPH_DEFAULT_MAX_DEPTH = 3;
    private static final int LINK_GRAPH_MAX_DEPTH = 8;
    private static final int LINK_GRAPH_DEFAULT_MAX_NODES = 160;
    private static final int LINK_GRAPH_MAX_NODES = 500;
    private static final int LINK_GRAPH_MAX_EDGES_FACTOR = 3;

    private record DataItemFilterOptions(
            String query,
            EnumSet<DataItemSearchField> searchFields,
            Boolean verified,
            Boolean hasRevisions,
            Boolean hasVerifications,
            String dataType,
            DataItemSortBy sortBy,
            boolean sortAscending
    ) {}

    private record ContainerChildLinkFilterOptions(
            String query,
            EnumSet<ContainerChildLinkSearchField> searchFields,
            ContainerChildLinkSortBy sortBy,
            boolean sortAscending
    ) {}

    private record OwnerFilterOptions(
            String query,
            EnumSet<OwnerSearchField> searchFields,
            OwnerSortBy sortBy,
            boolean sortAscending,
            OwnerStatus status
    ) {}

    private record RevisionExtraction(boolean enabled, List<String> replaces) {}

    private record GraphEntityMetadata(
            String id,
            String kind,
            String containerId,
            String dataItemId,
            String name
    ) {}

    private record GraphFrontierNode(String value, int level) {}

    private record GraphNeighbor(String target, String relation, String kind) {}

    private final UserDataRepository userRepository;
    private final ObjectMapper mapper;
    private final DataItemRepository dataItemRepository;
    private final DataTypeRepository dataTypeRepository;
    private final ContainerRepository containerRepository;
    private final ContainerChildLinkRepository containerChildLinkRepository;
    private final DataItemVerificationRepository dataItemVerificationRepository;
    private final OwnerRepository ownerRepository;
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
            ContainerChildLinkRepository containerChildLinkRepository,
            DataItemVerificationRepository dataItemVerificationRepository,
            OwnerRepository ownerRepository,
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
        this.containerChildLinkRepository = containerChildLinkRepository;
        this.dataItemVerificationRepository = dataItemVerificationRepository;
        this.ownerRepository = ownerRepository;
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

    @Transactional(readOnly = true)
    public Map<String, Object> getContainerChildLinks(
            String creatorAddr,
            String containerId,
            String containerScope,
            String query,
            String searchFields,
            String sortBy,
            String sortDirection,
            String domain,
            int page,
            int pageSize
    ) {
        String normalizedContainerId = normalizeBlank(containerId);
        String normalizedDomain = normalizeBlank(domain);
        ContainerScope normalizedContainerScope = parseContainerScope(containerScope);
        ContainerChildLinkFilterOptions filterOptions =
                parseContainerChildLinkFilterOptions(query, searchFields, sortBy, sortDirection);

        List<Container> scopedContainers = resolveContainerScope(
                normalizedDomain,
                normalizedContainerScope,
                creatorAddr
        );

        if (normalizedContainerId != null) {
            scopedContainers = scopedContainers.stream()
                    .filter(container -> normalizedContainerId.equals(container.getId()))
                    .toList();
        }

        List<String> scopedContainerIds = scopedContainers.stream()
                .map(Container::getId)
                .filter(Objects::nonNull)
                .toList();

        if (scopedContainerIds.isEmpty()) {
            return buildBrowseResponse(
                    List.of(),
                    page,
                    pageSize,
                    0,
                    buildContainerChildLinkFilterMeta(
                            normalizedContainerId,
                            containerScopeToApiValue(normalizedContainerScope),
                            filterOptions,
                            normalizedDomain
                    )
            );
        }

        List<ContainerChildLink> candidateLinks =
                containerChildLinkRepository.findByContainerParentIdIn(scopedContainerIds);

        Set<String> relatedContainerIds = new LinkedHashSet<>(scopedContainerIds);
        for (ContainerChildLink link : candidateLinks) {
            if (link == null) continue;
            if (link.getContainerParentId() != null) {
                relatedContainerIds.add(link.getContainerParentId());
            }
            if (link.getContainerChildId() != null) {
                relatedContainerIds.add(link.getContainerChildId());
            }
        }

        Map<String, String> containerNamesById = containerRepository.findAllById(relatedContainerIds).stream()
                .collect(Collectors.toMap(
                        Container::getId,
                        this::containerDisplayName,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        List<ContainerChildLink> filteredLinks = candidateLinks.stream()
                .filter(link -> matchesContainerChildLinkSearch(link, filterOptions))
                .toList();
        List<ContainerChildLink> orderedLinks =
                sortContainerChildLinks(filteredLinks, filterOptions);

        int totalElements = orderedLinks.size();
        int fromIndex = Math.min(page * pageSize, totalElements);
        int toIndex = Math.min(fromIndex + pageSize, totalElements);
        List<ContainerChildLink> pagedLinks = orderedLinks.subList(fromIndex, toIndex);

        List<Map<String, Object>> content = pagedLinks.stream()
                .map(link -> toContainerChildLinkRow(link, containerNamesById))
                .toList();

        return buildBrowseResponse(
                content,
                page,
                pageSize,
                totalElements,
                buildContainerChildLinkFilterMeta(
                        normalizedContainerId,
                        containerScopeToApiValue(normalizedContainerScope),
                        filterOptions,
                        normalizedDomain
                )
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getOwners(
            String creatorAddr,
            String containerId,
            String containerScope,
            String ownerStatus,
            String query,
            String searchFields,
            String sortBy,
            String sortDirection,
            String domain,
            int page,
            int pageSize
    ) {
        String normalizedContainerId = normalizeBlank(containerId);
        String normalizedDomain = normalizeBlank(domain);
        ContainerScope normalizedContainerScope = parseContainerScope(containerScope);
        OwnerFilterOptions filterOptions =
                parseOwnerFilterOptions(ownerStatus, query, searchFields, sortBy, sortDirection);

        List<Container> scopedContainers = resolveContainerScope(
                normalizedDomain,
                normalizedContainerScope,
                creatorAddr
        );

        if (normalizedContainerId != null) {
            scopedContainers = scopedContainers.stream()
                    .filter(container -> normalizedContainerId.equals(container.getId()))
                    .toList();
        }

        List<String> scopedContainerIds = scopedContainers.stream()
                .map(Container::getId)
                .filter(Objects::nonNull)
                .toList();

        if (scopedContainerIds.isEmpty()) {
            return buildBrowseResponse(
                    List.of(),
                    page,
                    pageSize,
                    0,
                    buildOwnerFilterMeta(
                            normalizedContainerId,
                            containerScopeToApiValue(normalizedContainerScope),
                            filterOptions,
                            normalizedDomain
                    )
            );
        }

        Map<String, String> containerNamesById = scopedContainers.stream()
                .collect(Collectors.toMap(
                        Container::getId,
                        this::containerDisplayName,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        List<Owner> filteredOwners = ownerRepository.findByContainerIdIn(scopedContainerIds).stream()
                .filter(owner -> matchesOwnerStatus(owner, filterOptions.status()))
                .filter(owner -> matchesOwnerSearch(owner, filterOptions, containerNamesById))
                .toList();

        List<Owner> orderedOwners = sortOwners(filteredOwners, filterOptions, containerNamesById);

        int totalElements = orderedOwners.size();
        int fromIndex = Math.min(page * pageSize, totalElements);
        int toIndex = Math.min(fromIndex + pageSize, totalElements);
        List<Owner> pagedOwners = orderedOwners.subList(fromIndex, toIndex);

        List<Map<String, Object>> content = pagedOwners.stream()
                .map(owner -> toOwnerRow(owner, containerNamesById))
                .toList();

        return buildBrowseResponse(
                content,
                page,
                pageSize,
                totalElements,
                buildOwnerFilterMeta(
                        normalizedContainerId,
                        containerScopeToApiValue(normalizedContainerScope),
                        filterOptions,
                        normalizedDomain
                )
        );
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
            String dataItemRecipientScope,
            String dataItemVerificationRecipientScope,
            String recipientAddress,
            String containerScope,
            String dataItemQuery,
            String dataItemSearchFields,
            Boolean dataItemVerified,
            Boolean dataItemHasRevisions,
            Boolean dataItemHasVerifications,
            String dataItemDataType,
            String dataItemSortBy,
            String dataItemSortDirection,
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
        String explicitRecipientAddress = normalizeBlank(recipientAddress);
        String normalizedRecipientAddress =
                explicitRecipientAddress != null ? explicitRecipientAddress : normalizeBlank(creatorAddr);
        RecipientScope normalizedDataItemRecipientScope =
                parseRecipientScope(dataItemRecipientScope);
        RecipientScope normalizedDataItemVerificationRecipientScope =
                parseRecipientScope(dataItemVerificationRecipientScope);
        ContainerScope normalizedContainerScope = parseContainerScope(containerScope);
        DataItemFilterOptions dataItemFilters = parseDataItemFilterOptions(
                dataItemQuery,
                dataItemSearchFields,
                dataItemVerified,
                dataItemHasRevisions,
                dataItemHasVerifications,
                dataItemDataType,
                dataItemSortBy,
                dataItemSortDirection
        );

        if (normalizedContainerId == null) {
            if (includeDataItems) {
                return getContainerTreeAcrossContainers(
                        normalizedDataTypeId,
                        normalizedDataItemId,
                        normalizedDataItemVerificationId,
                        dataItemVerificationVerified,
                        normalizedDataItemRecipientScope,
                        normalizedDataItemVerificationRecipientScope,
                        normalizedRecipientAddress,
                        dataItemFilters,
                        normalizedDomain,
                        normalizedContainerScope,
                        creatorAddr,
                        page,
                        pageSize,
                        safeIncludes,
                        includeDataItemVerifications
                );
            }

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
                    recipientScopeToApiValue(normalizedDataItemRecipientScope),
                    recipientScopeToApiValue(normalizedDataItemVerificationRecipientScope),
                    normalizedRecipientAddress,
                    dataItemFilters.query(),
                    toSearchFieldsCsv(dataItemFilters.searchFields()),
                    dataItemFilters.verified(),
                    dataItemFilters.hasRevisions(),
                    dataItemFilters.hasVerifications(),
                    dataItemFilters.dataType(),
                    dataItemFilters.sortBy().name().toLowerCase(Locale.ROOT),
                    dataItemFilters.sortAscending() ? "asc" : "desc",
                    normalizedDomain,
                    containerScopeToApiValue(normalizedContainerScope),
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
                    recipientScopeToApiValue(normalizedDataItemRecipientScope),
                    recipientScopeToApiValue(normalizedDataItemVerificationRecipientScope),
                    normalizedRecipientAddress,
                    dataItemFilters.query(),
                    toSearchFieldsCsv(dataItemFilters.searchFields()),
                    dataItemFilters.verified(),
                    dataItemFilters.hasRevisions(),
                    dataItemFilters.hasVerifications(),
                    dataItemFilters.dataType(),
                    dataItemFilters.sortBy().name().toLowerCase(Locale.ROOT),
                    dataItemFilters.sortAscending() ? "asc" : "desc",
                    normalizedDomain,
                    containerScopeToApiValue(normalizedContainerScope),
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
                    recipientScopeToApiValue(normalizedDataItemRecipientScope),
                    recipientScopeToApiValue(normalizedDataItemVerificationRecipientScope),
                    normalizedRecipientAddress,
                    dataItemFilters.query(),
                    toSearchFieldsCsv(dataItemFilters.searchFields()),
                    dataItemFilters.verified(),
                    dataItemFilters.hasRevisions(),
                    dataItemFilters.hasVerifications(),
                    dataItemFilters.dataType(),
                    dataItemFilters.sortBy().name().toLowerCase(Locale.ROOT),
                    dataItemFilters.sortAscending() ? "asc" : "desc",
                    normalizedDomain,
                    containerScopeToApiValue(normalizedContainerScope),
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

        Map<String, DataType> dataTypesById = allMatchingDataTypes.stream()
                .collect(Collectors.toMap(
                        DataType::getId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        List<DataItem> candidateItems;
        if (normalizedDataItemId != null) {
            candidateItems = resolveSingleDataItem(
                    normalizedContainerId,
                    dataTypeIds,
                    normalizedDataItemId,
                    normalizedDomain
            );
        } else if (dataTypeIds.isEmpty()) {
            candidateItems = List.of();
        } else {
            candidateItems = dataItemRepository.findByContainerIdAndDataTypeIdInAndOptionalDomain(
                    normalizedContainerId,
                    dataTypeIds,
                    normalizedDomain
            );
        }

        if (normalizedDataItemRecipientScope != RecipientScope.ALL) {
            candidateItems = candidateItems.stream()
                    .filter(item ->
                            matchesRecipientScope(
                                    item.getRecipients(),
                                    normalizedDataItemRecipientScope,
                                    normalizedRecipientAddress
                            )
                    )
                    .toList();
        }

        List<String> candidateItemIds = candidateItems.stream().map(DataItem::getId).toList();
        boolean needsVerifications =
                includeDataItemVerifications
                        || normalizedDataItemVerificationId != null
                        || dataItemVerificationVerified != null
                        || dataItemFilters.hasVerifications() != null;

        List<DataItemVerification> filteredDataItemVerifications =
                needsVerifications && !candidateItemIds.isEmpty()
                        ? dataItemVerificationRepository.findByDataItemIdIn(candidateItemIds)
                        : List.of();

        if (normalizedDataItemVerificationId != null) {
            filteredDataItemVerifications = filteredDataItemVerifications.stream()
                    .filter(v -> normalizedDataItemVerificationId.equals(v.getId()))
                    .toList();
        }

        if (dataItemVerificationVerified != null) {
            filteredDataItemVerifications = filteredDataItemVerifications.stream()
                    .filter(v -> Objects.equals(dataItemVerificationVerified, v.getVerified()))
                    .toList();
        }

        if (normalizedDataItemVerificationRecipientScope != RecipientScope.ALL) {
            filteredDataItemVerifications = filteredDataItemVerifications.stream()
                    .filter(verification ->
                            matchesRecipientScope(
                                    verification.getRecipients(),
                                    normalizedDataItemVerificationRecipientScope,
                                    normalizedRecipientAddress
                            )
                    )
                    .toList();
        }

        Map<String, List<DataItemVerification>> dataItemVerificationsByItemId = filteredDataItemVerifications.stream()
                .collect(Collectors.groupingBy(DataItemVerification::getDataItemId));

        boolean dataItemVerificationFiltered =
                normalizedDataItemVerificationId != null
                        || dataItemVerificationVerified != null
                        || normalizedDataItemVerificationRecipientScope != RecipientScope.ALL;

        Map<String, DataItemRevisionDto> revisionDtoCache = new HashMap<>();
        Function<DataItem, DataItemRevisionDto> revisionResolver =
                dataItem -> revisionDtoCache.computeIfAbsent(dataItem.getId(), ignored -> buildRevisionDto(dataItem));

        List<DataItem> filteredItems = candidateItems.stream()
                .filter(di ->
                        !dataItemVerificationFiltered
                                || !dataItemVerificationsByItemId.getOrDefault(di.getId(), List.of()).isEmpty()
                )
                .filter(di -> matchesDataItemFilters(
                        di,
                        dataTypesById,
                        dataItemVerificationsByItemId,
                        revisionResolver,
                        dataItemFilters
                ))
                .toList();

        List<DataItem> orderedItems = sortDataItems(filteredItems, dataItemFilters);

        long totalDataItems = orderedItems.size();
        int totalPages = totalDataItems == 0 ? 0 : (int) Math.ceil((double) totalDataItems / pageSize);
        int fromIndex = Math.min(page * pageSize, orderedItems.size());
        int toIndex = Math.min(fromIndex + pageSize, orderedItems.size());
        boolean hasNext = toIndex < orderedItems.size();
        List<DataItem> items = orderedItems.subList(fromIndex, toIndex);

        List<String> itemIds = items.stream().map(DataItem::getId).toList();
        Map<String, List<DataItemVerification>> pagedVerificationsByItemId = includeDataItemVerifications
                ? itemIds.stream().collect(Collectors.toMap(
                        Function.identity(),
                        id -> dataItemVerificationsByItemId.getOrDefault(id, List.of()),
                        (left, right) -> left,
                        LinkedHashMap::new
                ))
                : Map.of();

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
                                            ? pagedVerificationsByItemId.getOrDefault(di.getId(), List.of())
                                            : List.of(),
                                    revisionResolver.apply(di)
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
                recipientScopeToApiValue(normalizedDataItemRecipientScope),
                recipientScopeToApiValue(normalizedDataItemVerificationRecipientScope),
                normalizedRecipientAddress,
                dataItemFilters.query(),
                toSearchFieldsCsv(dataItemFilters.searchFields()),
                dataItemFilters.verified(),
                dataItemFilters.hasRevisions(),
                dataItemFilters.hasVerifications(),
                dataItemFilters.dataType(),
                dataItemFilters.sortBy().name().toLowerCase(Locale.ROOT),
                dataItemFilters.sortAscending() ? "asc" : "desc",
                normalizedDomain,
                containerScopeToApiValue(normalizedContainerScope),
                1L,
                1L,
                responseDataTypes.size(),
                totalDataItems,
                returnedDataItemVerifications,
                totalPages,
                hasNext,
                false
        );
        meta.put("returnedDataItems", returnedItems);
        meta.put(
                "availableDataTypes",
                allMatchingDataTypes.stream()
                        .map(DataType::getName)
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(name -> !name.isEmpty())
                        .distinct()
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList()
        );

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

    @Transactional(readOnly = true)
    public LinkGraphResponseDto getLinkGraph(LinkGraphRequestDto request) {
        if (request == null) {
            return new LinkGraphResponseDto(List.of(), List.of(), "Missing request payload.");
        }

        LinkGraphMode mode = parseLinkGraphMode(request.getMode());
        LinkSourceType sourceType = parseLinkSourceType(request.getSourceType());
        String sourceContainerId = normalizeBlank(request.getSourceContainerId());
        String sourceDataItemId = normalizeBlank(request.getSourceDataItemId());
        int maxDepth = clamp(
                request.getMaxDepth(),
                LINK_GRAPH_DEFAULT_MAX_DEPTH,
                1,
                LINK_GRAPH_MAX_DEPTH
        );
        int maxNodes = clamp(
                request.getMaxNodes(),
                LINK_GRAPH_DEFAULT_MAX_NODES,
                1,
                LINK_GRAPH_MAX_NODES
        );
        boolean preventCycles = request.getPreventCycles() == null || request.getPreventCycles();

        List<String> seeds = request.getSeeds() == null
                ? List.of()
                : request.getSeeds().stream()
                        .map(NodeService::normalizeBlank)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();

        if (seeds.isEmpty()) {
            return new LinkGraphResponseDto(List.of(), List.of(), "No seed values provided.");
        }

        Map<String, GraphEntityMetadata> entitiesById = new HashMap<>();
        Map<String, List<String>> recipientToObjectIds = new HashMap<>();
        Map<String, List<String>> referenceToObjectIds = new HashMap<>();
        Map<String, List<String>> objectToRecipients = new HashMap<>();
        Map<String, List<String>> objectToReferences = new HashMap<>();

        for (DataItem dataItem : dataItemRepository.findAll()) {
            GraphEntityMetadata metadata = new GraphEntityMetadata(
                    dataItem.getId(),
                    "data_item",
                    dataItem.getContainerId(),
                    dataItem.getId(),
                    dataItem.getName()
            );
            String objectKey = normalizeGraphKey(dataItem.getId());
            if (objectKey == null) {
                continue;
            }

            entitiesById.put(objectKey, metadata);
            indexGraphLinks(
                    dataItem.getId(),
                    dataItem.getRecipients(),
                    dataItem.getReferences(),
                    recipientToObjectIds,
                    referenceToObjectIds,
                    objectToRecipients,
                    objectToReferences
            );
        }

        for (DataItemVerification verification : dataItemVerificationRepository.findAll()) {
            GraphEntityMetadata metadata = new GraphEntityMetadata(
                    verification.getId(),
                    "data_item_verification",
                    verification.getContainerId(),
                    verification.getDataItemId(),
                    verification.getName()
            );
            String objectKey = normalizeGraphKey(verification.getId());
            if (objectKey == null) {
                continue;
            }

            entitiesById.put(objectKey, metadata);
            indexGraphLinks(
                    verification.getId(),
                    verification.getRecipients(),
                    verification.getReferences(),
                    recipientToObjectIds,
                    referenceToObjectIds,
                    objectToRecipients,
                    objectToReferences
            );
        }

        String effectiveSourceDataItemId = sourceDataItemId;
        String sourceVerificationId = null;
        if (sourceType == LinkSourceType.DATA_ITEM_VERIFICATION && sourceDataItemId != null) {
            GraphEntityMetadata sourceEntity = entitiesById.get(normalizeGraphKey(sourceDataItemId));
            if (sourceEntity != null && "data_item_verification".equals(sourceEntity.kind())) {
                sourceVerificationId = sourceEntity.id();
                effectiveSourceDataItemId = normalizeBlank(sourceEntity.dataItemId());
            }
        }

        Map<String, LinkGraphNodeDto> nodesByKey = new LinkedHashMap<>();
        Map<String, LinkGraphEdgeDto> edgesByKey = new LinkedHashMap<>();
        Deque<GraphFrontierNode> queue = new ArrayDeque<>();
        Set<String> visited = new LinkedHashSet<>();
        int maxEdges = Math.max(maxNodes * LINK_GRAPH_MAX_EDGES_FACTOR, maxNodes);
        boolean nodesTruncated = false;
        boolean edgesTruncated = false;

        for (String seed : seeds) {
            String seedKey = normalizeGraphKey(seed);
            if (seedKey == null) {
                continue;
            }

            nodesByKey.putIfAbsent(
                    seedKey,
                    new LinkGraphNodeDto(
                            seed,
                            buildGraphLabel(seed, "seed", entitiesById),
                            0,
                            "seed"
                    )
            );
            queue.addLast(new GraphFrontierNode(seed, 0));
            if (preventCycles) {
                visited.add(seedKey);
            }
        }

        while (!queue.isEmpty()) {
            GraphFrontierNode frontierNode = queue.removeFirst();
            if (frontierNode.level() >= maxDepth) {
                continue;
            }

            List<GraphNeighbor> neighbors = resolveGraphNeighbors(
                    mode,
                    sourceType,
                    frontierNode.value(),
                    sourceContainerId,
                    effectiveSourceDataItemId,
                    sourceVerificationId,
                    entitiesById,
                    recipientToObjectIds,
                    referenceToObjectIds,
                    objectToRecipients,
                    objectToReferences
            );

            for (GraphNeighbor neighbor : neighbors) {
                String fromKey = normalizeGraphKey(frontierNode.value());
                String targetKey = normalizeGraphKey(neighbor.target());
                if (fromKey == null || targetKey == null) {
                    continue;
                }

                if (!nodesByKey.containsKey(targetKey)) {
                    if (nodesByKey.size() >= maxNodes) {
                        nodesTruncated = true;
                        continue;
                    }

                    nodesByKey.put(
                            targetKey,
                            new LinkGraphNodeDto(
                                    neighbor.target(),
                                    buildGraphLabel(neighbor.target(), neighbor.kind(), entitiesById),
                                    frontierNode.level() + 1,
                                    neighbor.kind()
                            )
                    );
                }

                String edgeKey = fromKey + "->" + targetKey + ":" + neighbor.relation();
                if (!edgesByKey.containsKey(edgeKey)) {
                    if (edgesByKey.size() >= maxEdges) {
                        edgesTruncated = true;
                    } else {
                        edgesByKey.put(
                                edgeKey,
                                new LinkGraphEdgeDto(
                                        frontierNode.value(),
                                        neighbor.target(),
                                        neighbor.relation()
                                )
                        );
                    }
                }

                if (frontierNode.level() + 1 > maxDepth) {
                    continue;
                }

                if (preventCycles) {
                    if (visited.add(targetKey)) {
                        queue.addLast(new GraphFrontierNode(neighbor.target(), frontierNode.level() + 1));
                    }
                } else {
                    queue.addLast(new GraphFrontierNode(neighbor.target(), frontierNode.level() + 1));
                }
            }
        }

        List<String> infoParts = new ArrayList<>();
        if (edgesByKey.isEmpty()) {
            infoParts.add("No linked values found.");
        }
        if (nodesTruncated) {
            infoParts.add("Node limit reached (maxNodes=" + maxNodes + ").");
        }
        if (edgesTruncated) {
            infoParts.add("Edge limit reached.");
        }

        String info = infoParts.isEmpty() ? null : String.join(" ", infoParts);
        return new LinkGraphResponseDto(
                new ArrayList<>(nodesByKey.values()),
                new ArrayList<>(edgesByKey.values()),
                info
        );
    }

    private static void indexGraphLinks(
            String objectId,
            Collection<String> recipients,
            Collection<String> references,
            Map<String, List<String>> recipientToObjectIds,
            Map<String, List<String>> referenceToObjectIds,
            Map<String, List<String>> objectToRecipients,
            Map<String, List<String>> objectToReferences
    ) {
        String objectKey = normalizeGraphKey(objectId);
        if (objectKey == null) {
            return;
        }

        for (String recipient : sanitizeGraphValues(recipients)) {
            addGraphIndexValue(recipientToObjectIds, normalizeGraphKey(recipient), objectId);
            addGraphIndexValue(objectToRecipients, objectKey, recipient);
        }

        for (String reference : sanitizeGraphValues(references)) {
            addGraphIndexValue(referenceToObjectIds, normalizeGraphKey(reference), objectId);
            addGraphIndexValue(objectToReferences, objectKey, reference);
        }
    }

    private static List<GraphNeighbor> resolveGraphNeighbors(
            LinkGraphMode mode,
            LinkSourceType sourceType,
            String currentValue,
            String sourceContainerId,
            String sourceDataItemId,
            String sourceVerificationId,
            Map<String, GraphEntityMetadata> entitiesById,
            Map<String, List<String>> recipientToObjectIds,
            Map<String, List<String>> referenceToObjectIds,
            Map<String, List<String>> objectToRecipients,
            Map<String, List<String>> objectToReferences
    ) {
        String currentKey = normalizeGraphKey(currentValue);
        if (currentKey == null) {
            return List.of();
        }

        Map<String, GraphNeighbor> neighbors = new LinkedHashMap<>();
        GraphEntityMetadata currentEntity = entitiesById.get(currentKey);

        if (mode == LinkGraphMode.RECIPIENTS) {
            for (String objectId : recipientToObjectIds.getOrDefault(currentKey, List.of())) {
                GraphEntityMetadata objectMetadata = entitiesById.get(normalizeGraphKey(objectId));
                if (!matchesGraphScope(
                        objectMetadata,
                        sourceType,
                        sourceContainerId,
                        sourceDataItemId,
                        sourceVerificationId
                )) {
                    continue;
                }
                addGraphNeighbor(
                        neighbors,
                        objectId,
                        "recipient_of",
                        objectMetadata.kind()
                );
            }

            if (currentEntity != null && matchesGraphScope(
                    currentEntity,
                    sourceType,
                    sourceContainerId,
                    sourceDataItemId,
                    sourceVerificationId
            )) {
                for (String recipient : objectToRecipients.getOrDefault(currentKey, List.of())) {
                    addGraphNeighbor(neighbors, recipient, "has_recipient", "recipient");
                }
            }
        } else {
            if (currentEntity != null && matchesGraphScope(
                    currentEntity,
                    sourceType,
                    sourceContainerId,
                    sourceDataItemId,
                    sourceVerificationId
            )) {
                for (String reference : objectToReferences.getOrDefault(currentKey, List.of())) {
                    GraphEntityMetadata referenceMetadata = entitiesById.get(normalizeGraphKey(reference));
                    String kind = referenceMetadata != null ? referenceMetadata.kind() : "reference";
                    addGraphNeighbor(neighbors, reference, "references", kind);
                }
            }

            for (String objectId : referenceToObjectIds.getOrDefault(currentKey, List.of())) {
                GraphEntityMetadata objectMetadata = entitiesById.get(normalizeGraphKey(objectId));
                if (!matchesGraphScope(
                        objectMetadata,
                        sourceType,
                        sourceContainerId,
                        sourceDataItemId,
                        sourceVerificationId
                )) {
                    continue;
                }
                addGraphNeighbor(
                        neighbors,
                        objectId,
                        "referenced_by",
                        objectMetadata.kind()
                );
            }
        }

        return new ArrayList<>(neighbors.values());
    }

    private static boolean matchesGraphScope(
            GraphEntityMetadata metadata,
            LinkSourceType sourceType,
            String sourceContainerId,
            String sourceDataItemId,
            String sourceVerificationId
    ) {
        if (metadata == null) {
            return false;
        }

        if (sourceContainerId != null && !Objects.equals(sourceContainerId, metadata.containerId())) {
            return false;
        }

        if (sourceDataItemId == null) {
            return true;
        }

        if (sourceType == LinkSourceType.DATA_ITEM_VERIFICATION) {
            if ("data_item".equals(metadata.kind())) {
                return Objects.equals(sourceDataItemId, metadata.id());
            }

            if ("data_item_verification".equals(metadata.kind())) {
                if (sourceVerificationId != null) {
                    return Objects.equals(sourceVerificationId, metadata.id());
                }
                return Objects.equals(sourceDataItemId, metadata.dataItemId());
            }

            return true;
        }

        if ("data_item".equals(metadata.kind())) {
            return Objects.equals(sourceDataItemId, metadata.id());
        }

        if ("data_item_verification".equals(metadata.kind())) {
            return Objects.equals(sourceDataItemId, metadata.dataItemId());
        }

        return true;
    }

    private static void addGraphNeighbor(
            Map<String, GraphNeighbor> neighbors,
            String target,
            String relation,
            String kind
    ) {
        String targetKey = normalizeGraphKey(target);
        if (targetKey == null) {
            return;
        }
        String key = targetKey + ":" + relation;
        neighbors.putIfAbsent(key, new GraphNeighbor(target, relation, kind));
    }

    private static void addGraphIndexValue(
            Map<String, List<String>> index,
            String key,
            String value
    ) {
        if (key == null || value == null || value.isBlank()) {
            return;
        }
        List<String> values = index.computeIfAbsent(key, ignored -> new ArrayList<>());
        if (!values.contains(value)) {
            values.add(value);
        }
    }

    private static List<String> sanitizeGraphValues(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(NodeService::normalizeBlank)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private static LinkGraphMode parseLinkGraphMode(String rawMode) {
        if (rawMode == null || rawMode.isBlank()) {
            return LinkGraphMode.RECIPIENTS;
        }

        return "references".equalsIgnoreCase(rawMode.trim())
                ? LinkGraphMode.REFERENCES
                : LinkGraphMode.RECIPIENTS;
    }

    private static LinkSourceType parseLinkSourceType(String rawSourceType) {
        if (rawSourceType == null || rawSourceType.isBlank()) {
            return LinkSourceType.DATA_ITEM;
        }

        return "data_item_verification".equalsIgnoreCase(rawSourceType.trim())
                ? LinkSourceType.DATA_ITEM_VERIFICATION
                : LinkSourceType.DATA_ITEM;
    }

    private static int clamp(Integer value, int fallback, int min, int max) {
        int base = value == null ? fallback : value;
        return Math.max(min, Math.min(max, base));
    }

    private static String normalizeGraphKey(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static String buildGraphLabel(
            String id,
            String kind,
            Map<String, GraphEntityMetadata> entitiesById
    ) {
        String key = normalizeGraphKey(id);
        GraphEntityMetadata metadata = key == null ? null : entitiesById.get(key);
        if (metadata == null) {
            return id;
        }

        String name = normalizeBlank(metadata.name());
        if (name == null) {
            return metadata.id();
        }
        return name + " (" + abbreviateObjectId(metadata.id()) + ")";
    }

    private static String abbreviateObjectId(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 18) {
            return trimmed;
        }
        return trimmed.substring(0, 8) + "…" + trimmed.substring(trimmed.length() - 8);
    }

    private static DataItemFilterOptions parseDataItemFilterOptions(
            String query,
            String searchFieldsCsv,
            Boolean verified,
            Boolean hasRevisions,
            Boolean hasVerifications,
            String dataType,
            String sortByRaw,
            String sortDirectionRaw
    ) {
        String normalizedQuery = normalizeBlank(query);
        String normalizedDataType = normalizeBlank(dataType);
        EnumSet<DataItemSearchField> searchFields = parseDataItemSearchFields(searchFieldsCsv);
        DataItemSortBy sortBy = parseDataItemSortBy(sortByRaw);
        boolean sortAscending = parseDataItemSortAscending(sortDirectionRaw);

        return new DataItemFilterOptions(
                normalizedQuery,
                searchFields,
                verified,
                hasRevisions,
                hasVerifications,
                normalizedDataType,
                sortBy,
                sortAscending
        );
    }

    private static EnumSet<DataItemSearchField> parseDataItemSearchFields(String csv) {
        if (csv == null || csv.isBlank()) {
            return EnumSet.copyOf(DEFAULT_DATA_ITEM_SEARCH_FIELDS);
        }

        EnumSet<DataItemSearchField> parsed = EnumSet.noneOf(DataItemSearchField.class);
        for (String token : csv.split(",")) {
            String normalized = token == null ? "" : token.trim().toLowerCase(Locale.ROOT);
            switch (normalized) {
                case "name" -> parsed.add(DataItemSearchField.NAME);
                case "description" -> parsed.add(DataItemSearchField.DESCRIPTION);
                case "content", "body", "payload" -> parsed.add(DataItemSearchField.CONTENT);
                case "externalid", "external_id", "external-id" -> parsed.add(DataItemSearchField.EXTERNAL_ID);
                case "externalindex", "external_index", "external-index" ->
                        parsed.add(DataItemSearchField.EXTERNAL_INDEX);
                case "objectid", "object_id", "object-id", "id" -> parsed.add(DataItemSearchField.OBJECT_ID);
                case "datatype", "data_type", "data-type", "type" -> parsed.add(DataItemSearchField.DATA_TYPE);
                case "creator", "creatoraddr", "creator_addr", "creator-addr" ->
                        parsed.add(DataItemSearchField.CREATOR_ADDR);
                default -> {
                    // Ignore unknown values for forward compatibility.
                }
            }
        }

        return parsed.isEmpty() ? EnumSet.copyOf(DEFAULT_DATA_ITEM_SEARCH_FIELDS) : parsed;
    }

    private static DataItemSortBy parseDataItemSortBy(String raw) {
        if (raw == null || raw.isBlank()) {
            return DataItemSortBy.CREATED;
        }

        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "name" -> DataItemSortBy.NAME;
            case "externalindex", "external_index", "external-index" -> DataItemSortBy.EXTERNAL_INDEX;
            case "externalid", "external_id", "external-id" -> DataItemSortBy.EXTERNAL_ID;
            case "created", "createdonchain", "created_on_chain", "created-on-chain" -> DataItemSortBy.CREATED;
            default -> DataItemSortBy.CREATED;
        };
    }

    private static boolean parseDataItemSortAscending(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        return "asc".equalsIgnoreCase(raw.trim());
    }

    private static ContainerChildLinkFilterOptions parseContainerChildLinkFilterOptions(
            String query,
            String searchFieldsCsv,
            String sortByRaw,
            String sortDirectionRaw
    ) {
        return new ContainerChildLinkFilterOptions(
                normalizeBlank(query),
                parseContainerChildLinkSearchFields(searchFieldsCsv),
                parseContainerChildLinkSortBy(sortByRaw),
                parseDataItemSortAscending(sortDirectionRaw)
        );
    }

    private static EnumSet<ContainerChildLinkSearchField> parseContainerChildLinkSearchFields(
            String csv
    ) {
        if (csv == null || csv.isBlank()) {
            return EnumSet.copyOf(DEFAULT_CONTAINER_CHILD_LINK_SEARCH_FIELDS);
        }

        EnumSet<ContainerChildLinkSearchField> parsed =
                EnumSet.noneOf(ContainerChildLinkSearchField.class);
        for (String token : csv.split(",")) {
            String normalized = token == null ? "" : token.trim().toLowerCase(Locale.ROOT);
            switch (normalized) {
                case "name" -> parsed.add(ContainerChildLinkSearchField.NAME);
                case "description" -> parsed.add(ContainerChildLinkSearchField.DESCRIPTION);
                case "content", "body", "payload" ->
                        parsed.add(ContainerChildLinkSearchField.CONTENT);
                case "externalid", "external_id", "external-id" ->
                        parsed.add(ContainerChildLinkSearchField.EXTERNAL_ID);
                case "externalindex", "external_index", "external-index" ->
                        parsed.add(ContainerChildLinkSearchField.EXTERNAL_INDEX);
                case "objectid", "object_id", "object-id", "id" ->
                        parsed.add(ContainerChildLinkSearchField.OBJECT_ID);
                case "parentcontainerid", "parent_container_id", "parent-container-id",
                        "containerparentid", "container_parent_id", "container-parent-id" ->
                        parsed.add(ContainerChildLinkSearchField.PARENT_CONTAINER_ID);
                case "childcontainerid", "child_container_id", "child-container-id",
                        "containerchildid", "container_child_id", "container-child-id" ->
                        parsed.add(ContainerChildLinkSearchField.CHILD_CONTAINER_ID);
                case "creator", "creatoraddr", "creator_addr", "creator-addr" ->
                        parsed.add(ContainerChildLinkSearchField.CREATOR_ADDR);
                default -> {
                    // ignore unknown values
                }
            }
        }

        return parsed.isEmpty()
                ? EnumSet.copyOf(DEFAULT_CONTAINER_CHILD_LINK_SEARCH_FIELDS)
                : parsed;
    }

    private static ContainerChildLinkSortBy parseContainerChildLinkSortBy(String raw) {
        if (raw == null || raw.isBlank()) {
            return ContainerChildLinkSortBy.CREATED;
        }

        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "name" -> ContainerChildLinkSortBy.NAME;
            case "externalindex", "external_index", "external-index" ->
                    ContainerChildLinkSortBy.EXTERNAL_INDEX;
            case "externalid", "external_id", "external-id" ->
                    ContainerChildLinkSortBy.EXTERNAL_ID;
            case "created", "createdonchain", "created_on_chain", "created-on-chain" ->
                    ContainerChildLinkSortBy.CREATED;
            default -> ContainerChildLinkSortBy.CREATED;
        };
    }

    private static OwnerFilterOptions parseOwnerFilterOptions(
            String statusRaw,
            String query,
            String searchFieldsCsv,
            String sortByRaw,
            String sortDirectionRaw
    ) {
        return new OwnerFilterOptions(
                normalizeBlank(query),
                parseOwnerSearchFields(searchFieldsCsv),
                parseOwnerSortBy(sortByRaw),
                parseDataItemSortAscending(sortDirectionRaw),
                parseOwnerStatus(statusRaw)
        );
    }

    private static EnumSet<OwnerSearchField> parseOwnerSearchFields(String csv) {
        if (csv == null || csv.isBlank()) {
            return EnumSet.copyOf(DEFAULT_OWNER_SEARCH_FIELDS);
        }

        EnumSet<OwnerSearchField> parsed = EnumSet.noneOf(OwnerSearchField.class);
        for (String token : csv.split(",")) {
            String normalized = token == null ? "" : token.trim().toLowerCase(Locale.ROOT);
            switch (normalized) {
                case "addr", "address" -> parsed.add(OwnerSearchField.ADDRESS);
                case "role" -> parsed.add(OwnerSearchField.ROLE);
                case "containerid", "container_id", "container-id" ->
                        parsed.add(OwnerSearchField.CONTAINER_ID);
                case "containername", "container_name", "container-name" ->
                        parsed.add(OwnerSearchField.CONTAINER_NAME);
                case "objectid", "object_id", "object-id", "id" ->
                        parsed.add(OwnerSearchField.OBJECT_ID);
                case "creator", "creatoraddr", "creator_addr", "creator-addr" ->
                        parsed.add(OwnerSearchField.CREATOR_ADDR);
                case "removed", "isremoved", "is_removed", "is-removed" ->
                        parsed.add(OwnerSearchField.REMOVED);
                default -> {
                    // ignore unknown values
                }
            }
        }

        return parsed.isEmpty() ? EnumSet.copyOf(DEFAULT_OWNER_SEARCH_FIELDS) : parsed;
    }

    private static OwnerSortBy parseOwnerSortBy(String raw) {
        if (raw == null || raw.isBlank()) {
            return OwnerSortBy.CREATED;
        }

        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "address", "addr" -> OwnerSortBy.ADDRESS;
            case "role" -> OwnerSortBy.ROLE;
            case "containername", "container_name", "container-name", "container" ->
                    OwnerSortBy.CONTAINER_NAME;
            case "created", "createdonchain", "created_on_chain", "created-on-chain" ->
                    OwnerSortBy.CREATED;
            default -> OwnerSortBy.CREATED;
        };
    }

    private static OwnerStatus parseOwnerStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return OwnerStatus.ACTIVE;
        }

        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "all", "any" -> OwnerStatus.ALL;
            case "removed", "inactive" -> OwnerStatus.REMOVED;
            default -> OwnerStatus.ACTIVE;
        };
    }

    private static boolean matchesOwnerStatus(Owner owner, OwnerStatus status) {
        if (owner == null || status == null) {
            return false;
        }

        return switch (status) {
            case ALL -> true;
            case ACTIVE -> !owner.isRemoved();
            case REMOVED -> owner.isRemoved();
        };
    }

    private static boolean matchesContainerChildLinkSearch(
            ContainerChildLink link,
            ContainerChildLinkFilterOptions options
    ) {
        if (link == null || options == null || options.query() == null) {
            return true;
        }

        List<String> queryTokens = toQueryTokens(options.query());
        if (queryTokens.isEmpty()) {
            return true;
        }

        List<String> values = new ArrayList<>();
        if (options.searchFields().contains(ContainerChildLinkSearchField.NAME)) {
            values.add(normalizeUnknownText(link.getName()));
        }
        if (options.searchFields().contains(ContainerChildLinkSearchField.DESCRIPTION)) {
            values.add(normalizeUnknownText(link.getDescription()));
        }
        if (options.searchFields().contains(ContainerChildLinkSearchField.CONTENT)) {
            values.add(normalizeUnknownText(link.getContent()));
        }
        if (options.searchFields().contains(ContainerChildLinkSearchField.EXTERNAL_ID)) {
            values.add(normalizeUnknownText(link.getExternalId()));
        }
        if (options.searchFields().contains(ContainerChildLinkSearchField.EXTERNAL_INDEX)) {
            values.add(normalizeUnknownText(link.getExternalIndex()));
        }
        if (options.searchFields().contains(ContainerChildLinkSearchField.OBJECT_ID)) {
            values.add(normalizeUnknownText(link.getId()));
        }
        if (options.searchFields().contains(ContainerChildLinkSearchField.PARENT_CONTAINER_ID)) {
            values.add(normalizeUnknownText(link.getContainerParentId()));
        }
        if (options.searchFields().contains(ContainerChildLinkSearchField.CHILD_CONTAINER_ID)) {
            values.add(normalizeUnknownText(link.getContainerChildId()));
        }
        if (options.searchFields().contains(ContainerChildLinkSearchField.CREATOR_ADDR)) {
            values.add(normalizeUnknownText(
                    link.getCreator() == null ? null : link.getCreator().getCreatorAddr()
            ));
        }

        return queryTokens.stream().allMatch(
                token -> values.stream().anyMatch(value -> value.contains(token))
        );
    }

    private static boolean matchesOwnerSearch(
            Owner owner,
            OwnerFilterOptions options,
            Map<String, String> containerNamesById
    ) {
        if (owner == null || options == null || options.query() == null) {
            return true;
        }

        List<String> queryTokens = toQueryTokens(options.query());
        if (queryTokens.isEmpty()) {
            return true;
        }

        String containerId = owner.getContainer() == null ? null : owner.getContainer().getId();
        String containerName = containerNamesById.get(containerId);
        List<String> values = new ArrayList<>();
        if (options.searchFields().contains(OwnerSearchField.ADDRESS)) {
            values.add(normalizeUnknownText(owner.getAddr()));
        }
        if (options.searchFields().contains(OwnerSearchField.ROLE)) {
            values.add(normalizeUnknownText(owner.getRole()));
        }
        if (options.searchFields().contains(OwnerSearchField.CONTAINER_ID)) {
            values.add(normalizeUnknownText(containerId));
        }
        if (options.searchFields().contains(OwnerSearchField.CONTAINER_NAME)) {
            values.add(normalizeUnknownText(containerName));
        }
        if (options.searchFields().contains(OwnerSearchField.OBJECT_ID)) {
            values.add(normalizeUnknownText(owner.getId()));
        }
        if (options.searchFields().contains(OwnerSearchField.CREATOR_ADDR)) {
            values.add(normalizeUnknownText(
                    owner.getCreator() == null ? null : owner.getCreator().getCreatorAddr()
            ));
        }
        if (options.searchFields().contains(OwnerSearchField.REMOVED)) {
            values.add(normalizeUnknownText(owner.isRemoved()));
        }

        return queryTokens.stream().allMatch(
                token -> values.stream().anyMatch(value -> value.contains(token))
        );
    }

    private static List<String> toQueryTokens(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String normalizedQuery = normalizeText(query);
        if (normalizedQuery.isBlank()) {
            return List.of();
        }

        return java.util.Arrays.stream(normalizedQuery.split("\\s+"))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .toList();
    }

    private static List<ContainerChildLink> sortContainerChildLinks(
            List<ContainerChildLink> links,
            ContainerChildLinkFilterOptions options
    ) {
        Comparator<ContainerChildLink> comparator = (left, right) -> {
            int ordered = compareNullableSortValues(
                    containerChildLinkSortValue(left, options.sortBy()),
                    containerChildLinkSortValue(right, options.sortBy()),
                    options.sortAscending()
            );
            if (ordered != 0) {
                return ordered;
            }
            return normalizeText(left.getId()).compareTo(normalizeText(right.getId()));
        };

        return links.stream().sorted(comparator).toList();
    }

    private static Comparable<?> containerChildLinkSortValue(
            ContainerChildLink link,
            ContainerChildLinkSortBy sortBy
    ) {
        return switch (sortBy) {
            case CREATED -> link.getSequenceIndex();
            case NAME -> normalizeText(link.getName());
            case EXTERNAL_INDEX -> link.getExternalIndex();
            case EXTERNAL_ID -> normalizeText(link.getExternalId());
        };
    }

    private static List<Owner> sortOwners(
            List<Owner> owners,
            OwnerFilterOptions options,
            Map<String, String> containerNamesById
    ) {
        Comparator<Owner> comparator = (left, right) -> {
            int ordered = compareNullableSortValues(
                    ownerSortValue(left, options.sortBy(), containerNamesById),
                    ownerSortValue(right, options.sortBy(), containerNamesById),
                    options.sortAscending()
            );
            if (ordered != 0) {
                return ordered;
            }
            return normalizeText(left.getId()).compareTo(normalizeText(right.getId()));
        };

        return owners.stream().sorted(comparator).toList();
    }

    private static Comparable<?> ownerSortValue(
            Owner owner,
            OwnerSortBy sortBy,
            Map<String, String> containerNamesById
    ) {
        String containerId = owner.getContainer() == null ? null : owner.getContainer().getId();
        return switch (sortBy) {
            case CREATED -> owner.getSequenceIndex();
            case ADDRESS -> normalizeText(owner.getAddr());
            case ROLE -> normalizeText(owner.getRole());
            case CONTAINER_NAME -> normalizeText(containerNamesById.get(containerId));
        };
    }

    private static Map<String, Object> toContainerChildLinkRow(
            ContainerChildLink link,
            Map<String, String> containerNamesById
    ) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("id", link.getId());
        fields.put("containerParentId", link.getContainerParentId());
        fields.put("containerParentName", containerNamesById.get(link.getContainerParentId()));
        fields.put("containerChildId", link.getContainerChildId());
        fields.put("containerChildName", containerNamesById.get(link.getContainerChildId()));
        fields.put("name", link.getName());
        fields.put("description", link.getDescription());
        fields.put("content", link.getContent());
        fields.put("externalId", link.getExternalId());
        fields.put("externalIndex", link.getExternalIndex());
        fields.put("sequenceIndex", link.getSequenceIndex());
        fields.put(
                "creatorAddr",
                link.getCreator() == null ? null : link.getCreator().getCreatorAddr()
        );

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("object_id", link.getId());
        row.put("fields", fields);
        return row;
    }

    private static Map<String, Object> toOwnerRow(
            Owner owner,
            Map<String, String> containerNamesById
    ) {
        String containerId = owner.getContainer() == null ? null : owner.getContainer().getId();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("id", owner.getId());
        fields.put("containerId", containerId);
        fields.put("containerName", containerNamesById.get(containerId));
        fields.put("addr", owner.getAddr());
        fields.put("role", owner.getRole());
        fields.put("removed", owner.isRemoved());
        fields.put("sequenceIndex", owner.getSequenceIndex());
        fields.put(
                "creatorAddr",
                owner.getCreator() == null ? null : owner.getCreator().getCreatorAddr()
        );

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("object_id", owner.getId());
        row.put("fields", fields);
        return row;
    }

    private static Map<String, Object> buildBrowseResponse(
            List<Map<String, Object>> content,
            int page,
            int pageSize,
            int totalElements,
            Map<String, Object> filters
    ) {
        int totalPages = totalElements == 0
                ? 0
                : (int) Math.ceil((double) totalElements / pageSize);
        boolean hasNext = page + 1 < totalPages;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", content);
        response.put("page", page);
        response.put("pageSize", pageSize);
        response.put("totalElements", totalElements);
        response.put("totalPages", totalPages);
        response.put("hasNext", hasNext);
        response.put("filters", filters);
        return response;
    }

    private static Map<String, Object> buildContainerChildLinkFilterMeta(
            String containerId,
            String containerScope,
            ContainerChildLinkFilterOptions options,
            String domain
    ) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("containerId", containerId);
        filters.put("containerScope", containerScope);
        filters.put("query", options.query());
        filters.put("searchFields", toContainerChildLinkSearchFieldsCsv(options.searchFields()));
        filters.put("sortBy", options.sortBy().name().toLowerCase(Locale.ROOT));
        filters.put("sortDirection", options.sortAscending() ? "asc" : "desc");
        filters.put("domain", domain);
        return filters;
    }

    private static Map<String, Object> buildOwnerFilterMeta(
            String containerId,
            String containerScope,
            OwnerFilterOptions options,
            String domain
    ) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("containerId", containerId);
        filters.put("containerScope", containerScope);
        filters.put("ownerStatus", ownerStatusToApiValue(options.status()));
        filters.put("query", options.query());
        filters.put("searchFields", toOwnerSearchFieldsCsv(options.searchFields()));
        filters.put("sortBy", options.sortBy().name().toLowerCase(Locale.ROOT));
        filters.put("sortDirection", options.sortAscending() ? "asc" : "desc");
        filters.put("domain", domain);
        return filters;
    }

    private static String toContainerChildLinkSearchFieldsCsv(
            EnumSet<ContainerChildLinkSearchField> searchFields
    ) {
        List<String> values = new ArrayList<>();
        if (searchFields.contains(ContainerChildLinkSearchField.NAME)) values.add("name");
        if (searchFields.contains(ContainerChildLinkSearchField.DESCRIPTION)) values.add("description");
        if (searchFields.contains(ContainerChildLinkSearchField.CONTENT)) values.add("content");
        if (searchFields.contains(ContainerChildLinkSearchField.EXTERNAL_ID)) values.add("externalId");
        if (searchFields.contains(ContainerChildLinkSearchField.EXTERNAL_INDEX)) values.add("externalIndex");
        if (searchFields.contains(ContainerChildLinkSearchField.OBJECT_ID)) values.add("objectId");
        if (searchFields.contains(ContainerChildLinkSearchField.PARENT_CONTAINER_ID)) {
            values.add("parentContainerId");
        }
        if (searchFields.contains(ContainerChildLinkSearchField.CHILD_CONTAINER_ID)) {
            values.add("childContainerId");
        }
        if (searchFields.contains(ContainerChildLinkSearchField.CREATOR_ADDR)) values.add("creatorAddr");
        return String.join(",", values);
    }

    private static String toOwnerSearchFieldsCsv(EnumSet<OwnerSearchField> searchFields) {
        List<String> values = new ArrayList<>();
        if (searchFields.contains(OwnerSearchField.ADDRESS)) values.add("addr");
        if (searchFields.contains(OwnerSearchField.ROLE)) values.add("role");
        if (searchFields.contains(OwnerSearchField.CONTAINER_ID)) values.add("containerId");
        if (searchFields.contains(OwnerSearchField.CONTAINER_NAME)) values.add("containerName");
        if (searchFields.contains(OwnerSearchField.OBJECT_ID)) values.add("objectId");
        if (searchFields.contains(OwnerSearchField.CREATOR_ADDR)) values.add("creatorAddr");
        if (searchFields.contains(OwnerSearchField.REMOVED)) values.add("removed");
        return String.join(",", values);
    }

    private static String ownerStatusToApiValue(OwnerStatus status) {
        if (status == null) {
            return "active";
        }
        return switch (status) {
            case ALL -> "all";
            case ACTIVE -> "active";
            case REMOVED -> "removed";
        };
    }

    private static String normalizeUnknownText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String stringValue) {
            return normalizeText(stringValue);
        }
        if (value instanceof Boolean boolValue) {
            return boolValue ? "true" : "false";
        }
        return String.valueOf(value).trim().toLowerCase(Locale.ROOT);
    }

    private String containerDisplayName(Container container) {
        if (container == null) {
            return "";
        }
        String name = normalizeBlank(container.getName());
        if (name != null) {
            return name;
        }
        return normalizeBlank(container.getId()) == null ? "" : container.getId();
    }

    private static RecipientScope parseRecipientScope(String raw) {
        if (raw == null || raw.isBlank()) {
            return RecipientScope.ALL;
        }

        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "mine", "received", "received_by_me" -> RecipientScope.MINE;
            case "others", "received_by_others" -> RecipientScope.OTHERS;
            case "with_recipients", "with-recipients", "has_recipients", "has-recipients" ->
                    RecipientScope.WITH_RECIPIENTS;
            default -> RecipientScope.ALL;
        };
    }

    private static ContainerScope parseContainerScope(String raw) {
        if (raw == null || raw.isBlank()) {
            return ContainerScope.ACCESSIBLE;
        }

        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "all", "global", "any" -> ContainerScope.ALL;
            default -> ContainerScope.ACCESSIBLE;
        };
    }

    private static String recipientScopeToApiValue(RecipientScope scope) {
        if (scope == null) {
            return "all";
        }
        return switch (scope) {
            case MINE -> "mine";
            case OTHERS -> "others";
            case WITH_RECIPIENTS -> "with_recipients";
            case ALL -> "all";
        };
    }

    private static String containerScopeToApiValue(ContainerScope scope) {
        if (scope == null) {
            return "accessible";
        }

        return scope == ContainerScope.ALL ? "all" : "accessible";
    }

    private static boolean matchesRecipientScope(
            List<String> recipients,
            RecipientScope scope,
            String recipientAddress
    ) {
        List<String> normalizedRecipients = recipients == null
                ? List.of()
                : recipients.stream()
                        .filter(Objects::nonNull)
                        .map(value -> value.trim().toLowerCase(Locale.ROOT))
                        .filter(value -> !value.isBlank())
                        .toList();

        if (scope == null || scope == RecipientScope.ALL) {
            return true;
        }

        if (scope == RecipientScope.WITH_RECIPIENTS) {
            return !normalizedRecipients.isEmpty();
        }

        String normalizedAddress = normalizeText(recipientAddress);
        if (normalizedAddress.isBlank()) {
            return false;
        }

        boolean mine = normalizedRecipients.contains(normalizedAddress);
        return switch (scope) {
            case MINE -> mine;
            case OTHERS -> !normalizedRecipients.isEmpty() && !mine;
            case WITH_RECIPIENTS -> !normalizedRecipients.isEmpty();
            case ALL -> true;
        };
    }

    private static String toSearchFieldsCsv(EnumSet<DataItemSearchField> searchFields) {
        List<String> values = new ArrayList<>();
        if (searchFields.contains(DataItemSearchField.NAME)) values.add("name");
        if (searchFields.contains(DataItemSearchField.DESCRIPTION)) values.add("description");
        if (searchFields.contains(DataItemSearchField.CONTENT)) values.add("content");
        if (searchFields.contains(DataItemSearchField.EXTERNAL_ID)) values.add("externalId");
        if (searchFields.contains(DataItemSearchField.EXTERNAL_INDEX)) values.add("externalIndex");
        if (searchFields.contains(DataItemSearchField.OBJECT_ID)) values.add("objectId");
        if (searchFields.contains(DataItemSearchField.DATA_TYPE)) values.add("dataType");
        if (searchFields.contains(DataItemSearchField.CREATOR_ADDR)) values.add("creatorAddr");
        return String.join(",", values);
    }

    private static List<DataItem> sortDataItems(
            List<DataItem> items,
            DataItemFilterOptions options
    ) {
        Comparator<DataItem> comparator = (left, right) -> {
            int ordered = compareNullableSortValues(
                    dataItemSortValue(left, options.sortBy()),
                    dataItemSortValue(right, options.sortBy()),
                    options.sortAscending()
            );
            if (ordered != 0) return ordered;
            return normalizeText(left.getId()).compareTo(normalizeText(right.getId()));
        };

        return items.stream().sorted(comparator).toList();
    }

    private static Comparable<?> dataItemSortValue(
            DataItem dataItem,
            DataItemSortBy sortBy
    ) {
        return switch (sortBy) {
            case CREATED -> dataItem.getSequenceIndex();
            case NAME -> normalizeText(dataItem.getName());
            case EXTERNAL_INDEX -> dataItem.getExternalIndex();
            case EXTERNAL_ID -> normalizeText(dataItem.getExternalId());
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static int compareNullableSortValues(Comparable left, Comparable right, boolean ascending) {
        if (left == null && right == null) return 0;
        if (left == null) return 1;
        if (right == null) return -1;
        int compared = left.compareTo(right);
        return ascending ? compared : -compared;
    }

    private static boolean matchesDataItemFilters(
            DataItem dataItem,
            Map<String, DataType> dataTypesById,
            Map<String, List<DataItemVerification>> dataItemVerificationsByItemId,
            Function<DataItem, DataItemRevisionDto> revisionResolver,
            DataItemFilterOptions options
    ) {
        if (options.verified() != null) {
            boolean isVerified = Boolean.TRUE.equals(dataItem.isVerified());
            if (!Objects.equals(options.verified(), isVerified)) {
                return false;
            }
        }

        if (options.hasRevisions() != null) {
            DataItemRevisionDto revision = revisionResolver.apply(dataItem);
            boolean hasRevisions = revision != null
                    && revision.isEnabled()
                    && revision.getReplaces() != null
                    && !revision.getReplaces().isEmpty();
            if (!Objects.equals(options.hasRevisions(), hasRevisions)) {
                return false;
            }
        }

        if (options.hasVerifications() != null) {
            boolean hasVerifications = !dataItemVerificationsByItemId
                    .getOrDefault(dataItem.getId(), List.of())
                    .isEmpty();
            if (!Objects.equals(options.hasVerifications(), hasVerifications)) {
                return false;
            }
        }

        DataType dataType = dataTypesById.get(dataItem.getDataTypeId());
        if (options.dataType() != null) {
            String dataTypeName = normalizeText(dataType == null ? null : dataType.getName());
            if (!normalizeText(options.dataType()).equals(dataTypeName)) {
                return false;
            }
        }

        if (options.query() == null) {
            return true;
        }

        return matchesSearchQuery(dataItem, dataType, options.query(), options.searchFields());
    }

    private static boolean matchesSearchQuery(
            DataItem dataItem,
            DataType dataType,
            String query,
            EnumSet<DataItemSearchField> searchFields
    ) {
        String[] tokens = normalizeText(query).split("\\s+");
        List<String> nonBlankTokens = new ArrayList<>();
        for (String token : tokens) {
            if (!token.isBlank()) {
                nonBlankTokens.add(token);
            }
        }
        if (nonBlankTokens.isEmpty()) {
            return true;
        }

        List<String> values = new ArrayList<>();
        if (searchFields.contains(DataItemSearchField.NAME)) {
            values.add(normalizeText(dataItem.getName()));
        }
        if (searchFields.contains(DataItemSearchField.DESCRIPTION)) {
            values.add(normalizeText(dataItem.getDescription()));
        }
        if (searchFields.contains(DataItemSearchField.CONTENT)) {
            values.add(normalizeText(dataItem.getContent()));
        }
        if (searchFields.contains(DataItemSearchField.EXTERNAL_ID)) {
            values.add(normalizeText(dataItem.getExternalId()));
        }
        if (searchFields.contains(DataItemSearchField.EXTERNAL_INDEX)) {
            BigInteger externalIndex = dataItem.getExternalIndex();
            values.add(externalIndex == null ? "" : externalIndex.toString().toLowerCase(Locale.ROOT));
        }
        if (searchFields.contains(DataItemSearchField.OBJECT_ID)) {
            values.add(normalizeText(dataItem.getId()));
        }
        if (searchFields.contains(DataItemSearchField.DATA_TYPE)) {
            values.add(normalizeText(dataType == null ? null : dataType.getName()));
        }
        if (searchFields.contains(DataItemSearchField.CREATOR_ADDR)) {
            values.add(normalizeText(dataItem.getCreator() == null ? null : dataItem.getCreator().getCreatorAddr()));
        }

        return nonBlankTokens.stream().allMatch(
                token -> values.stream().anyMatch(value -> value.contains(token))
        );
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlankOrUndefined(String value) {
        return value == null || value.isBlank() || "undefined".equals(value);
    }

    private ContainerTreeDto getContainerTreeAcrossContainers(
            String normalizedDataTypeId,
            String normalizedDataItemId,
            String normalizedDataItemVerificationId,
            Boolean dataItemVerificationVerified,
            RecipientScope normalizedDataItemRecipientScope,
            RecipientScope normalizedDataItemVerificationRecipientScope,
            String normalizedRecipientAddress,
            DataItemFilterOptions dataItemFilters,
            String normalizedDomain,
            ContainerScope normalizedContainerScope,
            String creatorAddr,
            int page,
            int pageSize,
            EnumSet<ContainerTreeIncludeEnum> safeIncludes,
            boolean includeDataItemVerifications
    ) {
        List<Container> scopedContainers = resolveContainerScope(
                normalizedDomain,
                normalizedContainerScope,
                creatorAddr
        );

        Map<String, Container> containersById = scopedContainers.stream()
                .filter(container -> container != null && container.getId() != null)
                .collect(Collectors.toMap(
                        Container::getId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        List<DataType> loadedDataTypes = new ArrayList<>();
        if (normalizedDataTypeId != null) {
            dataTypeRepository.findById(normalizedDataTypeId)
                    .filter(dataType -> containersById.containsKey(dataType.getContainerId()))
                    .filter(dataType ->
                            normalizedDomain == null
                                    || publishTargetRepository.findByDomainAndDataTypeId(
                                            normalizedDomain,
                                            dataType.getId()
                                    ).isPresent()
                    )
                    .ifPresent(loadedDataTypes::add);
        } else {
            for (Container container : containersById.values()) {
                List<DataType> containerDataTypes = normalizedDomain != null
                        ? dataTypeRepository.findByContainerAndPublishTargetDomain(
                                container.getId(),
                                normalizedDomain
                        )
                        : dataTypeRepository.findByContainer(container.getId());
                loadedDataTypes.addAll(containerDataTypes);
            }
        }

        Map<String, DataType> dataTypesById = loadedDataTypes.stream()
                .filter(dataType -> dataType != null && dataType.getId() != null)
                .collect(Collectors.toMap(
                        DataType::getId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        List<DataItem> candidateItems = new ArrayList<>();
        if (normalizedDataItemId != null) {
            dataItemRepository.findById(normalizedDataItemId)
                    .filter(dataItem -> containersById.containsKey(dataItem.getContainerId()))
                    .filter(dataItem ->
                            normalizedDataTypeId == null
                                    || dataTypesById.containsKey(dataItem.getDataTypeId())
                    )
                    .filter(dataItem ->
                            normalizedDomain == null
                                    || publishTargetRepository.findByDomainAndDataItemId(
                                            normalizedDomain,
                                            dataItem.getId()
                                    ).isPresent()
                    )
                    .ifPresent(candidateItems::add);
        } else if (!dataTypesById.isEmpty()) {
            Map<String, List<String>> dataTypeIdsByContainer = dataTypesById.values().stream()
                    .collect(Collectors.groupingBy(
                            DataType::getContainerId,
                            LinkedHashMap::new,
                            Collectors.mapping(DataType::getId, Collectors.toList())
                    ));

            for (Map.Entry<String, List<String>> entry : dataTypeIdsByContainer.entrySet()) {
                List<String> containerDataTypeIds = entry.getValue();
                if (containerDataTypeIds == null || containerDataTypeIds.isEmpty()) {
                    continue;
                }

                List<DataItem> itemsForContainer = dataItemRepository.findByContainerAndDataTypeIds(
                        entry.getKey(),
                        containerDataTypeIds
                );

                if (normalizedDomain != null) {
                    itemsForContainer = itemsForContainer.stream()
                            .filter(dataItem ->
                                    publishTargetRepository.findByDomainAndDataItemId(
                                            normalizedDomain,
                                            dataItem.getId()
                                    ).isPresent()
                            )
                            .toList();
                }

                candidateItems.addAll(itemsForContainer);
            }
        }

        if (normalizedDataItemRecipientScope != RecipientScope.ALL) {
            candidateItems = candidateItems.stream()
                    .filter(item ->
                            matchesRecipientScope(
                                    item.getRecipients(),
                                    normalizedDataItemRecipientScope,
                                    normalizedRecipientAddress
                            )
                    )
                    .toList();
        }

        List<String> candidateItemIds = candidateItems.stream()
                .map(DataItem::getId)
                .toList();
        boolean needsVerifications =
                includeDataItemVerifications
                        || normalizedDataItemVerificationId != null
                        || dataItemVerificationVerified != null
                        || dataItemFilters.hasVerifications() != null;

        List<DataItemVerification> filteredDataItemVerifications =
                needsVerifications && !candidateItemIds.isEmpty()
                        ? dataItemVerificationRepository.findByDataItemIdIn(candidateItemIds)
                        : List.of();

        if (normalizedDataItemVerificationId != null) {
            filteredDataItemVerifications = filteredDataItemVerifications.stream()
                    .filter(verification -> normalizedDataItemVerificationId.equals(verification.getId()))
                    .toList();
        }

        if (dataItemVerificationVerified != null) {
            filteredDataItemVerifications = filteredDataItemVerifications.stream()
                    .filter(verification -> Objects.equals(dataItemVerificationVerified, verification.getVerified()))
                    .toList();
        }

        if (normalizedDataItemVerificationRecipientScope != RecipientScope.ALL) {
            filteredDataItemVerifications = filteredDataItemVerifications.stream()
                    .filter(verification ->
                            matchesRecipientScope(
                                    verification.getRecipients(),
                                    normalizedDataItemVerificationRecipientScope,
                                    normalizedRecipientAddress
                            )
                    )
                    .toList();
        }

        Map<String, List<DataItemVerification>> dataItemVerificationsByItemId =
                filteredDataItemVerifications.stream()
                        .collect(Collectors.groupingBy(DataItemVerification::getDataItemId));

        boolean dataItemVerificationFiltered =
                normalizedDataItemVerificationId != null
                        || dataItemVerificationVerified != null
                        || normalizedDataItemVerificationRecipientScope != RecipientScope.ALL;

        Map<String, DataItemRevisionDto> revisionDtoCache = new HashMap<>();
        Function<DataItem, DataItemRevisionDto> revisionResolver =
                dataItem -> revisionDtoCache.computeIfAbsent(
                        dataItem.getId(),
                        ignored -> buildRevisionDto(dataItem)
                );

        List<DataItem> filteredItems = candidateItems.stream()
                .filter(dataItem ->
                        !dataItemVerificationFiltered
                                || !dataItemVerificationsByItemId
                                        .getOrDefault(dataItem.getId(), List.of())
                                        .isEmpty()
                )
                .filter(dataItem -> matchesDataItemFilters(
                        dataItem,
                        dataTypesById,
                        dataItemVerificationsByItemId,
                        revisionResolver,
                        dataItemFilters
                ))
                .toList();

        List<DataItem> orderedItems = sortDataItems(filteredItems, dataItemFilters);

        long totalDataItems = orderedItems.size();
        int totalPages = totalDataItems == 0 ? 0 : (int) Math.ceil((double) totalDataItems / pageSize);
        int fromIndex = Math.min(page * pageSize, orderedItems.size());
        int toIndex = Math.min(fromIndex + pageSize, orderedItems.size());
        boolean hasNext = toIndex < orderedItems.size();
        List<DataItem> pagedItems = orderedItems.subList(fromIndex, toIndex);

        Map<String, List<DataItemVerification>> pagedVerificationsByItemId = includeDataItemVerifications
                ? pagedItems.stream().map(DataItem::getId).collect(Collectors.toMap(
                        Function.identity(),
                        id -> dataItemVerificationsByItemId.getOrDefault(id, List.of()),
                        (left, right) -> left,
                        LinkedHashMap::new
                ))
                : Map.of();

        Map<String, List<DataItem>> pagedItemsByContainer = pagedItems.stream()
                .collect(Collectors.groupingBy(
                        DataItem::getContainerId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<ContainerNodeDto> containerNodes = new ArrayList<>();
        for (Container container : containersById.values()) {
            List<DataItem> containerItems = pagedItemsByContainer.getOrDefault(container.getId(), List.of());
            if (containerItems.isEmpty()) {
                continue;
            }

            Map<String, List<DataItem>> itemsByTypeId = containerItems.stream()
                    .collect(Collectors.groupingBy(
                            DataItem::getDataTypeId,
                            LinkedHashMap::new,
                            Collectors.toList()
                    ));

            List<DataTypeNodeDto> typeNodes = new ArrayList<>();
            for (Map.Entry<String, List<DataItem>> entry : itemsByTypeId.entrySet()) {
                DataType dataType = dataTypesById.get(entry.getKey());
                if (dataType == null) {
                    continue;
                }

                List<DataItemNodeDto> itemDtos = entry.getValue().stream()
                        .map(dataItem -> new DataItemNodeDto(
                                dataItem,
                                includeDataItemVerifications
                                        ? pagedVerificationsByItemId.getOrDefault(dataItem.getId(), List.of())
                                        : List.of(),
                                revisionResolver.apply(dataItem)
                        ))
                        .toList();

                typeNodes.add(new DataTypeNodeDto(dataType, itemDtos));
            }

            if (!typeNodes.isEmpty()) {
                containerNodes.add(new ContainerNodeDto(container, typeNodes));
            }
        }

        long returnedDataItemVerifications = containerNodes.stream()
                .flatMap(containerNode -> containerNode.getDataTypes().stream())
                .flatMap(typeNode -> typeNode.getDataItems().stream())
                .mapToLong(itemNode -> itemNode.getDataItemVerifications().size())
                .sum();

        Map<String, Object> meta = buildMeta(
                "data_item",
                page,
                pageSize,
                safeIncludes,
                null,
                normalizedDataTypeId,
                normalizedDataItemId,
                normalizedDataItemVerificationId,
                dataItemVerificationVerified,
                recipientScopeToApiValue(normalizedDataItemRecipientScope),
                recipientScopeToApiValue(normalizedDataItemVerificationRecipientScope),
                normalizedRecipientAddress,
                dataItemFilters.query(),
                toSearchFieldsCsv(dataItemFilters.searchFields()),
                dataItemFilters.verified(),
                dataItemFilters.hasRevisions(),
                dataItemFilters.hasVerifications(),
                dataItemFilters.dataType(),
                dataItemFilters.sortBy().name().toLowerCase(Locale.ROOT),
                dataItemFilters.sortAscending() ? "asc" : "desc",
                normalizedDomain,
                containerScopeToApiValue(normalizedContainerScope),
                containersById.size(),
                containerNodes.size(),
                dataTypesById.size(),
                totalDataItems,
                returnedDataItemVerifications,
                totalPages,
                hasNext,
                false
        );
        meta.put("returnedDataItems", pagedItems.size());
        meta.put(
                "availableDataTypes",
                dataTypesById.values().stream()
                        .map(DataType::getName)
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(name -> !name.isEmpty())
                        .distinct()
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList()
        );

        return new ContainerTreeDto(containerNodes, meta);
    }

    private List<Container> resolveContainerScope(
            String normalizedDomain,
            ContainerScope requestedContainerScope,
            String creatorAddr
    ) {
        ContainerScope effectiveScope =
                requestedContainerScope == null ? ContainerScope.ACCESSIBLE : requestedContainerScope;

        if (effectiveScope == ContainerScope.ALL) {
            return normalizedDomain != null
                    ? containerRepository.findByPublishTargetDomain(normalizedDomain)
                    : containerRepository.findAll();
        }

        List<Container> accessibleContainers =
                containerRepository.findAccessibleContainers(creatorAddr, Pageable.unpaged());

        if (normalizedDomain == null || accessibleContainers.isEmpty()) {
            return accessibleContainers;
        }

        List<String> accessibleContainerIds = accessibleContainers.stream()
                .map(Container::getId)
                .filter(Objects::nonNull)
                .toList();

        if (accessibleContainerIds.isEmpty()) {
            return List.of();
        }

        Set<String> domainScopedContainerIds = publishTargetRepository
                .findByDomainAndContainerIdIn(normalizedDomain, accessibleContainerIds)
                .stream()
                .map(target -> target.getContainerId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (domainScopedContainerIds.isEmpty()) {
            return List.of();
        }

        return accessibleContainers.stream()
                .filter(container -> domainScopedContainerIds.contains(container.getId()))
                .toList();
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
        List<OffchainDataItemRevision> effectiveIndexedRows = indexedRows.stream()
                .filter(row -> !isReferenceDerivedRevisionRow(row))
                .toList();

        if (!effectiveIndexedRows.isEmpty()) {
            boolean enabled = effectiveIndexedRows.stream().anyMatch(OffchainDataItemRevision::isEnabled);
            List<String> replaces = effectiveIndexedRows.stream()
                    .map(OffchainDataItemRevision::getReplacedDataItemId)
                    .filter(Objects::nonNull)
                    .filter(id -> !id.isBlank())
                    .distinct()
                    .toList();
            return new DataItemRevisionDto(enabled, replaces);
        }

        RevisionExtraction extraction = extractRevisionExtraction(dataItem.getContent());
        return new DataItemRevisionDto(
                extraction.enabled(),
                extraction.replaces()
        );
    }

    private static boolean isReferenceDerivedRevisionRow(OffchainDataItemRevision row) {
        if (row == null) return false;
        String source = row.getSource();
        return source != null && "references".equalsIgnoreCase(source.trim());
    }

    private RevisionExtraction extractRevisionExtraction(String content) {
        if (content == null || content.isBlank()) {
            return new RevisionExtraction(false, List.of());
        }

        try {
            ContentEncodingUtils.DecodedContent decodedContent =
                    ContentEncodingUtils.decodeForProcessing(content);
            String contentToParse = decodedContent.decoded() ? decodedContent.content() : content;

            Map<String, Object> contentMap = mapper.readValue(
                    contentToParse,
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
                return new RevisionExtraction(true, List.of());
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

                return new RevisionExtraction(true, normalizeObjectIds(explicitPreviousIds));
            }

            List<String> explicitPreviousIds = normalizeObjectIds(revisionsObject);
            return new RevisionExtraction(true, explicitPreviousIds);
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
            String dataItemRecipientScope,
            String dataItemVerificationRecipientScope,
            String recipientAddress,
            String dataItemQuery,
            String dataItemSearchFields,
            Boolean dataItemVerified,
            Boolean dataItemHasRevisions,
            Boolean dataItemHasVerifications,
            String dataItemDataType,
            String dataItemSortBy,
            String dataItemSortDirection,
            String domain,
            String containerScope,
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
        filters.put("dataItemRecipientScope", dataItemRecipientScope);
        filters.put("dataItemVerificationRecipientScope", dataItemVerificationRecipientScope);
        filters.put("recipientAddress", recipientAddress);
        filters.put("dataItemQuery", dataItemQuery);
        filters.put("dataItemSearchFields", dataItemSearchFields);
        filters.put("dataItemVerified", dataItemVerified);
        filters.put("dataItemHasRevisions", dataItemHasRevisions);
        filters.put("dataItemHasVerifications", dataItemHasVerifications);
        filters.put("dataItemDataType", dataItemDataType);
        filters.put("dataItemSortBy", dataItemSortBy);
        filters.put("dataItemSortDirection", dataItemSortDirection);
        filters.put("domain", domain);
        filters.put("containerScope", containerScope);

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
