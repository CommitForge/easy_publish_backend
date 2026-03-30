package com.easypublish.service.easypublishindex;

import com.easypublish.entities.cars.maintenances.CarMaintenance;
import com.easypublish.entities.onchain.DataItem;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Component
public class CarsMaintenancesIndexFeature implements EasyPublishIndexFeature {

    @Override
    public String featureKey() {
        return "cars.maintenances";
    }

    @Override
    public void index(
            DataItem dataItem,
            Map<String, Object> easyPublishMap,
            EasyPublishIndexContext context,
            EasyPublishIndexAccumulator accumulator
    ) {
        Object carsRaw = easyPublishMap.get("cars");
        if (!(carsRaw instanceof Map<?, ?> carsMapRaw)) {
            return;
        }

        String dataItemId = EasyPublishIndexUtils.normalize(dataItem.getId());
        if (dataItemId == null) {
            return;
        }

        Map<String, Object> carsMap = EasyPublishIndexUtils.toStringObjectMap(carsMapRaw);
        Object maintenancesRaw = carsMap.get("maintenances");
        if (!(maintenancesRaw instanceof Collection<?> maintenanceCollection)) {
            return;
        }

        for (Object maintenanceEntry : maintenanceCollection) {
            if (!(maintenanceEntry instanceof Map<?, ?> maintenanceRawMap)) {
                continue;
            }
            Map<String, Object> maintenance = EasyPublishIndexUtils.toStringObjectMap(maintenanceRawMap);
            String date = EasyPublishIndexUtils.firstString(maintenance.get("date"));

            accumulator.addMaintenanceRow(
                    new CarMaintenance(
                            dataItemId,
                            date,
                            EasyPublishIndexUtils.parseDate(date),
                            EasyPublishIndexUtils.firstString(maintenance.get("distance")),
                            EasyPublishIndexUtils.firstString(maintenance.get("service")),
                            EasyPublishIndexUtils.firstString(maintenance.get("cost")),
                            EasyPublishIndexUtils.firstString(maintenance.get("parts")),
                            EasyPublishIndexUtils.firstString(maintenance.get("performed_by"), maintenance.get("performedBy")),
                            EasyPublishIndexUtils.firstString(maintenance.get("note"))
                    )
            );
        }
    }
}
