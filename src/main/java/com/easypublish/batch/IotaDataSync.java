package com.easypublish.batch;

import com.easypublish.entities.sync.SyncProgress;
import com.easypublish.entities.onchain.*;
import com.easypublish.parsed.EasyPublishParser;
import com.easypublish.repositories.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class IotaDataSync {

    private BlockEmitterIndexer indexer;
    @Autowired
    public EntityManager em;
    private final ObjectMapper mapper = new ObjectMapper();
    @Autowired
    public SyncProgressRepository syncProgressRepository;
    @Autowired
    public DataItemRepository dataItemRepository;
    @Autowired
    public ContainerChainRepository onchainContainerChainRepository;

    @Autowired
    public UpdateChainRepository onchainUpdateChainRepository;

    @Autowired
    public DataItemChainRepository onchainDataItemChainRepository;

    @Autowired
    public DataItemVerificationChainRepository onchainDataItemVerificationChainRepository;
    @Autowired
    private DataTypeRepository dataTypeRepository;
    @Autowired
    private EasyPublishParser easyPublishParser;
    @Autowired
    private ContainerRepository containerRepository;
    @PostConstruct
    public void init() {
        this.indexer = new BlockEmitterIndexer(em, easyPublishParser);
    }

    // ----------------------------
    // Fetch data via Node.js script (safe JSON parsing)
    // ----------------------------
    private JsonNode fetchContainerData(String containerId, String type) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "node",
                "node/getContainerItems.js",
                containerId,
                type
        );
        Process process = pb.start();

        // read stdout and stderr separately
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        try (BufferedReader out = new BufferedReader(new InputStreamReader(process.getInputStream()));
             BufferedReader err = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

            String line;
            while ((line = out.readLine()) != null) stdout.append(line);
            while ((line = err.readLine()) != null) stderr.append(line);
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Node process failed, exit code: " + exitCode + "\nError: " + stderr);
        }

        // strip non-JSON characters from stdout (keep only first { … } or [ … ])
        String output = stdout.toString().trim();
        int firstBrace = output.indexOf("{");
        int firstBracket = output.indexOf("[");
        int start = -1;
        if (firstBrace >= 0 && (firstBracket < 0 || firstBrace < firstBracket)) start = firstBrace;
        else if (firstBracket >= 0) start = firstBracket;

        if (start >= 0) {
            output = output.substring(start);
        } else {
            throw new RuntimeException("No JSON found in Node output:\n" + output);
        }

        return mapper.readTree(output);
    }
    private JsonNode fetchObjectData(String objectId, String type) throws Exception {
        if (objectId == null || objectId.isBlank()) {
            throw new IllegalArgumentException("objectId must not be null");
        }

        String typeArg = (type == null || type.isBlank()) ? "" : type;

        Path scriptPath = Paths.get("node/getObjectById.js").toAbsolutePath();
        if (!Files.exists(scriptPath)) {
            throw new RuntimeException("Node script not found: " + scriptPath);
        }

        ProcessBuilder pb = new ProcessBuilder(
                "node",
                scriptPath.toString(),
                objectId,
                typeArg
        );

        pb.redirectErrorStream(true);
        Process process = pb.start();

        String output;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            output = reader.lines().collect(Collectors.joining());
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Node process failed, exit code: " + exitCode + "\nOutput: " + output);
        }

        ObjectMapper mapper = new ObjectMapper();
        return mapper.readTree(output);
    }

    @Transactional
    public void syncObjectById(String objectId, String type) throws Exception {
        if (objectId == null || objectId.isBlank()) {
            throw new IllegalArgumentException("objectId must not be null or blank");
        }

        // -----------------------------
        // Fetch the object from Node.js script
        // -----------------------------
        JsonNode objectData = fetchObjectData(objectId, type);
        if (objectData == null || objectData.isEmpty()) {
            System.out.println("[WARN] Object not found: " + objectId);
            return;
        }

        // -----------------------------
        // Flatten fields
        // -----------------------------
        JsonNode fields = objectData.path("fields");

        switch (type) {

            case "container":
                updateOrCreateContainer(objectData.toString());
                System.out.println("[SYNC] container synced: " + objectId);
                break;

            case "data_type": {
                // container_id fallback
                String containerId = getIdOrNull(fields, "container_id");
                if (containerId == null || containerId.isBlank()) {
                    System.out.println("[WARN] DataType missing container_id: " + objectId);
                    return;
                }
                updateOrCreateDataType(objectData.toString(), containerId);
                System.out.println("[SYNC] data_type synced → container " + containerId);
                break;
            }

            case "data_item": {
                String containerId = getIdOrNull(fields, "container_id");
                String dataTypeId = getIdOrNull(fields, "data_type_id");
                if (containerId == null || containerId.isBlank()) {
                    System.out.println("[WARN] DataItem missing container_id: " + objectId);
                    return;
                }
                updateOrCreateDataItem(objectData, containerId, dataTypeId);
                System.out.println("[SYNC] data_item synced → container " + containerId);
                break;
            }

            case "owner": {
                String containerId = getIdOrNull(fields, "container_id");
                if (containerId == null || containerId.isBlank()) {
                    System.out.println("[WARN] Owner missing container_id: " + objectId);
                    return;
                }
                updateOrCreateOwner(objectData.toString(), containerId);
                System.out.println("[SYNC] owner synced → container " + containerId);
                break;
            }

            case "owner_audit": {
                String containerId = getIdOrNull(fields, "container_id");
                if (containerId == null || containerId.isBlank()) containerId = objectId; // fallback
                updateOrCreateOwnerAudit(objectData.toString(), containerId);
                System.out.println("[SYNC] owner_audit synced → container " + containerId);
                break;
            }

            case "data_item_verification": {
                String containerId = getIdOrNull(fields, "container_id");
                String dataItemId = getIdOrNull(fields, "data_item_id");
                if (containerId == null || containerId.isBlank()) containerId = objectId; // fallback
                DataItemVerification dataItemVerification = updateOrCreateDataItemVerification(objectData.toString(), containerId, dataItemId);
                System.out.println("[SYNC] data_item_verification synced → container " + containerId);
                // also update data item that was verified
                JsonNode objectData2 = fetchObjectData(dataItemVerification.getDataItemId(), "data_item");
                JsonNode fields2 = objectData2.path("fields");
                String containerId2 = getIdOrNull(fields2, "container_id");
                String dataTypeId2 = getIdOrNull(fields2, "data_type_id");
                updateOrCreateDataItem(objectData2, containerId2, dataTypeId2);
                break;
            }

            case "child", "container_child_link": {
                String parentId = getIdOrNull(fields, "container_parent_id");
                if (parentId == null || parentId.isBlank()) {
                    System.out.println("[WARN] Child missing container_parent_id: " + objectId);
                    return;
                }
                updateOrCreateChildLink(objectData.toString(), parentId);
                System.out.println("[SYNC] child synced → parent " + parentId);
                break;
            }

            default:
                throw new IllegalArgumentException("Unsupported type: " + type);
        }
    }

    // ----------------------------
    // Update or create helpers
    // ----------------------------
    @Transactional
    public Container updateOrCreateContainer(String json) throws Exception {
        JsonNode root = mapper.readTree(json);
        String id = extractObjectId(root);
        Container existing = id != null && !id.isEmpty() ? em.find(Container.class, id) : null;
        return existing != null ? indexer.updateContainerJson(json) : indexer.processContainerJson(json);
    }

    @Transactional
    public Owner updateOrCreateOwner(String json, String containerId) throws Exception {
        JsonNode root = mapper.readTree(json);
        String id = extractObjectId(root);
        Owner existing = id != null && !id.isEmpty() ? em.find(Owner.class, id) : null;
        return existing != null ? indexer.updateOwnerJson(json, containerId) : indexer.processOwnerJson(json, containerId);
    }

    @Transactional
    public ContainerChildLink updateOrCreateChildLink(String json, String containerId) throws Exception {
        JsonNode root = mapper.readTree(json);
        String id = extractObjectId(root);
        ContainerChildLink existing = id != null && !id.isEmpty() ? em.find(ContainerChildLink.class, id) : null;
        return existing != null ? indexer.updateContainerChildLinkJson(json, containerId) : indexer.processContainerChildLinkJson(json, containerId);
    }

    @Transactional
    public DataType updateOrCreateDataType(String json, String containerId) throws Exception {
        JsonNode root = mapper.readTree(json);
        String id = extractObjectId(root);
        if (id == null || id.isEmpty()) return null;

        // fallback containerId
        if (containerId == null || containerId.isEmpty()) {
            containerId = getIdOrNull(root.path("fields"), "container_id");
        }

        DataType existing = em.find(DataType.class, id);
        return existing != null ? indexer.updateDataTypeJson(json, containerId) : indexer.processDataTypeJson(json, containerId);
    }
    @Transactional
    public DataItem updateOrCreateDataItem(JsonNode objectData,
                                           String containerId,
                                           String dataTypeId) throws Exception {

        String objectId = extractObjectId(objectData);
        JsonNode fields = objectData.path("fields");

        if (objectId == null || objectId.isBlank()) {
            throw new IllegalStateException("DataItem missing object_id");
        }

        if (containerId == null || containerId.isBlank()) {
            containerId = getIdOrNull(fields, "container_id");
        }
        if (dataTypeId == null || dataTypeId.isBlank()) {
            dataTypeId = getIdOrNull(fields, "data_type_id");
        }

        // Normalize string "null"
        containerId = normalizeText(containerId);
        dataTypeId = normalizeText(dataTypeId);

        // 🔴 HARD REQUIREMENT — container must exist
        if (containerId == null || containerId.isBlank()) {
            throw new IllegalStateException(
                    "DataItem " + objectId + " has no container_id"
            );
        }

        // Ensure container exists
        if (!containerRepository.existsById(containerId)) {
            syncObjectById(containerId, "container");
            if (!containerRepository.existsById(containerId)) {
                throw new IllegalStateException(
                        "Container " + containerId + " could not be synced"
                );
            }
        }

        // Ensure dataType exists (optional but validated)
        if (dataTypeId != null && !dataTypeId.isBlank()) {
            if (!dataTypeRepository.existsById(dataTypeId)) {
                syncObjectById(dataTypeId, "data_type");
            }
        }

        DataItem entity = dataItemRepository.findById(objectId)
                .orElseGet(DataItem::new);

        entity.setId(objectId);
        entity.setContainerId(containerId);
        entity.setDataTypeId(dataTypeId);
        entity.setCreator(parseCreator(fields));

        entity.setName(getTextOrNull(fields, "name"));
        entity.setDescription(getTextOrNull(fields, "description"));
        entity.setContent(getTextOrNull(fields, "content"));
        entity.setExternalId(getTextOrNull(fields, "external_id"));
        entity.setExternalIndex(getBigIntOrZero(fields, "external_index"));
        entity.setSequenceIndex(getBigIntOrZero(fields, "sequence_index"));
        entity.setPrevId(getIdOrNull(fields, "prev_id"));
        entity.setPrevDataItemChainId(getIdOrNull(fields, "prev_data_item_chain_id"));
        entity.setPrevDataTypeItemId(getIdOrNull(fields, "prev_data_type_item_id"));

        JsonNode verifiedNode = fields.get("verified");

        if (verifiedNode == null || verifiedNode.isNull()) {
            entity.setVerified(null);
        } else {
            entity.setVerified(verifiedNode.asBoolean());
        }

        // ---------- Collections (clean + refill) ----------
        entity.setRecipients(readStringArray(fields, "recipients"));
        entity.setReferences(readStringArray(fields, "references"));
        entity.setVerificationSuccessAddresses(
                readStringArray(fields, "verification_success_addresses")
        );
        entity.setVerificationFailureAddresses(
                readStringArray(fields, "verification_failure_addresses")
        );
        entity.setVerificationSuccessDataItem(
                readStringArray(fields, "verification_success_data_item")
        );
        entity.setVerificationFailureDataItem(
                readStringArray(fields, "verification_failure_data_item")
        );

        DataItem saved = dataItemRepository.save(entity);

        System.out.println("[DB] Saved DataItem: " + saved.getId());

        // ---------- Parse content JSON for publish targets ----------
        if (entity.getContent() != null && !entity.getContent().isBlank()) {
            try {
                // Use objectId so we can associate parsed targets with this DataItem
                easyPublishParser.parseAndSave(entity.getContent(), saved.getId(), false, false, false);
                System.out.println("[SYNC] EasyPublish targets stored for DataItem " + saved.getId());
            } catch (Exception e) {
                System.err.println("[WARN] Failed to parse EasyPublish content for DataItem " + saved.getId());
                e.printStackTrace();
            }
        }

        return saved;
    }

    private String getTextOrIdOrNull(JsonNode valueNode) {
        if (valueNode == null || valueNode.isMissingNode() || valueNode.isNull()) {
            return null;
        }
        if (valueNode.isTextual() || valueNode.isNumber() || valueNode.isBoolean()) {
            return normalizeText(valueNode.asText(null));
        }

        JsonNode idNode = valueNode.path("id");
        if (!idNode.isMissingNode() && !idNode.isNull()) {
            return normalizeText(idNode.asText(null));
        }

        JsonNode nestedIdNode = valueNode.path("fields").path("id");
        if (!nestedIdNode.isMissingNode() && !nestedIdNode.isNull()) {
            return normalizeText(nestedIdNode.asText(null));
        }

        return normalizeText(valueNode.asText(null));
    }

    private String getIdOrNull(JsonNode fields, String fieldName) {
        return getTextOrIdOrNull(fields.path(fieldName));
    }

    private String extractObjectId(JsonNode root) {
        String id = getTextOrIdOrNull(root.path("object_id"));
        if (id != null) return id;

        id = getTextOrIdOrNull(root.path("fields").path("id"));
        if (id != null) return id;

        id = getTextOrIdOrNull(root.path("fields").path("fields").path("id"));
        if (id != null) return id;

        id = getTextOrIdOrNull(root.path("id"));
        if (id != null) return id;

        return null;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSyncSuccess(SyncProgress progress) {
        progress.setLastSyncError(false);
        progress.setLastSyncTs(Instant.now());
        syncProgressRepository.save(progress);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSyncFailure(SyncProgress progress) {
        progress.setLastSyncError(true);
        progress.setLastSyncTs(Instant.now());
        syncProgressRepository.save(progress);
    }

    // ----------------------------
    // Recursive UpdateChain sync
    // ----------------------------
    @Transactional
    public void syncUpdateChainRecords(
            String containerChainId,
            String updateChainId,
            String dataItemChainId,
            String dataItemVerificationChainId
    ) throws Exception {

        SyncProgress progress = syncProgressRepository
                .findByChainObjectIdAndChainType(updateChainId, "update_chain")
                .orElseGet(() -> {
                    SyncProgress p = new SyncProgress();
                    p.setChainObjectId(updateChainId);
                    p.setChainType("update_chain");
                    p.setLastSequenceIndex(BigInteger.ZERO);
                    p.setLastSyncedObjectId(null);
                    p.setLastSyncTs(Instant.EPOCH);
                    p.setLastSyncError(false);
                    return syncProgressRepository.save(p);
                });

        try {

            BigInteger lastSeq = progress.getLastSequenceIndex() != null
                    ? progress.getLastSequenceIndex()
                    : BigInteger.ZERO;
            String lastId = progress.getLastSyncedObjectId();

            // --------------------------------------------------
            // Ensure chains exist
            // --------------------------------------------------
            validateAndPersistChain(containerChainId, "ContainerChain");
            validateAndPersistChain(updateChainId, "UpdateChain");
            validateAndPersistChain(dataItemChainId, "DataItemChain");
            validateAndPersistChain(dataItemVerificationChainId, "DataItemVerificationChain");

            // --------------------------------------------------
            // Sync DataItemChain
            // --------------------------------------------------
            DataItemChain chain = onchainDataItemChainRepository
                    .findById(dataItemChainId)
                    .orElseThrow();

            String currentDataItemId = chain.getLastDataItemId();

            while (currentDataItemId != null && !currentDataItemId.isBlank() && !"null".equals(currentDataItemId)) {

                if (dataItemRepository.existsById(currentDataItemId)) break;

                JsonNode itemJson = fetchObjectData(currentDataItemId, "data_item");
                if (itemJson == null || itemJson.isEmpty()) break;

                syncObjectById(currentDataItemId, "data_item");

                currentDataItemId = getIdOrNull(itemJson.path("fields"), "prev_data_item_chain_id");
            }

            // --------------------------------------------------
            // Walk UpdateChain
            // --------------------------------------------------
            JsonNode chainJson = fetchObjectData(updateChainId, "update_chain");
            if (chainJson == null || chainJson.isEmpty()) {
                markSyncSuccess(progress);
                return;
            }

            String currentUpdateId = getIdOrNull(chainJson.path("fields"), "last_update_record_id");

            if (currentUpdateId == null || currentUpdateId.isBlank()) {
                markSyncSuccess(progress);
                return;
            }

            List<JsonNode> recordsToProcess = new ArrayList<>();

            while (currentUpdateId != null && !currentUpdateId.isBlank()) {

                JsonNode recordJson = fetchObjectData(currentUpdateId, "update_record_item");
                if (recordJson == null || recordJson.isEmpty()) break;

                BigInteger seq = getBigIntOrZero(recordJson.path("fields"), "sequence_index");
                if (seq.compareTo(lastSeq) <= 0) break;

                recordsToProcess.add(recordJson);

                currentUpdateId = getIdOrNull(recordJson.path("fields"), "prev_id");
            }

            Collections.reverse(recordsToProcess);

            for (JsonNode recordNode : recordsToProcess) {

                UpdateChainRecord r = indexer.processUpdateRecordJson(
                        recordNode.toString(),
                        lastId,
                        lastSeq
                );

                if (r.getSequenceIndex() != null) {
                    lastSeq = r.getSequenceIndex();
                }
                lastId = r.getId();

                progress.setLastSequenceIndex(lastSeq);
                progress.setLastSyncedObjectId(lastId);
                progress.setLastSyncTs(Instant.now());
                syncProgressRepository.save(progress);

                String objectType = normalizeText(r.getObjectType());
                if (objectType == null) {
                    System.out.println("[WARN] Missing object type on update record " + r.getId());
                    continue;
                }

                switch (objectType) {

                    case "container" ->
                            syncObjectById(r.getObjectId(), "container");

                    case "data_type" ->
                            syncObjectById(r.getObjectId(), "data_type");

                    case "data_item" ->
                            syncObjectById(r.getObjectId(), "data_item");

                    case "owner_audit" ->
                            syncObjectById(r.getObjectId(), "owner_audit");

                    case "owner" ->
                            syncObjectById(r.getObjectId(), "owner");

                    case "data_item_verification" ->
                            syncObjectById(r.getObjectId(), "data_item_verification");

                    case "child", "container_child_link" ->
                            syncObjectById(r.getObjectId(), "child");

                    default ->
                            System.out.println("[WARN] Unknown object type: " + r.getObjectType());
                }
            }

            // ✅ If we reach here → everything succeeded
            markSyncSuccess(progress);

        } catch (Exception ex) {

            // ❌ Any failure anywhere
            markSyncFailure(progress);

            throw ex;
        }
    }
    /**
     * Ensure all 4 chains exist with the provided IDs.
     * Non-null fields initialized to safe defaults to prevent DB constraint issues.
     */
    private void validateAndPersistChain(String objectId, String type) throws Exception {

        JsonNode objectData = fetchObjectData(objectId, type);

        if (objectData == null || objectData.isEmpty()) {
            throw new RuntimeException("On-chain object not found: " + objectId);
        }

        JsonNode fields = objectData.path("fields");

        switch (type) {

            case "ContainerChain":
                ContainerChain container = onchainContainerChainRepository
                        .findById(objectId)
                        .orElse(new ContainerChain());

                container.setId(objectId);
                container.setLastContainerIndex(getBigIntOrZero(fields, "last_container_index"));
                container.setLastContainerId(getIdOrNull(fields, "last_container_id"));
                // container.setOtherField(fields.path("some_other_field").asText());

                onchainContainerChainRepository.save(container);
                break;

            case "UpdateChain":
                UpdateChain update = onchainUpdateChainRepository
                        .findById(objectId)
                        .orElse(new UpdateChain());

                update.setId(objectId);
                update.setLastContainerIndex(getBigIntOrZero(fields, "last_update_record_index"));
                update.setLastContainerId(getIdOrNull(fields, "last_update_record_id"));
                onchainUpdateChainRepository.save(update);
                break;

            case "DataItemChain":
                DataItemChain dataItem = onchainDataItemChainRepository
                        .findById(objectId)
                        .orElse(new DataItemChain());

                dataItem.setId(objectId);
                dataItem.setLastDataItemIndex(getBigIntOrZero(fields, "last_data_item_index"));
                dataItem.setLastDataItemId(getIdOrNull(fields, "last_data_item_id"));
                onchainDataItemChainRepository.save(dataItem);
                break;

            case "DataItemVerificationChain":
                DataItemVerificationChain verification = onchainDataItemVerificationChainRepository
                        .findById(objectId)
                        .orElse(new DataItemVerificationChain());

                verification.setId(objectId);
                BigInteger verificationIndex = getBigIntOrNull(fields, "last_data_item_verefication_index");
                if (verificationIndex == null) {
                    verificationIndex = getBigIntOrNull(fields, "last_data_item_verification_index");
                }
                verification.setLastDataItemVerificationIndex(verificationIndex);

                String verificationId = getIdOrNull(fields, "last_data_item_verefication_id");
                if (verificationId == null) {
                    verificationId = getIdOrNull(fields, "last_data_item_verification_id");
                }
                verification.setLastDataItemVerificationId(verificationId);
                onchainDataItemVerificationChainRepository.save(verification);
                break;
        }
    }

    @Transactional
    public OwnerAudit updateOrCreateOwnerAudit(String json, String containerId) throws Exception {
        JsonNode root = mapper.readTree(json);
        JsonNode f = root.path("fields"); // fields node

        // Extract ID safely
        String id = extractObjectId(root);
        if (id == null) throw new IllegalArgumentException("Missing ID in JSON");

        // Use containerId from parameter or fallback to fields
        if (containerId == null || containerId.isEmpty()) {
            containerId = getIdOrNull(f, "container_id");
        }

        OwnerAudit audit = em.find(OwnerAudit.class, id);
        boolean isNew = audit == null;
        if (isNew) audit = new OwnerAudit();

        audit.setId(id);

        // object_id from fields (not root)
        audit.setObjectId(getIdOrNull(f, "object_id"));
        audit.setContainerId(containerId);

        // creator
        JsonNode creatorNode = f.path("creator");
        if (!creatorNode.isMissingNode() && !creatorNode.isNull()) {
            audit.setCreator(parseCreator(f));
        } else {
            audit.setCreator(null);
        }

        audit.setAddr(getTextOrNull(f, "addr"));
        audit.setRole(getTextOrNull(f, "role"));
        audit.setRemoved(f.path("removed").asBoolean(false));

        if (isNew) em.persist(audit);
        else em.merge(audit);

        return audit;
    }
    @Transactional
    public DataItemVerification updateOrCreateDataItemVerification(
            String json,
            String containerId,
            String dataItemId
    ) throws Exception {

        JsonNode root = mapper.readTree(json);
        JsonNode f = root.path("fields");

        // 🔥 Extract ID safely
        String id = extractObjectId(root);
        if (id == null) throw new IllegalStateException("DataItemVerification missing id");

        // Fallback to input parameters if not present in JSON
        if (containerId == null) containerId = getIdOrNull(f, "container_id");
        if (dataItemId == null) dataItemId = getIdOrNull(f, "data_item_id");
        containerId = normalizeText(containerId);
        dataItemId = normalizeText(dataItemId);
        if (containerId == null || dataItemId == null) {
            throw new IllegalStateException(
                    "DataItemVerification " + id + " missing container_id or data_item_id"
            );
        }

        DataItemVerification div = em.find(DataItemVerification.class, id);
        boolean isNew = div == null;
        if (isNew) div = new DataItemVerification();

        // ====== Set parent fields ======
        div.setId(id);
        div.setContainerId(containerId);
        div.setDataItemId(dataItemId);
        div.setExternalId(getTextOrNull(f, "external_id"));
        div.setCreator(parseCreator(f));
        div.setName(getTextOrNull(f, "name"));
        div.setDescription(getTextOrNull(f, "description"));
        div.setContent(getTextOrNull(f, "content"));
        JsonNode verifiedNode = f.get("verified");
        div.setVerified((verifiedNode == null || verifiedNode.isNull()) ? null : verifiedNode.asBoolean());
        div.setPrevDataItemVerificationChainId(getIdOrNull(f, "prev_data_item_verification_chain_id"));
        div.setPrevId(getIdOrNull(f, "prev_id"));

        div.setSequenceIndex(getBigIntOrZero(f, "sequence_index"));
        div.setExternalIndex(getBigIntOrZero(f, "external_index"));

        // ====== Persist parent first to generate ID ======
        if (isNew) em.persist(div);
        else div = em.merge(div);

        em.flush(); // ID must exist for @ElementCollection inserts

        // ====== Use a final reference for lambdas ======
        final DataItemVerification finalDiv = div;

        // Clear existing lists
        if (finalDiv.getRecipients() == null) finalDiv.setRecipients(new ArrayList<>());
        if (finalDiv.getReferences() == null) finalDiv.setReferences(new ArrayList<>());
        if (finalDiv.getVerificationSuccess() == null) finalDiv.setVerificationSuccess(new ArrayList<>());
        if (finalDiv.getVerificationFailure() == null) finalDiv.setVerificationFailure(new ArrayList<>());
        finalDiv.getRecipients().clear();
        finalDiv.getReferences().clear();
        finalDiv.getVerificationSuccess().clear();
        finalDiv.getVerificationFailure().clear();

        // Add elements safely
        readStringArrayOptional(f, "recipients").forEach(addr -> {
            if (addr != null) finalDiv.getRecipients().add(addr);
        });

        readStringArray(f, "references").forEach(ref -> {
            if (ref != null) finalDiv.getReferences().add(ref);
        });

        readStringArrayOptional(f, "verification_success").forEach(addr -> {
            if (addr != null) finalDiv.getVerificationSuccess().add(addr);
        });

        readStringArrayOptional(f, "verification_failure").forEach(addr -> {
            if (addr != null) finalDiv.getVerificationFailure().add(addr);
        });

        // Final merge ensures collections are written safely
        DataItemVerification dataItemVerification = em.merge(finalDiv);

        // ---------- Parse content JSON for publish targets ----------
        if (div.getContent() != null && !div.getContent().isBlank()) {
            try {
                // Use objectId so we can associate parsed targets with this DataItem
                easyPublishParser.parseAndSave(div.getContent(), div.getId(), true, false, false);
                System.out.println("[SYNC] EasyPublish targets stored for DataItemVerification " + div.getId());
            } catch (Exception e) {
                System.err.println("[WARN] Failed to parse EasyPublish content for DataItemVerification " + div.getId());
                e.printStackTrace();
            }
        }

        return dataItemVerification;
    }
    private JsonNode fields(JsonNode root) {
        return root.path("fields");
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return (trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed)) ? null : trimmed;
    }

    private BigInteger getBigIntOrNull(JsonNode node, String field) {
        JsonNode f = node.path(field);
        if (f.isMissingNode() || f.isNull()) {
            return null;
        }

        String text = normalizeText(f.asText(null));
        if (text == null) {
            return null;
        }

        try {
            return new BigInteger(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private BigInteger getBigIntOrZero(JsonNode node, String field) {
        BigInteger value = getBigIntOrNull(node, field);
        return value != null ? value : BigInteger.ZERO;
    }
    private List<String> readStringArray(JsonNode node, String field) {
        List<String> list = new ArrayList<>();
        JsonNode arr = node.path(field);
        if (arr.isArray()) {
            arr.forEach(e -> {
                String value = getTextOrIdOrNull(e);
                if (value != null) {
                    list.add(value);
                }
            });
        } else {
            String single = getTextOrIdOrNull(arr);
            if (single != null) {
                list.add(single);
            }
        }
        return list;
    }
    private String getTextOrNull(JsonNode node, String field) {
        JsonNode f = node.path(field);
        if (f.isMissingNode() || f.isNull()) {
            return null;
        }
        return normalizeText(f.asText(null));
    }

    private List<String> readStringArrayOptional(JsonNode node, String field) {
        if (!node.has(field) || node.path(field).isNull()) return new ArrayList<>();
        return readStringArray(node, field);
    }
    private Creator parseCreator(JsonNode fields) {
        JsonNode c = fields.path("creator");
        if (c.has("fields")) {
            c = c.path("fields");
        }
        if (c.isMissingNode() || c.isNull()) return null;

        Creator creator = new Creator();
        creator.setCreatorAddr(getTextOrNull(c, "creator_addr"));
        creator.setCreatorUpdateAddr(getTextOrNull(c, "creator_update_addr"));
        creator.setCreatorTimestampMs(getBigIntOrNull(c, "creator_timestamp_ms"));
        creator.setCreatorUpdateTimestampMs(getBigIntOrNull(c, "creator_update_timestamp_ms"));
        return creator;
    }

}
