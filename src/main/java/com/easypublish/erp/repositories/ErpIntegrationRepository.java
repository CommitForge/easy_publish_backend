package com.easypublish.erp.repositories;

import com.easypublish.erp.entities.ErpIntegration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ErpIntegrationRepository extends JpaRepository<ErpIntegration, String> {
    List<ErpIntegration> findByOwnerAddressOrderByUpdatedAtDesc(String ownerAddress);

    Optional<ErpIntegration> findByApiKey(String apiKey);
}

