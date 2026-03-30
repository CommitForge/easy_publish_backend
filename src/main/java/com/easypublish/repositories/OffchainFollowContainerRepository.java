package com.easypublish.repositories;

import com.easypublish.entities.offchain.OffchainFollowContainer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OffchainFollowContainerRepository extends JpaRepository<OffchainFollowContainer, Long> {

    List<OffchainFollowContainer> findByActorAddressOrderBySequenceIndexDescIdDesc(String actorAddress);
}
