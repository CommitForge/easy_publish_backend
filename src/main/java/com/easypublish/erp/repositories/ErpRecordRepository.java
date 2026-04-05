package com.easypublish.erp.repositories;

import com.easypublish.erp.entities.ErpRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ErpRecordRepository extends JpaRepository<ErpRecord, String> {
    List<ErpRecord> findByIntegrationIdOrderByUpdatedAtDesc(String integrationId);

    List<ErpRecord> findByIntegrationIdAndPublishStatusOrderByUpdatedAtDesc(
            String integrationId,
            String publishStatus
    );

    Optional<ErpRecord> findByIntegrationIdAndExternalRecordId(
            String integrationId,
            String externalRecordId
    );
}

