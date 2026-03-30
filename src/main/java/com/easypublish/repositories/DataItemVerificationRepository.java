package com.easypublish.repositories;

import com.easypublish.entities.onchain.DataItemVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DataItemVerificationRepository extends JpaRepository<DataItemVerification, String> {
    List<DataItemVerification> findByDataItemIdIn(List<String> dataItemIds);
}
