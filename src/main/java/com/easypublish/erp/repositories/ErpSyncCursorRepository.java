package com.easypublish.erp.repositories;

import com.easypublish.erp.entities.ErpSyncCursor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ErpSyncCursorRepository extends JpaRepository<ErpSyncCursor, String> {
    Optional<ErpSyncCursor> findByIntegrationIdAndCursorType(String integrationId, String cursorType);
}

