package com.easypublish.service;

import com.easypublish.dtos.ContainerNodeDto;
import com.easypublish.dtos.ContainerTreeDto;
import com.easypublish.dtos.ContainerTreeIncludeEnum;
import com.easypublish.dtos.DataItemNodeDto;
import com.easypublish.dtos.DataTypeNodeDto;
import com.easypublish.entities.UserDataEntity;
import com.easypublish.entities.onchain.Container;
import com.easypublish.entities.onchain.DataItem;
import com.easypublish.entities.onchain.DataItemVerification;
import com.easypublish.entities.onchain.DataType;
import com.easypublish.repositories.ContainerRepository;
import com.easypublish.repositories.DataItemRepository;
import com.easypublish.repositories.DataItemVerificationRepository;
import com.easypublish.repositories.DataTypeRepository;
import com.easypublish.repositories.UserDataRepository;
import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.List;
import java.util.Map;
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

    private final UserDataRepository userRepository;
    private final ObjectMapper mapper;
    private final DataItemRepository dataItemRepository;
    private final DataTypeRepository dataTypeRepository;
    private final ContainerRepository containerRepository;
    private final DataItemVerificationRepository dataItemVerificationRepository;
    private final NodeQueryService nodeQueryService;

    // Extracted to properties for environment portability:
    // app.node.binary, app.node.items-script, app.node.directory
    private final String nodeBinary;
    private final String nodeScript;
    private final File nodeDirectory;

    public NodeService(
            UserDataRepository userRepository,
            ObjectMapper mapper,
            DataItemRepository dataItemRepository,
            DataTypeRepository dataTypeRepository,
            ContainerRepository containerRepository,
            DataItemVerificationRepository dataItemVerificationRepository,
            NodeQueryService nodeQueryService,
            @Value("${app.node.binary:node}") String nodeBinary,
            @Value("${app.node.items-script:getItems.js}") String nodeScript,
            @Value("${app.node.directory:./node}") String nodeDirectory
    ) {
        this.userRepository = userRepository;
        this.mapper = mapper;
        this.dataItemRepository = dataItemRepository;
        this.dataTypeRepository = dataTypeRepository;
        this.containerRepository = containerRepository;
        this.dataItemVerificationRepository = dataItemVerificationRepository;
        this.nodeQueryService = nodeQueryService;
        this.nodeBinary = nodeBinary;
        this.nodeScript = nodeScript;
        this.nodeDirectory = new File(nodeDirectory);
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

        Process process = pb.start();
        String output = collectProcessOutput(process);
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            log.error("Node process failed:\n{}", output);
            throw new RuntimeException("Node process failed");
        }

        try {
            return mapper.readValue(output, new TypeReference<List<Map<String, Object>>>() {});
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
            String creatorAddr,
            String domain,
            int page,
            int pageSize,
            EnumSet<ContainerTreeIncludeEnum> includes
    ) {
        if (containerId == null || containerId.isBlank()) {
            Pageable pageable = PageRequest.of(page, pageSize);

            List<Container> containers = (domain != null && !domain.isBlank())
                    ? containerRepository.findByPublishTargetDomain(domain, pageable)
                    : containerRepository.findAccessibleContainers(creatorAddr, pageable);

            List<ContainerNodeDto> containerNodes = containers.stream()
                    .map(container -> new ContainerNodeDto(container, List.of()))
                    .toList();

            return new ContainerTreeDto(containerNodes);
        }

        Container container = containerRepository.findById(containerId)
                .orElseThrow(() -> new IllegalArgumentException("Container not found"));

        List<DataTypeNodeDto> typeNodes = List.of();

        if (includes.contains(ContainerTreeIncludeEnum.DATA_TYPE) ||
                includes.contains(ContainerTreeIncludeEnum.DATA_ITEM)) {

            List<DataType> dataTypes;
            if (dataTypeId != null && !dataTypeId.isBlank()) {
                dataTypes = dataTypeRepository.findByIdAndContainer(dataTypeId, container.getId())
                        .map(List::of)
                        .orElse(List.of());
            } else if (domain != null && !domain.isBlank()) {
                dataTypes = dataTypeRepository.findByContainerAndPublishTargetDomain(container.getId(), domain);
            } else {
                dataTypes = dataTypeRepository.findByContainer(container.getId());
            }

            List<String> dataTypeIds = dataTypes.stream().map(DataType::getId).toList();

            if (!dataTypeIds.isEmpty() && includes.contains(ContainerTreeIncludeEnum.DATA_ITEM)) {
                Pageable pageable = PageRequest.of(page, pageSize);

                Page<DataItem> itemPage = dataItemRepository.findByContainerIdAndDataTypeIdInAndOptionalDomain(
                        container.getId(),
                        dataTypeIds,
                        (domain == null || domain.isBlank()) ? null : domain,
                        pageable
                );

                List<DataItem> pagedItems = itemPage.getContent();
                List<String> itemIds = pagedItems.stream().map(DataItem::getId).toList();
                List<DataItemVerification> verifications = itemIds.isEmpty()
                        ? List.of()
                        : dataItemVerificationRepository.findByDataItemIdIn(itemIds);

                Map<String, List<DataItemVerification>> verificationsByItemId = verifications.stream()
                        .collect(Collectors.groupingBy(DataItemVerification::getDataItemId));

                Map<String, List<DataItem>> itemsByTypeId = pagedItems.stream()
                        .collect(Collectors.groupingBy(DataItem::getDataTypeId));

                typeNodes = dataTypes.stream()
                        .map(dt -> {
                            List<DataItem> items = itemsByTypeId.getOrDefault(dt.getId(), List.of());
                            List<DataItemNodeDto> itemDtos = items.stream()
                                    .map(di -> new DataItemNodeDto(
                                            di,
                                            verificationsByItemId.getOrDefault(di.getId(), List.of())
                                    ))
                                    .toList();

                            return new DataTypeNodeDto(dt, itemDtos);
                        })
                        .toList();
            } else {
                typeNodes = dataTypes.stream()
                        .map(dt -> new DataTypeNodeDto(dt, List.of()))
                        .toList();
            }
        }

        return new ContainerTreeDto(List.of(new ContainerNodeDto(container, typeNodes)));
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

    private static String collectProcessOutput(Process process) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}
