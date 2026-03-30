package com.easypublish.repositories;

import com.easypublish.entities.offchain.OffchainDataItemRevision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface OffchainDataItemRevisionRepository extends JpaRepository<OffchainDataItemRevision, Long> {

    List<OffchainDataItemRevision> findByDataItemIdOrderByIdAsc(String dataItemId);

    List<OffchainDataItemRevision> findByDataItemIdIn(Collection<String> dataItemIds);
}
