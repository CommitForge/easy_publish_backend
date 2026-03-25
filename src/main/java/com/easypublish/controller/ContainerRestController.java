package com.easypublish.controller;

import com.easypublish.dtos.ContainerTreeDto;
import com.easypublish.dtos.ContainerTreeIncludeEnum;
import com.easypublish.entities.offchain.FollowedContainer;
import com.easypublish.entities.onchain.Container;
import com.easypublish.entities.onchain.DataItem;
import com.easypublish.entities.onchain.DataType;
import com.easypublish.repositories.ContainerRepository;
import com.easypublish.repositories.FollowedContainerRepository;
import com.easypublish.service.NodeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * REST API for browsing containers/data tree and follow/unfollow operations.
 */
@RestController
public class ContainerRestController {

    private final NodeService nodeService;
    private final FollowedContainerRepository followedContainerRepository;
    private final ContainerRepository containerRepository;

    // Extracted to properties: app.follow.max-per-user
    @Value("${app.follow.max-per-user:1000}")
    private int maxFollowsPerUser;

    // Extracted to properties: app.domain.default-public
    @Value("${app.domain.default-public:izipublish.com}")
    private String defaultPublicDomain;

    public ContainerRestController(
            NodeService nodeService,
            FollowedContainerRepository followedContainerRepository,
            ContainerRepository containerRepository
    ) {
        this.nodeService = nodeService;
        this.followedContainerRepository = followedContainerRepository;
        this.containerRepository = containerRepository;
    }

    @GetMapping("/api/items")
    public ContainerTreeDto getItems(
            @RequestParam(required = false) String include,
            @RequestParam String userAddress,
            @RequestParam(required = false) String containerId,
            @RequestParam(required = false) String dataTypeId,
            @RequestParam(required = false) String domain,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {

        if (userAddress == null || userAddress.isBlank()) {
            throw new IllegalArgumentException("userAddress is required");
        }

        EnumSet<ContainerTreeIncludeEnum> includes =
                ContainerTreeIncludeEnum.fromCsv(include);

        if (defaultPublicDomain.equals(domain)) {
            domain = null;
        }

        return nodeService.getContainerTree(
                containerId,
                dataTypeId,
                userAddress,
                domain,
                page,
                pageSize,
                includes
        );
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
        int added = 0;
        int skipped = 0;

        // Get current follow count
        long currentFollowCount = followedContainerRepository.countByUserAddress(userAddress);

        for (String containerId : containerIds) {

            // Stop when configured follow limit is reached.
            if (currentFollowCount + added >= maxFollowsPerUser) {
                skipped += (containerIds.size() - added - skipped);
                break;
            }

            // Check whether container exists.
            Optional<Container> containerOpt = containerRepository.findById(containerId);
            if (containerOpt.isEmpty()) {
                skipped++;
                continue;
            }

            Container container = containerOpt.get();

            // User cannot follow own container.
            if (container.getCreator() != null
                    && Objects.equals(container.getCreator().getCreatorAddr(), userAddress)) {
                skipped++;
                continue;
            }

            // Skip if already followed.
            boolean alreadyFollowed = followedContainerRepository
                    .findByUserAddressAndContainerId(userAddress, containerId)
                    .isPresent();
            if (alreadyFollowed) {
                skipped++;
                continue;
            }

            // Save new follow.
            followedContainerRepository.save(new FollowedContainer(userAddress, containerId));
            added++;
        }

        return Map.of(
                "status", "success",
                "added", added,
                "skipped", skipped,
                "maxAllowed", maxFollowsPerUser
        );
    }

    @DeleteMapping("/api/follow-container")
    @Transactional
    public void unfollowContainer(
            @RequestParam String userAddress,
            @RequestParam String containerId
    ) {

        followedContainerRepository
                .deleteByUserAddressAndContainerId(userAddress, containerId);
    }

    @DeleteMapping("/api/follow-containers")
    @Transactional
    public void unfollowAllContainers(
            @RequestParam String userAddress
    ) {
        followedContainerRepository.deleteByUserAddress(userAddress);
    }

    @GetMapping("/api/followed-containers")
    public Map<String, Object> getFollowedContainers(
            @RequestParam String userAddress,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {

        var pageable = org.springframework.data.domain.PageRequest.of(page, pageSize);

        var pageResult = followedContainerRepository.findByUserAddress(userAddress, pageable);

        List<String> containerIds = pageResult
                .getContent()
                .stream()
                .map(FollowedContainer::getContainerId)
                .toList();

        List<Container> containers = containerRepository.findAllById(containerIds);

        List<Map<String, String>> containerDTOs = containers.stream()
                .map(c -> Map.of("id", c.getId(), "name", c.getName()))
                .toList();

        return Map.of(
                "content", containerDTOs,
                "page", pageResult.getNumber(),
                "pageSize", pageResult.getSize(),
                "totalElements", pageResult.getTotalElements(),
                "totalPages", pageResult.getTotalPages()
        );
    }
}
