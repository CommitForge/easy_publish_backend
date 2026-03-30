package com.easypublish.service.easypublishindex;

import com.easypublish.entities.cars.maintenances.CarMaintenance;
import com.easypublish.entities.offchain.FollowedContainer;
import com.easypublish.entities.offchain.OffchainDataItemRevision;
import com.easypublish.entities.offchain.OffchainFollowContainer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mutable collector for rows produced by all easy_publish index features.
 */
public class EasyPublishIndexAccumulator {

    private final List<OffchainFollowContainer> followActionRows = new ArrayList<>();
    private final List<OffchainDataItemRevision> revisionRows = new ArrayList<>();
    private final List<CarMaintenance> maintenanceRows = new ArrayList<>();
    private final Map<String, LinkedHashMap<String, Boolean>> activeFollowState = new LinkedHashMap<>();

    public void addFollowAction(OffchainFollowContainer row) {
        if (row == null) {
            return;
        }
        followActionRows.add(row);
        if (row.getActorAddress() == null || row.getTargetContainerId() == null) {
            return;
        }
        activeFollowState
                .computeIfAbsent(row.getActorAddress(), ignored -> new LinkedHashMap<>())
                .put(row.getTargetContainerId(), row.isEnabled());
    }

    public void addRevisionRow(OffchainDataItemRevision row) {
        if (row != null) {
            revisionRows.add(row);
        }
    }

    public void addMaintenanceRow(CarMaintenance row) {
        if (row != null) {
            maintenanceRows.add(row);
        }
    }

    public List<OffchainFollowContainer> followActionRows() {
        return followActionRows;
    }

    public List<OffchainDataItemRevision> revisionRows() {
        return revisionRows;
    }

    public List<CarMaintenance> maintenanceRows() {
        return maintenanceRows;
    }

    public List<FollowedContainer> buildActiveFollowedContainers() {
        List<FollowedContainer> rows = new ArrayList<>();
        for (Map.Entry<String, LinkedHashMap<String, Boolean>> actorEntry : activeFollowState.entrySet()) {
            String actorAddress = actorEntry.getKey();
            for (Map.Entry<String, Boolean> followEntry : actorEntry.getValue().entrySet()) {
                if (Boolean.TRUE.equals(followEntry.getValue())) {
                    rows.add(new FollowedContainer(actorAddress, followEntry.getKey()));
                }
            }
        }
        return rows;
    }
}
