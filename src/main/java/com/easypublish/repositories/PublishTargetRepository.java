package com.easypublish.repositories;

import com.easypublish.entities.publish.PublishTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PublishTargetRepository extends JpaRepository<PublishTarget, Long> {

    Optional<PublishTarget> findByDomainAndDataItemId(String domain, String dataItemId);

    Optional<PublishTarget> findByDomainAndDataItemVerificationId(String domain, String dataItemVerificationId);

    // ✅ New method for container publish targets
    Optional<PublishTarget> findByDomainAndContainerId(String domain, String containerId);

    // ✅ New method for data type publish targets
    Optional<PublishTarget> findByDomainAndDataTypeId(String domain, String dataTypeId);
}
