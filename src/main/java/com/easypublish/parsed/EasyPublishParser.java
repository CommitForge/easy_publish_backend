package com.easypublish.parsed;

import com.easypublish.entities.publish.PublishTarget;
import com.easypublish.repositories.CarMaintenanceRepository;
import com.easypublish.repositories.PublishTargetRepository;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

@Component
public class EasyPublishParser {

    @Autowired
    private PublishTargetRepository publishTargetRepository;

    @Autowired
    private CarMaintenanceRepository carMaintenanceRepository;

    @Transactional
    public void parseAndSave(String json, String objectId, boolean isVerification, boolean isContainer, boolean isDataType) {

        if (json == null || json.isBlank()) {
            System.out.println("[INFO] Empty JSON for objectId " + objectId);
            return;
        }

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        EasyPublish easyPublish;

        try {
            easyPublish = mapper.readValue(json, EasyPublish.class);
        } catch (Exception e) {
            System.err.println("[WARN] JSON parsing failed for objectId " + objectId);
            System.err.println(e.getMessage());
            return;
        }

        PublishWrapper wrapper = easyPublish.getPublishWrapper();

        if (wrapper == null) {
            System.out.println("[INFO] No easy_publish section found for " + objectId);
            return;
        }

        /* -------------------------
           PUBLISH TARGETS
        ------------------------- */

        if (wrapper.getPublish() != null && wrapper.getPublish().getTargets() != null) {

            for (PublishTarget target : wrapper.getPublish().getTargets()) {

                try {

                    if (target.getDomain() == null || target.getDomain().isBlank())
                        continue;

                    Optional<PublishTarget> optionalExisting;

                    if (isContainer) {
                        optionalExisting = publishTargetRepository
                                .findByDomainAndContainerId(target.getDomain(), objectId);
                    }
                    else if (isDataType) {
                        optionalExisting = publishTargetRepository
                                .findByDomainAndDataTypeId(target.getDomain(), objectId);
                    }
                    else if (isVerification) {
                        optionalExisting = publishTargetRepository
                                .findByDomainAndDataItemVerificationId(target.getDomain(), objectId);
                    }
                    else {
                        optionalExisting = publishTargetRepository
                                .findByDomainAndDataItemId(target.getDomain(), objectId);
                    }

                    PublishTarget existing = optionalExisting.orElseGet(PublishTarget::new);

                    existing.setDomain(target.getDomain());
                    existing.setBaseUrl(target.getBaseUrl());
                    existing.setEnabled(target.isEnabled());
                    existing.setPaths(target.getPaths());

                    if (isContainer)
                        existing.setContainerId(objectId);
                    else if (isDataType)
                        existing.setDataTypeId(objectId);
                    else if (isVerification)
                        existing.setDataItemVerificationId(objectId);
                    else
                        existing.setDataItemId(objectId);

                    publishTargetRepository.save(existing);

                    System.out.println("[DB] Saved publish target → " + existing.getDomain());

                } catch (Exception e) {
                    System.err.println("[ERROR] Failed to save publish target: " + e.getMessage());
                }
            }
        } else {
            System.out.println("[INFO] No publish.targets section found");
        }

        /* -------------------------
           CAR MAINTENANCES
        ------------------------- */

        if (wrapper.getCars() != null && wrapper.getCars().getMaintenances() != null) {

            for (CarMaintenance m : wrapper.getCars().getMaintenances()) {

                try {
                    String date = m.getDate();
                    LocalDate parsed = date != null ? LocalDate.parse(date) : null;
                    carMaintenanceRepository.save(
                            new com.easypublish.entities.cars.maintenances.CarMaintenance(
                                    objectId,
                                    date,
                                    parsed,
                                    m.getDistance(),
                                    m.getService(),
                                    m.getCost(),
                                    m.getParts(),
                                    m.getPerformedBy(),
                                    m.getNote()
                            )
                    );

                    System.out.println("[DB] Saved maintenance entry for objectId=" + objectId);

                } catch (Exception e) {
                    System.err.println("[ERROR] Failed to save maintenance: " + e.getMessage());
                }
            }

        } else {
            System.out.println("[INFO] No cars.maintenances section found");
        }
    }
}