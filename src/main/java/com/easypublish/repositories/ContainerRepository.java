package com.easypublish.repositories;

import com.easypublish.entities.onchain.Container;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContainerRepository extends JpaRepository<Container, String> {

    // Find containers by creator address (paginated)
    @Query("""
        SELECT co
        FROM Container co
        WHERE co.creator.creatorAddr = :creatorAddr
    """)
    List<Container> findByCreator_creatorAddr(String creatorAddr, Pageable pageable);

    @Query("""
        SELECT co
        FROM Container co
        WHERE co.creator.creatorAddr = :creatorAddr
    """)
    List<Container> findByCreator_creatorAddr(String creatorAddr);

    // Find containers accessible by a user (own + followed) (paginated)
    @Query("""
        SELECT DISTINCT co
        FROM Container co
        LEFT JOIN FollowedContainer fc 
            ON fc.containerId = co.id
        WHERE co.creator.creatorAddr = :creatorAddr
           OR fc.userAddress = :creatorAddr
    """)
    List<Container> findAccessibleContainers(String creatorAddr, Pageable pageable);

    @Query(
            value = """
                SELECT DISTINCT co
                FROM Container co
                LEFT JOIN FollowedContainer fc
                    ON fc.containerId = co.id
                WHERE co.creator.creatorAddr = :creatorAddr
                   OR fc.userAddress = :creatorAddr
            """,
            countQuery = """
                SELECT COUNT(DISTINCT co.id)
                FROM Container co
                LEFT JOIN FollowedContainer fc
                    ON fc.containerId = co.id
                WHERE co.creator.creatorAddr = :creatorAddr
                   OR fc.userAddress = :creatorAddr
            """
    )
    Page<Container> findAccessibleContainersPage(String creatorAddr, Pageable pageable);

    // Find containers that have at least one enabled publish target (for efficiency, only return containers)
    @Query("""
        SELECT DISTINCT co
        FROM Container co
        JOIN PublishTarget pt
            ON pt.containerId = co.id
        WHERE pt.enabled = true
          AND (:domain IS NULL OR pt.domain = :domain)
    """)
    List<Container> findByPublishTargetDomain(String domain, Pageable pageable);

    @Query(
            value = """
                SELECT DISTINCT co
                FROM Container co
                JOIN PublishTarget pt
                    ON pt.containerId = co.id
                WHERE pt.enabled = true
                  AND (:domain IS NULL OR pt.domain = :domain)
            """,
            countQuery = """
                SELECT COUNT(DISTINCT co.id)
                FROM Container co
                JOIN PublishTarget pt
                    ON pt.containerId = co.id
                WHERE pt.enabled = true
                  AND (:domain IS NULL OR pt.domain = :domain)
            """
    )
    Page<Container> findByPublishTargetDomainPage(String domain, Pageable pageable);

    // Non-paginated version for internal use
    @Query("""
        SELECT DISTINCT co
        FROM Container co
        JOIN PublishTarget pt
            ON pt.containerId = co.id
        WHERE pt.enabled = true
          AND (:domain IS NULL OR pt.domain = :domain)
    """)
    List<Container> findByPublishTargetDomain(String domain);
}
