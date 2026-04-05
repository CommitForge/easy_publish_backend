package com.easypublish.erp.service;

import com.easypublish.erp.dto.ErpIntegrationCreateRequest;
import com.easypublish.erp.dto.ErpIntegrationUpdateRequest;
import com.easypublish.erp.entities.ErpIntegration;
import com.easypublish.erp.repositories.ErpIntegrationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ErpIntegrationService {

    private final ErpIntegrationRepository integrationRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.erp.cli.binary:node}")
    private String defaultCliBinary;

    @Value("${app.erp.cli.script:}")
    private String defaultCliScript;

    @Value("${app.erp.cli.working-directory:.}")
    private String defaultCliWorkingDirectory;

    @Value("${app.erp.cli.default-network:mainnet}")
    private String defaultCliNetwork;

    @Value("${app.erp.cli.private-key-env-var:IOTA_PRIVATE_KEY}")
    private String defaultCliPrivateKeyEnvVar;

    public ErpIntegrationService(ErpIntegrationRepository integrationRepository) {
        this.integrationRepository = integrationRepository;
    }

    public Map<String, Object> createIntegration(ErpIntegrationCreateRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing request body");
        }

        String ownerAddress = ErpSecurityService.normalize(request.getOwnerAddress());
        String name = ErpSecurityService.normalize(request.getName());
        if (ownerAddress == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ownerAddress is required");
        }
        if (name == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }

        ErpIntegration integration = new ErpIntegration();
        integration.setOwnerAddress(ownerAddress);
        integration.setName(name);
        integration.setDescription(ErpSecurityService.normalize(request.getDescription()));
        integration.setStatus("ACTIVE");
        integration.setApiKey(generateApiKey());
        integration.setWebhookUrl(ErpSecurityService.normalize(request.getWebhookUrl()));
        integration.setDefaultContainerId(ErpSecurityService.normalize(request.getDefaultContainerId()));
        integration.setDefaultDataTypeId(ErpSecurityService.normalize(request.getDefaultDataTypeId()));
        integration.setCliBinary(resolveOrDefault(request.getCliBinary(), defaultCliBinary));
        integration.setCliScript(resolveOrDefault(request.getCliScript(), defaultCliScript));
        integration.setCliWorkingDirectory(resolveOrDefault(request.getCliWorkingDirectory(), defaultCliWorkingDirectory));
        integration.setCliNetwork(resolveOrDefault(request.getCliNetwork(), defaultCliNetwork));
        integration.setCliPrivateKeyEnvVar(resolveOrDefault(request.getCliPrivateKeyEnvVar(), defaultCliPrivateKeyEnvVar));
        integration.setCreatedAt(Instant.now());
        integration.setUpdatedAt(Instant.now());

        ErpIntegration saved = integrationRepository.save(integration);
        return toResponse(saved, true);
    }

    public List<Map<String, Object>> listIntegrations(String ownerAddress) {
        String normalizedOwner = ErpSecurityService.normalize(ownerAddress);
        if (normalizedOwner == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ownerAddress is required");
        }
        return integrationRepository.findByOwnerAddressOrderByUpdatedAtDesc(normalizedOwner).stream()
                .map(this::toListResponse)
                .toList();
    }

    public Map<String, Object> getIntegration(String integrationId, boolean includeSecret) {
        ErpIntegration integration = integrationRepository.findById(integrationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ERP integration not found"));
        return toResponse(integration, includeSecret);
    }

    public Map<String, Object> updateIntegration(String integrationId, ErpIntegrationUpdateRequest request) {
        ErpIntegration integration = integrationRepository.findById(integrationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ERP integration not found"));

        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing request body");
        }

        if (ErpSecurityService.normalize(request.getName()) != null) {
            integration.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            integration.setDescription(ErpSecurityService.normalize(request.getDescription()));
        }
        if (ErpSecurityService.normalize(request.getStatus()) != null) {
            String status = request.getStatus().trim().toUpperCase();
            if (!List.of("ACTIVE", "INACTIVE", "PAUSED").contains(status)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported status");
            }
            integration.setStatus(status);
        }
        if (request.getWebhookUrl() != null) {
            integration.setWebhookUrl(ErpSecurityService.normalize(request.getWebhookUrl()));
        }
        if (request.getDefaultContainerId() != null) {
            integration.setDefaultContainerId(ErpSecurityService.normalize(request.getDefaultContainerId()));
        }
        if (request.getDefaultDataTypeId() != null) {
            integration.setDefaultDataTypeId(ErpSecurityService.normalize(request.getDefaultDataTypeId()));
        }
        if (request.getCliBinary() != null) {
            integration.setCliBinary(ErpSecurityService.normalize(request.getCliBinary()));
        }
        if (request.getCliScript() != null) {
            integration.setCliScript(ErpSecurityService.normalize(request.getCliScript()));
        }
        if (request.getCliWorkingDirectory() != null) {
            integration.setCliWorkingDirectory(ErpSecurityService.normalize(request.getCliWorkingDirectory()));
        }
        if (request.getCliNetwork() != null) {
            integration.setCliNetwork(ErpSecurityService.normalize(request.getCliNetwork()));
        }
        if (request.getCliPrivateKeyEnvVar() != null) {
            integration.setCliPrivateKeyEnvVar(ErpSecurityService.normalize(request.getCliPrivateKeyEnvVar()));
        }

        ErpIntegration saved = integrationRepository.save(integration);
        return toResponse(saved, false);
    }

    public Map<String, Object> rotateApiKey(String integrationId) {
        ErpIntegration integration = integrationRepository.findById(integrationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ERP integration not found"));
        integration.setApiKey(generateApiKey());
        ErpIntegration saved = integrationRepository.save(integration);
        return toResponse(saved, true);
    }

    private String resolveOrDefault(String value, String defaultValue) {
        String normalized = ErpSecurityService.normalize(value);
        return normalized == null ? ErpSecurityService.normalize(defaultValue) : normalized;
    }

    private String generateApiKey() {
        byte[] raw = new byte[32];
        secureRandom.nextBytes(raw);
        return "erp_" + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private Map<String, Object> toResponse(ErpIntegration integration, boolean includeSecret) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", integration.getId());
        map.put("ownerAddress", integration.getOwnerAddress());
        map.put("name", integration.getName());
        map.put("description", integration.getDescription());
        map.put("status", integration.getStatus());
        map.put("webhookUrl", integration.getWebhookUrl());
        map.put("defaultContainerId", integration.getDefaultContainerId());
        map.put("defaultDataTypeId", integration.getDefaultDataTypeId());
        map.put("cliBinary", integration.getCliBinary());
        map.put("cliScript", integration.getCliScript());
        map.put("cliWorkingDirectory", integration.getCliWorkingDirectory());
        map.put("cliNetwork", integration.getCliNetwork());
        map.put("cliPrivateKeyEnvVar", integration.getCliPrivateKeyEnvVar());
        map.put("createdAt", integration.getCreatedAt());
        map.put("updatedAt", integration.getUpdatedAt());
        if (includeSecret) {
            map.put("apiKey", integration.getApiKey());
        } else {
            String apiKey = integration.getApiKey();
            map.put(
                    "apiKeyHint",
                    apiKey == null || apiKey.length() < 8
                            ? null
                            : apiKey.substring(0, 4) + "..." + apiKey.substring(apiKey.length() - 4)
            );
        }
        return map;
    }

    private Map<String, Object> toListResponse(ErpIntegration integration) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", integration.getId());
        map.put("name", integration.getName());
        map.put("description", integration.getDescription());
        map.put("status", integration.getStatus());
        map.put("createdAt", integration.getCreatedAt());
        map.put("updatedAt", integration.getUpdatedAt());
        return map;
    }
}
