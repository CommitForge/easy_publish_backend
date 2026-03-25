package com.easypublish.repositories;

import com.easypublish.entities.UserDataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserDataRepository extends JpaRepository<UserDataEntity, Long> {

    // Find a user by their on-chain address
    Optional<UserDataEntity> findByAddress(String address);

    // You can also add custom queries as needed, e.g.:
    // List<UserDataEntity> findByLastSeenAtGreaterThan(Long timestamp);
}
