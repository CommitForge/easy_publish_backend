package com.easypublish.repositories;



import com.easypublish.entities.onchain.ContainerChildLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ContainerChildLinkRepository extends JpaRepository<ContainerChildLink, String> {

    @Query("""
        SELECT ccl
        FROM ContainerChildLink ccl
        WHERE ccl.containerParentId = :containerId
    """)
    List<ContainerChildLink> findByContainerParentId(@Param("containerId") String containerId);

    @Query("""
        SELECT ccl
        FROM ContainerChildLink ccl
        WHERE ccl.containerParentId IN :containerIds
    """)
    List<ContainerChildLink> findByContainerParentIdIn(
            @Param("containerIds") Collection<String> containerIds
    );

}
