package com.easypublish.repositories;

import com.easypublish.entities.onchain.Owner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface OwnerRepository extends JpaRepository<Owner, String> {

    @Query("""
        SELECT o
        FROM Owner o
        WHERE o.container.id = :containerId
    """)
    List<Owner> findByContainerId(@Param("containerId") String containerId);

    @Query("""
        SELECT o
        FROM Owner o
        WHERE o.container.id IN :containerIds
    """)
    List<Owner> findByContainerIdIn(@Param("containerIds") Collection<String> containerIds);
}
