package com.easypublish.erp.repositories;

import com.easypublish.erp.entities.ErpVerificationCandidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ErpVerificationCandidateRepository extends JpaRepository<ErpVerificationCandidate, String> {
    List<ErpVerificationCandidate> findByIntegrationIdOrderByUpdatedAtDesc(String integrationId);

    List<ErpVerificationCandidate> findByIntegrationIdAndStatusOrderByUpdatedAtDesc(
            String integrationId,
            String status
    );

    Optional<ErpVerificationCandidate> findByIntegrationIdAndDataItemId(
            String integrationId,
            String dataItemId
    );
}

