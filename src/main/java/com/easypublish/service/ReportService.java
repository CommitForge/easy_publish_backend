package com.easypublish.service;

import com.easypublish.entities.cars.maintenances.CarMaintenance;
import com.easypublish.entities.onchain.DataItem;
import com.easypublish.repositories.CarMaintenanceRepository;
import com.easypublish.repositories.DataItemRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Read service used by report endpoints.
 */
@Service
public class ReportService {

    private final CarMaintenanceRepository carMaintenanceRepository;
    private final DataItemRepository dataItemRepository;

    public ReportService(
            CarMaintenanceRepository carMaintenanceRepository,
            DataItemRepository dataItemRepository
    ) {
        this.carMaintenanceRepository = carMaintenanceRepository;
        this.dataItemRepository = dataItemRepository;
    }

    /**
     * Returns data items for a single data type.
     */
    public List<DataItem> getDataByType(String dataTypeId) {
        return dataItemRepository.findByDataTypeId(dataTypeId);
    }

    /**
     * Returns maintenance entries for a single data item.
     */
    public List<CarMaintenance> getCarMaintenances(String dataItemId) {
        return carMaintenanceRepository.findByDataItemId(dataItemId);
    }

    /**
     * Returns all maintenance entries for all data items of a data type.
     */
    public List<CarMaintenance> getCarMaintenancesByType(String dataTypeId) {
        return carMaintenanceRepository.findMaintenancesByDataTypeId(dataTypeId);
    }
}
