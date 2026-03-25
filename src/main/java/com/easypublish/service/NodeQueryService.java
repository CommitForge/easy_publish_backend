package com.easypublish.service;

import com.easypublish.repositories.ContainerRepository;
import com.easypublish.repositories.DataItemRepository;
import com.easypublish.repositories.DataTypeRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * DB-backed query facade for node object fetches by logical type.
 */
@Service
public class NodeQueryService {

    private static final String TYPE_CONTAINER = "container";
    private static final String TYPE_DATA_TYPE = "data_type";
    private static final String TYPE_DATA_ITEM = "data_item";

    private final ContainerRepository containerRepository;
    private final DataTypeRepository dataTypeRepository;
    private final DataItemRepository dataItemRepository;

    public NodeQueryService(
            ContainerRepository containerRepository,
            DataTypeRepository dataTypeRepository,
            DataItemRepository dataItemRepository
    ) {
        this.containerRepository = containerRepository;
        this.dataTypeRepository = dataTypeRepository;
        this.dataItemRepository = dataItemRepository;
    }

    /**
     * Fetch objects by type with optional container/dataType filtering.
     */
    public List<?> findByType(
            String type,
            String creatorAddr,
            String containerId,
            String dataTypeId
    ) {
        boolean hasContainer = containerId != null && !containerId.isBlank();
        boolean hasDataType = dataTypeId != null && !dataTypeId.isBlank();
        String normalizedType = type == null ? "" : type.trim().toLowerCase();

        switch (normalizedType) {
            case TYPE_CONTAINER:
                return containerRepository.findByCreator_creatorAddr(creatorAddr);
            case TYPE_DATA_TYPE:
                return hasContainer
                        ? dataTypeRepository.findByContainer(containerId)
                        : dataTypeRepository.findByCreatorAddr(creatorAddr);
            case TYPE_DATA_ITEM:
                if (hasContainer && hasDataType) {
                    return dataItemRepository.findByContainerAndDataType(containerId, dataTypeId);
                }
                if (hasContainer) {
                    return dataItemRepository.findByContainer(containerId);
                }
                return dataItemRepository.findByCreatorAddr(creatorAddr);
            default:
                throw new IllegalArgumentException("Unknown type: " + type);
        }
    }
}
