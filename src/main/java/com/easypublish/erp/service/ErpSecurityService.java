package com.easypublish.erp.service;

import com.easypublish.erp.entities.ErpIntegration;
import com.easypublish.erp.repositories.ErpIntegrationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ErpSecurityService {

    private final ErpIntegrationRepository integrationRepository;
    private final boolean allowOwnerAddressAuth;

    public ErpSecurityService(
            ErpIntegrationRepository integrationRepository,
            @Value("${app.erp.api.allow-owner-address-auth:false}") boolean allowOwnerAddressAuth
    ) {
        this.integrationRepository = integrationRepository;
        this.allowOwnerAddressAuth = allowOwnerAddressAuth;
    }

    public ErpIntegration requireIntegration(String integrationId) {
        String normalizedId = normalize(integrationId);
        if (normalizedId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "integrationId is required");
        }

        return integrationRepository.findById(normalizedId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ERP integration not found"));
    }

    public ErpIntegration requireAuthorizedIntegration(
            String integrationId,
            String apiKey,
            String ownerAddress
    ) {
        ErpIntegration integration = requireIntegration(integrationId);
        String normalizedApiKey = normalize(apiKey);
        String normalizedOwnerAddress = normalize(ownerAddress);

        if (normalizedApiKey != null) {
            if (!normalizedApiKey.equals(integration.getApiKey())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid ERP api key");
            }
            return integration;
        }

        if (allowOwnerAddressAuth && normalizedOwnerAddress != null) {
            if (!normalizedOwnerAddress.equalsIgnoreCase(normalize(integration.getOwnerAddress()))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Integration owner mismatch");
            }
            return integration;
        }

        throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                allowOwnerAddressAuth
                        ? "Provide ownerAddress query param or X-ERP-API-KEY header"
                        : "Provide X-ERP-API-KEY header (ownerAddress auth is disabled)"
        );
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
