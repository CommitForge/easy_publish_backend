package com.easypublish.erp.repositories;

import com.easypublish.erp.entities.ErpBlob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ErpBlobRepository extends JpaRepository<ErpBlob, String> {
    List<ErpBlob> findByIntegrationIdOrderByUpdatedAtDesc(String integrationId);
}

