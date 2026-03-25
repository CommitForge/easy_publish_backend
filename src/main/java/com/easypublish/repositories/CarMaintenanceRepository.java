package com.easypublish.repositories;

import com.easypublish.entities.cars.maintenances.CarMaintenance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CarMaintenanceRepository extends JpaRepository<CarMaintenance, Long> {
    List<CarMaintenance> findByDataItemId(String dataItemId);

    @Query("""
    SELECT cm
    FROM CarMaintenance cm
    JOIN DataItem di ON di.id = cm.dataItemId
    WHERE di.dataTypeId = :dataTypeId
    ORDER BY cm.date
""")
    List<CarMaintenance> findMaintenancesByDataTypeId(@Param("dataTypeId") String dataTypeId);
}
