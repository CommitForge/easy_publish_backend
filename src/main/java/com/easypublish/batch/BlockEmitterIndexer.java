package com.easypublish.batch;

import com.easypublish.entities.onchain.*;
import com.easypublish.parsed.EasyPublishParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class BlockEmitterIndexer {
    private final EasyPublishParser easyPublishParser;
    private final EntityManager em;
    private final ObjectMapper mapper = new ObjectMapper();

    public BlockEmitterIndexer(EntityManager em, EasyPublishParser easyPublishParser) {
        this.em = em;
        this.easyPublishParser = easyPublishParser;
    }

    @Value("${app.iota.network:testnet}")
    private String iotaNetwork;

    @Value("${app.iota.rpc-urls:}")
    private String iotaRpcUrls;

    @Value("${app.iota.rpc-attempts-per-url:2}")
    private int iotaRpcAttemptsPerUrl;

    @Value("${app.iota.rpc-retry-delay-ms:400}")
    private int iotaRpcRetryDelayMs;

    // ----------------------------
    // JSON helpers
    // ----------------------------
    private JsonNode fields(JsonNode root) {
        JsonNode f = root.path("fields");
        if (f.isMissingNode()) {
            throw new IllegalStateException("Invalid JSON, missing fields: " + root);
        }
        return f;
    }

    private JsonNode unwrapFields(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return node;
        }
        JsonNode inner = node.path("fields");
        return inner.isMissingNode() ? node : inner;
    }

    private String getTextOrNull(JsonNode valueNode) {
        if (valueNode == null || valueNode.isMissingNode() || valueNode.isNull()) {
            return null;
        }
        String value = valueNode.asText(null);
        if (value == null) return null;
        if ("null".equalsIgnoreCase(value.trim())) return null;
        return value;
    }

    private String getTextOrNull(JsonNode node, String field) {
        return getTextOrNull(node.path(field));
    }

    private String getTextOrIdOrNull(JsonNode valueNode) {
        if (valueNode == null || valueNode.isMissingNode() || valueNode.isNull()) {
            return null;
        }

        if (valueNode.isTextual() || valueNode.isNumber() || valueNode.isBoolean()) {
            return normalizeId(valueNode.asText(null));
        }

        JsonNode idNode = valueNode.path("id");
        if (!idNode.isMissingNode() && !idNode.isNull()) {
            return normalizeId(idNode.asText(null));
        }

        JsonNode fieldsIdNode = valueNode.path("fields").path("id");
        if (!fieldsIdNode.isMissingNode() && !fieldsIdNode.isNull()) {
            return normalizeId(fieldsIdNode.asText(null));
        }

        return normalizeId(valueNode.asText(null));
    }

    private String normalizeId(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed)) return null;
        return trimmed;
    }

    private String getIdOrNull(JsonNode node, String field) {
        return getTextOrIdOrNull(node.path(field));
    }

    private BigInteger getBigIntOrNull(JsonNode node, String field) {
        return getBigIntOrNull(node.path(field));
    }

    private BigInteger getBigIntOrNull(JsonNode valueNode) {
        String value = normalizeId(getTextOrNull(valueNode));
        if (value == null) {
            return null;
        }
        try {
            return new BigInteger(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Creator parseCreator(JsonNode f) {
        JsonNode c = unwrapFields(f.path("creator"));
        if (c == null || c.isMissingNode() || c.isNull()) return null;

        Creator creator = new Creator();
        creator.setCreatorAddr(getTextOrNull(c, "creator_addr"));
        creator.setCreatorUpdateAddr(getTextOrNull(c, "creator_update_addr"));
        creator.setCreatorTimestampMs(getBigIntOrNull(c, "creator_timestamp_ms"));
        creator.setCreatorUpdateTimestampMs(getBigIntOrNull(c, "creator_update_timestamp_ms"));

        return creator;
    }

    private Specification parseSpecification(JsonNode f) {
        JsonNode s = unwrapFields(f.path("specification"));
        if (s == null || s.isMissingNode() || s.isNull()) return null;

        Specification spec = new Specification();
        spec.setVersion(getTextOrNull(s, "version"));
        spec.setSchemas(getTextOrNull(s, "schemas"));
        spec.setApis(getTextOrNull(s, "apis"));
        spec.setResources(getTextOrNull(s, "resources"));

        return spec;
    }

    private ContainerPermission parsePermission(JsonNode f) {
        JsonNode perm = unwrapFields(f.path("permission"));
        ContainerPermission p = new ContainerPermission();
        p.setPublicUpdateContainer(perm.path("public_update_container").asBoolean());
        p.setPublicAttachContainerChild(perm.path("public_attach_container_child").asBoolean());
        p.setPublicCreateDataType(perm.path("public_create_data_type").asBoolean());
        p.setPublicPublishDataItem(perm.path("public_publish_data_item").asBoolean());
        return p;
    }

    private ContainerEventConfig parseEventConfig(JsonNode f) {
        JsonNode ev = unwrapFields(f.path("event_config"));
        ContainerEventConfig e = new ContainerEventConfig();
        e.setEventCreate(ev.path("event_create").asBoolean());
        e.setEventPublish(ev.path("event_publish").asBoolean());
        e.setEventAttach(ev.path("event_attach").asBoolean());
        e.setEventAdd(ev.path("event_add").asBoolean());
        e.setEventRemove(ev.path("event_remove").asBoolean());
        e.setEventUpdate(ev.path("event_update").asBoolean());
        return e;
    }

    // ----------------------------
    // Object ID helpers
    // ----------------------------
    private String extractId(JsonNode root) {
        String id = getTextOrIdOrNull(root.path("object_id"));
        if (id != null) return id;

        id = getTextOrIdOrNull(root.path("fields").path("id"));
        if (id != null) return id;

        id = getTextOrIdOrNull(root.path("fields").path("fields").path("id"));
        if (id != null) return id;

        id = getTextOrIdOrNull(root.path("id"));
        if (id != null) return id;

        throw new IllegalArgumentException("JSON is missing object_id: " + root);
    }

    // ----------------------------
    // Container
    // ----------------------------
    @Transactional
    public Container processContainerJson(String json) throws Exception {
        JsonNode root = mapper.readTree(json);
        JsonNode f = fields(root);

        String id = extractId(root);

        Container c = em.find(Container.class, id);
        boolean isNew = c == null;
        if (isNew) c = new Container();

        c.setId(id);
        c.setExternalId(getTextOrNull(f, "external_id"));
        c.setName(getTextOrNull(f, "name"));
        c.setDescription(getTextOrNull(f, "description"));
        c.setContent(getTextOrNull(f, "content"));
        c.setSequenceIndex(getBigIntOrZero(f, "sequence_index"));
        c.setExternalIndex(getBigIntOrZero(f, "external_index"));
        c.setOwnersActiveCount(getBigIntOrNull(f, "owners_active_count"));
        c.setLastOwnerIndex(getBigIntOrZero(f, "last_owner_index"));
        BigInteger lastChildIndex = getBigIntOrNull(f, "last_container_child_link_index");
        if (lastChildIndex == null) {
            lastChildIndex = getBigIntOrNull(f, "last_container_child_index");
        }
        c.setLastContainerChildIndex(lastChildIndex != null ? lastChildIndex : BigInteger.ZERO);
        c.setLastDataTypeIndex(getBigIntOrZero(f, "last_data_type_index"));
        c.setLastDataItemIndex(getBigIntOrZero(f, "last_data_item_index"));
        c.setLastOwnerId(getIdOrNull(f, "last_owner_id"));
        String lastChildId = getIdOrNull(f, "last_container_child_link_id");
        if (lastChildId == null) {
            lastChildId = getIdOrNull(f, "last_container_child_id");
        }
        c.setLastContainerChildId(lastChildId);
        c.setLastDataTypeId(getIdOrNull(f, "last_data_type_id"));
        c.setLastDataItemId(getIdOrNull(f, "last_data_item_id"));
        c.setPrevContainerChainId(getIdOrNull(f, "prev_container_chain_id"));
        c.setLastUpdateRecordIndex(getBigIntOrNull(f, "last_update_record_index"));
        c.setLastUpdateRecordId(getIdOrNull(f, "last_update_record_id"));
        c.setCreator(parseCreator(f));
        c.setSpecification(parseSpecification(f));
        c.setPermission(parsePermission(f));
        c.setEventConfig(parseEventConfig(f));

        if (isNew) em.persist(c);
        else em.merge(c);

        // ---------- Parse content JSON for publish targets ----------
        if (c.getContent() != null && !c.getContent().isBlank()) {
            try {
                // Use objectId so we can associate parsed targets with this DataItem
                easyPublishParser.parseAndSave(c.getContent(), c.getId(), false, true, false);
                System.out.println("[OFFCHAIN-SYNC] EasyPublish targets stored for Container " + c.getId());
            } catch (Exception e) {
                System.err.println("[WARN] Failed to parse EasyPublish content for Container " + c.getId());
                e.printStackTrace();
            }
        }

// ----- OWNERS -----
        JsonNode ownersNode = f.path("owners");

        if (ownersNode.isArray()) {

            // Optional: clear existing owners to resync fully
            if (c.getOwners() == null) {
                c.setOwners(new ArrayList<>());
            }
            c.getOwners().clear();

            for (JsonNode ownerWrapper : ownersNode) {
                JsonNode ownerFields = unwrapFields(ownerWrapper);

                // Extract owner ID (fields.id.id)
                JsonNode ownerIdNode = ownerFields.path("id");
                String ownerId = getTextOrIdOrNull(ownerIdNode);
                if (ownerId == null) continue;

                Owner owner = em.find(Owner.class, ownerId);
                boolean isNewOwner = owner == null;
                if (isNewOwner) owner = new Owner();

                owner.setId(ownerId);
                owner.setAddr(getTextOrNull(ownerFields, "addr"));
                owner.setRole(getTextOrNull(ownerFields, "role"));
                owner.setRemoved(ownerFields.path("removed").asBoolean(false));
                owner.setSequenceIndex(getBigIntOrZero(ownerFields, "sequence_index"));
                owner.setCreator(parseCreator(ownerFields));

                // Important: set relationship
                owner.setContainer(c);

                if (isNewOwner) em.persist(owner);
                else em.merge(owner);

                c.getOwners().add(owner);
            }
        }
        return c;
    }

    @Transactional
    public Container updateContainerJson(String json) throws Exception {
        return processContainerJson(json);
    }

    // ----------------------------
    // Owner
    // ----------------------------
    @Transactional
    public Owner processOwnerJson(String json, String containerId) throws Exception {
        JsonNode root = mapper.readTree(json);
        JsonNode f = fields(root);

        String id = extractId(root);

        // 🔥 FIX: fallback containerId from JSON if null
        if (containerId == null) {
            containerId = getIdOrNull(f, "container_id");
        }

        Container container = (containerId == null || containerId.isBlank())
                ? null
                : em.find(Container.class, containerId);

        Owner o = em.find(Owner.class, id);
        boolean isNewOwner = o == null;
        if (isNewOwner) {
            o = new Owner();
        }
        o.setId(id);
        o.setCreator(parseCreator(f));
        o.setAddr(getTextOrNull(f, "addr"));
        o.setRole(getTextOrNull(f, "role"));
        o.setRemoved(f.path("removed").asBoolean());
        o.setSequenceIndex(getBigIntOrZero(f,"sequence_index"));
        o.setPrevId(getIdOrNull(f, "prev_id"));
        if (container != null) {
            o.setContainer(container);
        }

        if (isNewOwner) em.persist(o);
        else em.merge(o);

        if (container != null) {
            container.setLastOwnerIndex(o.getSequenceIndex());
            container.setLastOwnerId(o.getId());
            em.merge(container);
        }

        return o;
    }

    @Transactional
    public Owner updateOwnerJson(String json, String containerId) throws Exception {
        return processOwnerJson(json, containerId);
    }

    // ----------------------------
    // DataType
    // ----------------------------
    @Transactional
    public DataType processDataTypeJson(String json, String containerId) throws Exception {
        JsonNode root = mapper.readTree(json);
        JsonNode f = fields(root);

        String id = extractId(root);

        // 🔥 FIX: fallback containerId from JSON
        if (containerId == null) {
            containerId = getIdOrNull(f, "container_id");
        }

        if (containerId == null) {
            throw new IllegalStateException("DataType missing container_id: " + json);
        }

        Container container = (containerId == null || containerId.isBlank())
                ? null
                : em.find(Container.class, containerId);

        DataType dt = new DataType();
        dt.setId(id);
        dt.setContainerId(containerId);
        dt.setCreator(parseCreator(f));
        dt.setSpecification(parseSpecification(f));
        dt.setExternalId(getTextOrNull(f, "external_id"));
        dt.setName(getTextOrNull(f, "name"));
        dt.setDescription(getTextOrNull(f, "description"));
        dt.setContent(getTextOrNull(f, "content"));
        dt.setSequenceIndex(getBigIntOrZero(f, "sequence_index"));
        dt.setExternalIndex(getBigIntOrZero(f, "external_index"));
        dt.setPrevId(getIdOrNull(f, "prev_id"));
        dt.setLastDataItemId(getIdOrNull(f, "last_data_item_id"));

        DataType existing = em.find(DataType.class, dt.getId());
        if (existing == null) em.persist(dt);
        else em.merge(dt);
        // ---------- Parse content JSON for publish targets ----------
        if (dt.getContent() != null && !dt.getContent().isBlank()) {
            try {
                // Use objectId so we can associate parsed targets with this DataItem
                easyPublishParser.parseAndSave(dt.getContent(), dt.getId(), false, false, true);
                System.out.println("[OFFCHAIN-SYNC] EasyPublish targets stored for DataType " + dt.getId());
            } catch (Exception e) {
                System.err.println("[WARN] Failed to parse EasyPublish content for DataType " + dt.getId());
                e.printStackTrace();
            }
        }

        if (container != null) {
            container.setLastDataTypeIndex(dt.getSequenceIndex());
            container.setLastDataTypeId(dt.getId());
            em.merge(container);
        }

        return dt;
    }

    @Transactional
    public DataType updateDataTypeJson(String json, String containerId) throws Exception {
        return processDataTypeJson(json, containerId);
    }

    // ----------------------------
    // DataItem
    // ----------------------------
    @Transactional
    public DataItem processDataItemJson(
            String json,
            String containerId,
            String dataTypeId
    ) throws Exception {

        JsonNode root = mapper.readTree(json);
        JsonNode f = fields(root);

        String id = extractId(root);

        // -----------------------------
        // Container + datatype fallback
        // -----------------------------
        if (containerId == null) {
            containerId = getIdOrNull(f, "container_id");
        }

        if (dataTypeId == null) {
            dataTypeId = getIdOrNull(f, "data_type_id");
        }

        // -----------------------------
        // Find or create entity
        // -----------------------------
        DataItem di = em.find(DataItem.class, id);
        if (di == null) {
            di = new DataItem();
            di.setId(id);
        }

        // -----------------------------
        // Core fields (1:1 with Move)
        // -----------------------------
        di.setContainerId(containerId);
        di.setDataTypeId(dataTypeId);
        di.setExternalId(getTextOrNull(f, "external_id"));
        di.setCreator(parseCreator(f));
        di.setName(getTextOrNull(f, "name"));
        di.setDescription(getTextOrNull(f, "description"));
        di.setContent(getTextOrNull(f, "content"));

        // 🔴 CRITICAL: trust chain sequence index
        di.setSequenceIndex(getBigIntOrZero(f, "sequence_index"));
        di.setExternalIndex(getBigIntOrZero(f, "external_index"));

        di.setPrevId(getIdOrNull(f, "prev_id"));
        di.setPrevDataItemChainId(getIdOrNull(f, "prev_data_item_chain_id"));
        di.setPrevDataTypeItemId(getIdOrNull(f, "prev_data_type_item_id"));

        // -----------------------------
        // Recipients (Option<vector>)
        // -----------------------------
        di.setRecipients(readStringArrayOptional(f, "recipients"));

        // -----------------------------
        // References
        // -----------------------------
        di.setReferences(readStringArray(f, "references"));

        // -----------------------------
        // Verification (addresses)
        // -----------------------------
        di.setVerificationSuccessAddresses(
                readStringArray(f, "verification_success_addresses")
        );
        di.setVerificationFailureAddresses(
                readStringArray(f, "verification_failure_addresses")
        );

        // -----------------------------
        // Verification (data items)
        // -----------------------------
        di.setVerificationSuccessDataItem(
                readStringArray(f, "verification_success_data_item")
        );
        di.setVerificationFailureDataItem(
                readStringArray(f, "verification_failure_data_item")
        );

        JsonNode verifiedNode = f.get("verified");
        di.setVerified((verifiedNode == null || verifiedNode.isNull()) ? null : verifiedNode.asBoolean());

        // -----------------------------
        // Persist
        // -----------------------------
        if (em.contains(di)) {
            em.merge(di);
        } else {
            em.persist(di);
        }

        // -----------------------------
        // Update DataType pointer (safe)
        // -----------------------------
        if (dataTypeId != null) {
            DataType dt = em.find(DataType.class, dataTypeId);
            if (dt != null) {
                dt.setLastDataItemId(di.getId());
                em.merge(dt);
            }
        }

        return di;
    }


    @Transactional
    public DataItem updateDataItemJson(String json, String containerId, String dataTypeId) throws Exception {
        return processDataItemJson(json, containerId, dataTypeId);
    }

    // ----------------------------
    // ContainerChildLink
    // ----------------------------
    @Transactional
    public ContainerChildLink processContainerChildLinkJson(String json, String containerId) throws Exception {
        JsonNode root = mapper.readTree(json);
        JsonNode f = fields(root);

        String id = extractId(root);

        if (containerId == null) {
            containerId = getIdOrNull(f, "container_parent_id");
        }

        Container container = em.find(Container.class, containerId);

        ContainerChildLink l = new ContainerChildLink();
        l.setId(id);
        l.setContainerParentId(containerId);
        l.setContainerChildId(getIdOrNull(f, "container_child_id"));
        l.setCreator(parseCreator(f));
        l.setExternalId(getTextOrNull(f, "external_id"));
        l.setName(getTextOrNull(f, "name"));
        l.setDescription(getTextOrNull(f, "description"));
        l.setContent(getTextOrNull(f, "content"));
        l.setSequenceIndex(getBigIntOrZero(f, "sequence_index"));
        l.setExternalIndex(getBigIntOrZero(f, "external_index"));
        l.setPrevId(getIdOrNull(f, "prev_id"));

        ContainerChildLink existing = em.find(ContainerChildLink.class, l.getId());
        if (existing == null) em.persist(l);
        else em.merge(l);

        if (container != null) {
            container.setLastContainerChildIndex(l.getSequenceIndex());
            container.setLastContainerChildId(l.getId());
            em.merge(container);
        }

        return l;
    }

    @Transactional
    public ContainerChildLink updateContainerChildLinkJson(String json, String containerId) throws Exception {
        return processContainerChildLinkJson(json, containerId);
    }

    // ----------------------------
    // UpdateChainRecord
    // ----------------------------
    @Transactional
    public UpdateChainRecord processUpdateRecordJson(String json, String lastRecordId, BigInteger lastRecordIndex) throws Exception {
        JsonNode root = mapper.readTree(json);
        JsonNode f = fields(root);

        String id = extractId(root);

        UpdateChainRecord r = new UpdateChainRecord();
        r.setId(id);
        r.setObjectId(getIdOrNull(f, "object_id"));
        r.setCreator(parseCreator(f));
        r.setObjectType(getTextOrNull(f, "object_type"));
        r.setAction(getBigIntOrZero(f, "action"));
        BigInteger sequence = getBigIntOrNull(f, "sequence_index");
        if (sequence == null) {
            sequence = lastRecordIndex != null ? lastRecordIndex.add(BigInteger.ONE) : BigInteger.ZERO;
        }
        r.setSequenceIndex(sequence);
        String prevId = getIdOrNull(f, "prev_id");
        if (prevId == null) {
            prevId = lastRecordId;
        }
        r.setPrevId(prevId);

        UpdateChainRecord existing = em.find(UpdateChainRecord.class, r.getId());
        if (existing == null) em.persist(r);
        else em.merge(r);

        return r;
    }

    @Transactional
    public void syncUpdateChainRecords(String updateChainId) throws Exception {
        JsonNode records = fetchContainerData(updateChainId, "update_record").path("items");
        if (!records.isArray() || records.size() == 0) return;

        List<JsonNode> recordList = new ArrayList<>();
        records.forEach(recordList::add);

        // Sort by prev_id chain to keep consistent ordering
        recordList.sort(Comparator.comparing(r -> getTextOrIdOrNull(r.path("fields").path("prev_id")),
                Comparator.nullsFirst(String::compareTo)));

        String lastId = null;
        BigInteger lastIndex = null;

        for (JsonNode recordNode : recordList) {
            UpdateChainRecord r = processUpdateRecordJson(recordNode.toString(), lastId, lastIndex);
            lastId = r.getId();
            lastIndex = r.getSequenceIndex();
        }
    }

    // ----------------------------
    // Node.js fetch helper
    // ----------------------------
    private JsonNode fetchContainerData(String id, String type) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "node",
                "node/getContainerItems.js",
                id,
                type
        );
        if (iotaNetwork != null && !iotaNetwork.isBlank()) {
            pb.environment().put("IOTA_NETWORK", iotaNetwork.trim());
        }
        if (iotaRpcUrls != null && !iotaRpcUrls.isBlank()) {
            pb.environment().put("IOTA_RPC_URLS", iotaRpcUrls.trim());
        }
        if (iotaRpcAttemptsPerUrl > 0) {
            pb.environment().put("IOTA_RPC_ATTEMPTS_PER_URL", String.valueOf(iotaRpcAttemptsPerUrl));
        }
        if (iotaRpcRetryDelayMs >= 0) {
            pb.environment().put("IOTA_RPC_RETRY_DELAY_MS", String.valueOf(iotaRpcRetryDelayMs));
        }

        Process process = pb.start();

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

        return mapper.readTree(stdout.toString());
    }

    private List<String> readStringArray(JsonNode node, String fieldName) {
        JsonNode arr = node.path(fieldName);
        if (arr.isMissingNode() || arr.isNull()) {
            return new ArrayList<>();
        }

        List<String> result = new ArrayList<>();
        if (arr.isArray()) {
            for (JsonNode n : arr) {
                String value = getTextOrIdOrNull(n);
                if (value != null) {
                    result.add(value);
                }
            }
            return result;
        }

        String single = getTextOrIdOrNull(arr);
        if (single != null) {
            result.add(single);
        }
        return result;
    }
    private List<String> readStringArrayOptional(JsonNode node, String fieldName) {
        return readStringArray(node, fieldName);
    }

    private BigInteger getBigIntOrZero(JsonNode node, String field) {
        BigInteger value = getBigIntOrNull(node, field);
        return value != null ? value : BigInteger.ZERO;
    }

}
