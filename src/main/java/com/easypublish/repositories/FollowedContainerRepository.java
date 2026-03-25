package com.easypublish.repositories;

import com.easypublish.entities.offchain.FollowedContainer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface FollowedContainerRepository
        extends JpaRepository<FollowedContainer, Long> {

    List<FollowedContainer> findByUserAddress(String userAddress);

    Optional<FollowedContainer> findByUserAddressAndContainerId(
            String userAddress,
            String containerId
    );

    void deleteByUserAddressAndContainerId(
            String userAddress,
            String containerId
    );
    long countByUserAddress(String userAddress);

    void deleteByUserAddress(String userAddress);

    Page<FollowedContainer> findByUserAddress(String userAddress, Pageable pageable);
}
