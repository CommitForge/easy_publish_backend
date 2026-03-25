package com.easypublish.repositories;

import com.easypublish.entities.sync.SyncProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SyncProgressRepository extends JpaRepository<SyncProgress, Long> {

    Optional<SyncProgress> findByChainObjectIdAndChainType(String chainObjectId, String chainType);
    Optional<SyncProgress> findTopByChainObjectIdOrderByLastSyncTsDesc(String chainObjectId);
}
