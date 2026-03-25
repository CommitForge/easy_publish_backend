package com.easypublish.repositories;


import com.easypublish.entities.onchain.DataItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

@Repository
public interface DataItemRepository extends JpaRepository<DataItem, String> {
    @Query("SELECT MAX(d.sequenceIndex) FROM DataItem d WHERE d.prevDataItemChainId = :chainId")
    Optional<BigInteger> findMaxSequenceIndexByChainId(@Param("chainId") String chainId);
    @Query("""
SELECT DISTINCT di
FROM DataItem di
LEFT JOIN PublishTarget pt
    ON pt.dataItemId = di.id
WHERE di.containerId = :containerId
  AND di.dataTypeId IN :dataTypeIds
  AND (:domain IS NULL OR pt.domain = :domain)
""")
    Page<DataItem> findByContainerIdAndDataTypeIdInAndOptionalDomain(
            @Param("containerId") String containerId,
            @Param("dataTypeIds") List<String> dataTypeIds,
            @Param("domain") String domain,   // pass null to get all domains
            Pageable pageable
    );
    /**
     * All data items by creator
     */
    @Query("""
        SELECT di
        FROM DataItem di
        WHERE di.creator.creatorAddr = :creatorAddr
    """)
    List<DataItem> findByCreatorAddr(
            @Param("creatorAddr") String creatorAddr
    );
    Page<DataItem> findByContainerIdAndDataTypeIdIn(
            String containerId,
            List<String> dataTypeIds,
            Pageable pageable
    );

    /**
     * All data items by creator + container
     */
    @Query("""
        SELECT di
        FROM DataItem di
        WHERE di.containerId = :containerId
    """)
    List<DataItem> findByContainer(
            @Param("containerId") String containerId
    );

    /**
     * All data items by creator + container + data type
     */
    @Query("""
        SELECT di
        FROM DataItem di
        WHERE di.containerId = :containerId
          AND di.dataTypeId = :dataTypeId
    """)
    List<DataItem> findByContainerAndDataType(
            @Param("containerId") String containerId,
            @Param("dataTypeId") String dataTypeId
    );
    /**
     * All data items by container + multiple data types
     */
    @Query("""
    SELECT di
    FROM DataItem di
    WHERE di.containerId = :containerId
      AND di.dataTypeId IN :dataTypeIds
""")
    List<DataItem> findByContainerAndDataTypeIds(
            @Param("containerId") String containerId,
            @Param("dataTypeIds") List<String> dataTypeIds
    );

    List<DataItem> findByDataTypeId(String dataTypeId);

}
