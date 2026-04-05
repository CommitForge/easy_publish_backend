package com.easypublish.erp.repositories;

import com.easypublish.erp.entities.ErpPublishJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ErpPublishJobRepository extends JpaRepository<ErpPublishJob, String> {
    List<ErpPublishJob> findByIntegrationIdOrderByUpdatedAtDesc(String integrationId);

    List<ErpPublishJob> findByIntegrationIdAndStatusOrderByUpdatedAtDesc(
            String integrationId,
            String status
    );
}

