package com.easypublish.repositories;

import com.easypublish.entities.publish.PublishTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PublishTargetRepository extends JpaRepository<PublishTarget, Long> {

    Optional<PublishTarget> findByDomainAndDataItemId(String domain, String dataItemId);

    Optional<PublishTarget> findByDomainAndDataItemVerificationId(String domain, String dataItemVerificationId);

    Optional<PublishTarget> findByDomainAndContainerId(String domain, String containerId);

    Optional<PublishTarget> findByDomainAndDataTypeId(String domain, String dataTypeId);

    List<PublishTarget> findByDomainAndContainerIdIn(String domain, Collection<String> containerIds);

    List<PublishTarget> findByDomainAndDataTypeIdIn(String domain, Collection<String> dataTypeIds);

    List<PublishTarget> findByDomainAndDataItemIdIn(String domain, Collection<String> dataItemIds);

    List<PublishTarget> findByDomainAndDataItemVerificationIdIn(
            String domain,
            Collection<String> dataItemVerificationIds
    );
}
