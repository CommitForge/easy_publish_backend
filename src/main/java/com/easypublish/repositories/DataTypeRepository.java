package com.easypublish.repositories;

import com.easypublish.entities.onchain.DataType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DataTypeRepository extends JpaRepository<DataType, String> {

    // Find all DataType by container ID
    @Query("SELECT d FROM DataType d WHERE d.containerId = :containerId")
    List<DataType> findByContainer(@Param("containerId") String containerId);

    @Query("SELECT d FROM DataType d WHERE d.containerId = :containerId")
    List<DataType> findByContainer(@Param("containerId") String containerId, Pageable pageable);

    // Optional: find all DataType by creator address only
    @Query("SELECT d FROM DataType d WHERE d.creator.creatorAddr = :creatorAddr")
    List<DataType> findByCreatorAddr(@Param("creatorAddr") String creatorAddr);

    // Find single DataType by id and container
    @Query("""
        SELECT d
        FROM DataType d
        WHERE d.id = :dataTypeId
          AND d.containerId = :containerId
    """)
    Optional<DataType> findByIdAndContainer(
            @Param("dataTypeId") String dataTypeId,
            @Param("containerId") String containerId
    );

    // ✅ New method: Find DataTypes by container and publish target domain
    @Query("""
        SELECT DISTINCT d
        FROM DataType d
        JOIN PublishTarget pt ON pt.dataTypeId = d.id
        WHERE d.containerId = :containerId
          AND pt.domain = :domain
    """)
    List<DataType> findByContainerAndPublishTargetDomain(
            @Param("containerId") String containerId,
            @Param("domain") String domain
    );
}