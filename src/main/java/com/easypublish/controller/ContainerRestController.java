package com.easypublish.controller;

import com.easypublish.dtos.ContainerTreeDto;
import com.easypublish.dtos.ContainerTreeIncludeEnum;
import com.easypublish.dtos.LinkGraphRequestDto;
import com.easypublish.dtos.LinkGraphResponseDto;
import com.easypublish.entities.offchain.FollowedContainer;
import com.easypublish.entities.offchain.OffchainFollowContainer;
import com.easypublish.entities.onchain.Container;
import com.easypublish.entities.onchain.DataItem;
import com.easypublish.entities.onchain.DataType;
import com.easypublish.repositories.ContainerRepository;
import com.easypublish.repositories.FollowedContainerRepository;
import com.easypublish.repositories.OffchainFollowContainerRepository;
import com.easypublish.service.NodeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * REST API for browsing containers/data tree and follow/unfollow operations.
 */
@RestController
public class ContainerRestController {
    private static final int DEFAULT_PAGE = 0;
    private static final int MAX_PAGE_SIZE = 200;

    private final NodeService nodeService;
    private final FollowedContainerRepository followedContainerRepository;
    private final OffchainFollowContainerRepository offchainFollowContainerRepository;
    private final ContainerRepository containerRepository;

    // Extracted to properties: app.domain.default-public
    @Value("${app.domain.default-public:izipublish.com}")
    private String defaultPublicDomain;

    public ContainerRestController(
            NodeService nodeService,
            FollowedContainerRepository followedContainerRepository,
            OffchainFollowContainerRepository offchainFollowContainerRepository,
            ContainerRepository containerRepository
    ) {
        this.nodeService = nodeService;
        this.followedContainerRepository = followedContainerRepository;
        this.offchainFollowContainerRepository = offchainFollowContainerRepository;
        this.containerRepository = containerRepository;
    }

    @GetMapping("/api/items")
    public ContainerTreeDto getItems(
            @RequestParam(required = false) String include,
            @RequestParam String userAddress,
            @RequestParam(required = false) String containerId,
            @RequestParam(required = false) String dataTypeId,
            @RequestParam(required = false) String dataItemId,
            @RequestParam(required = false) String dataItemVerificationId,
            @RequestParam(required = false) Boolean dataItemVerificationVerified,
            @RequestParam(required = false) String dataItemRecipientScope,
            @RequestParam(required = false) String dataItemVerificationRecipientScope,
            @RequestParam(required = false) String recipientAddress,
            @RequestParam(required = false) String containerScope,
            @RequestParam(required = false) String dataItemQuery,
            @RequestParam(required = false) String dataItemSearchFields,
            @RequestParam(required = false) Boolean dataItemVerified,
            @RequestParam(required = false) Boolean dataItemHasRevisions,
            @RequestParam(required = false) Boolean dataItemHasVerifications,
            @RequestParam(required = false) String dataItemDataType,
            @RequestParam(required = false) String dataItemSortBy,
            @RequestParam(required = false) String dataItemSortDirection,
            @RequestParam(required = false) String domain,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {

        if (userAddress == null || userAddress.isBlank()) {
            throw new IllegalArgumentException("userAddress is required");
        }

        int safePage = Math.max(DEFAULT_PAGE, page);
        int safePageSize = Math.min(MAX_PAGE_SIZE, Math.max(1, pageSize));

        EnumSet<ContainerTreeIncludeEnum> includes = parseIncludes(include, containerId);

        domain = normalizeDomain(domain);

        return nodeService.getContainerTree(
                containerId,
                dataTypeId,
                dataItemId,
                dataItemVerificationId,
                dataItemVerificationVerified,
                dataItemRecipientScope,
                dataItemVerificationRecipientScope,
                recipientAddress,
                containerScope,
                dataItemQuery,
                dataItemSearchFields,
                dataItemVerified,
                dataItemHasRevisions,
                dataItemHasVerifications,
                dataItemDataType,
                dataItemSortBy,
                dataItemSortDirection,
                userAddress,
                domain,
                safePage,
                safePageSize,
                includes
        );
    }

    @GetMapping("/api/container-child-links")
    public Map<String, Object> getContainerChildLinks(
            @RequestParam String userAddress,
            @RequestParam(required = false) String containerId,
            @RequestParam(required = false) String containerScope,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String searchFields,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDirection,
            @RequestParam(required = false) String domain,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        if (userAddress == null || userAddress.isBlank()) {
            throw new IllegalArgumentException("userAddress is required");
        }

        int safePage = Math.max(DEFAULT_PAGE, page);
        int safePageSize = Math.min(MAX_PAGE_SIZE, Math.max(1, pageSize));
        String normalizedDomain = normalizeDomain(domain);

        return nodeService.getContainerChildLinks(
                userAddress,
                containerId,
                containerScope,
                query,
                searchFields,
                sortBy,
                sortDirection,
                normalizedDomain,
                safePage,
                safePageSize
        );
    }

    @GetMapping("/api/owners")
    public Map<String, Object> getOwners(
            @RequestParam String userAddress,
            @RequestParam(required = false) String containerId,
            @RequestParam(required = false) String containerScope,
            @RequestParam(required = false) String ownerStatus,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String searchFields,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDirection,
            @RequestParam(required = false) String domain,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        if (userAddress == null || userAddress.isBlank()) {
            throw new IllegalArgumentException("userAddress is required");
        }

        int safePage = Math.max(DEFAULT_PAGE, page);
        int safePageSize = Math.min(MAX_PAGE_SIZE, Math.max(1, pageSize));
        String normalizedDomain = normalizeDomain(domain);

        return nodeService.getOwners(
                userAddress,
                containerId,
                containerScope,
                ownerStatus,
                query,
                searchFields,
                sortBy,
                sortDirection,
                normalizedDomain,
                safePage,
                safePageSize
        );
    }

    @PostMapping("/api/link-graph")
    public LinkGraphResponseDto getLinkGraph(
            @RequestBody(required = false) LinkGraphRequestDto request
    ) {
        return nodeService.getLinkGraph(request);
    }

    private static EnumSet<ContainerTreeIncludeEnum> parseIncludes(String include, String containerId) {
        EnumSet<ContainerTreeIncludeEnum> includes = ContainerTreeIncludeEnum.fromCsv(include);

        if (includes.isEmpty()) {
            boolean singleContainerMode = containerId != null && !containerId.isBlank();
            if (singleContainerMode) {
                includes = EnumSet.of(
                        ContainerTreeIncludeEnum.CONTAINER,
                        ContainerTreeIncludeEnum.DATA_TYPE,
                        ContainerTreeIncludeEnum.DATA_ITEM,
                        ContainerTreeIncludeEnum.DATA_ITEM_VERIFICATION
                );
            } else {
                includes = EnumSet.of(ContainerTreeIncludeEnum.CONTAINER);
            }
        } else {
            includes.add(ContainerTreeIncludeEnum.CONTAINER);
            if (includes.contains(ContainerTreeIncludeEnum.DATA_ITEM)) {
                includes.add(ContainerTreeIncludeEnum.DATA_ITEM_VERIFICATION);
            }
        }

        return includes;
    }

    private String normalizeDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return null;
        }

