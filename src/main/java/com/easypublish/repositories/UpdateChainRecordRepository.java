package com.easypublish.repositories;

import com.easypublish.entities.onchain.UpdateChainRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UpdateChainRecordRepository extends JpaRepository<UpdateChainRecord, String> {

    // Find a user by their on-chain address

    // Find all update records for a given container

    // You can also add custom queries as needed, e.g.:
    // List<UserDataEntity> findByLastSeenAtGreaterThan(Long timestamp);
}