        if (defaultPublicDomain.equals(domain)) {
            return null;
        }

        return domain;
    }

    @GetMapping("/api/containers/{id}")
    public Container getContainer(@PathVariable String id) {
        return nodeService.getContainer(id);
    }

    @GetMapping("/api/data-types/{id}")
    public DataType getDataType(@PathVariable String id) {
        return nodeService.getDataType(id);
    }

    @GetMapping("/api/data-items/{id}")
    public DataItem getDataItem(@PathVariable String id) {
        return nodeService.getDataItem(id);
    }

    @PostMapping("/api/follow-container")
    @Transactional
    public Map<String, Object> followContainers(
            @RequestParam String userAddress,
            @RequestParam List<String> containerIds
    ) {
        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Follow updates are on-chain now. Publish a data item with easy_publish.follow_containers."
        );
    }

    @DeleteMapping("/api/follow-container")
    @Transactional
    public void unfollowContainer(
            @RequestParam String userAddress,
            @RequestParam String containerId
    ) {
        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Unfollow is on-chain now. Publish a data item with easy_publish.follow_containers and enabled=false."
        );
    }

    @DeleteMapping("/api/follow-containers")
    @Transactional
    public void unfollowAllContainers(
            @RequestParam String userAddress
    ) {
        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Bulk unfollow is on-chain now. Publish a data item with easy_publish.follow_containers entries enabled=false."
        );
    }

    @GetMapping("/api/followed-containers")
    public Map<String, Object> getFollowedContainers(
            @RequestParam String userAddress,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        int safePage = Math.max(DEFAULT_PAGE, page);
        int safePageSize = Math.min(MAX_PAGE_SIZE, Math.max(1, pageSize));

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

        List<String> activeContainerIds = new ArrayList<>();
        for (Map.Entry<String, Boolean> entry : latestByContainer.entrySet()) {
            if (Boolean.TRUE.equals(entry.getValue())) {
                activeContainerIds.add(entry.getKey());
            }
        }

        if (activeContainerIds.isEmpty()) {
            List<String> fallback = followedContainerRepository.findByUserAddress(userAddress).stream()
                    .map(FollowedContainer::getContainerId)
                    .filter(Objects::nonNull)
                    .toList();
            activeContainerIds.addAll(fallback);
        }

        int totalElements = activeContainerIds.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / safePageSize);
        int fromIndex = Math.min(safePage * safePageSize, totalElements);
        int toIndex = Math.min(fromIndex + safePageSize, totalElements);

        List<String> pageContainerIds = activeContainerIds.subList(fromIndex, toIndex);
        Map<String, Container> containersById = containerRepository.findAllById(pageContainerIds).stream()
                .collect(LinkedHashMap::new, (map, container) -> map.put(container.getId(), container), Map::putAll);

        List<Map<String, String>> containerDTOs = new ArrayList<>();
        for (String containerId : pageContainerIds) {
            Container container = containersById.get(containerId);
            if (container == null) {
                continue;
            }
            containerDTOs.add(Map.of(
                    "id", container.getId(),
                    "name", container.getName() == null ? "" : container.getName()
            ));
        }

        return Map.of(
                "content", containerDTOs,
                "page", safePage,
                "pageSize", safePageSize,
                "totalElements", totalElements,
                "totalPages", totalPages
        );
    }
}
