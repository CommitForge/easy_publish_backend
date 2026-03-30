package com.easypublish.service.easypublishindex;

import com.easypublish.entities.offchain.OffchainDataItemRevision;
import com.easypublish.entities.onchain.DataItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class RevisionsIndexFeature implements EasyPublishIndexFeature {

    @Override
    public String featureKey() {
        return "revisions";
    }

    @Override
    public void index(
            DataItem dataItem,
            Map<String, Object> easyPublishMap,
            EasyPublishIndexContext context,
            EasyPublishIndexAccumulator accumulator
    ) {
        String dataItemId = EasyPublishIndexUtils.normalize(dataItem.getId());
        if (dataItemId == null) {
            return;
        }

        List<String> referenceIds = EasyPublishIndexUtils.normalizeObjectIds(dataItem.getReferences());
        RevisionExtraction revisionExtraction = extractRevisionExtraction(easyPublishMap, referenceIds);
        if (!revisionExtraction.enabled()) {
            return;
        }

        int beforeCount = accumulator.revisionRows().size();
        String currentContainerId = EasyPublishIndexUtils.normalize(dataItem.getContainerId());
        LinkedHashSet<String> uniquePreviousIds = new LinkedHashSet<>(revisionExtraction.replaces());

        for (String previousIdRaw : uniquePreviousIds) {
            String previousId = EasyPublishIndexUtils.normalize(previousIdRaw);
            if (previousId == null || Objects.equals(previousId, dataItemId)) {
                continue;
            }

            DataItem previousDataItem = context.dataItemById().get(previousId);
            if (previousDataItem == null) {
                continue;
            }

            if (!Objects.equals(currentContainerId, EasyPublishIndexUtils.normalize(previousDataItem.getContainerId()))) {
                continue;
            }

            accumulator.addRevisionRow(new OffchainDataItemRevision(
                    dataItemId,
                    previousId,
                    true,
                    revisionExtraction.changeDescription(),
                    revisionExtraction.source(),
                    dataItem.getSequenceIndex()
            ));
        }

        if (accumulator.revisionRows().size() == beforeCount) {
            accumulator.addRevisionRow(new OffchainDataItemRevision(
                    dataItemId,
                    null,
                    true,
                    revisionExtraction.changeDescription(),
                    revisionExtraction.source(),
                    dataItem.getSequenceIndex()
            ));
        }
    }

    private RevisionExtraction extractRevisionExtraction(
            Map<String, Object> easyPublishMap,
            List<String> referenceIds
    ) {
        Object revisionsObject = easyPublishMap.get("revisions");
        if (revisionsObject == null) {
            return RevisionExtraction.disabled();
        }

        if (revisionsObject instanceof Boolean revisionsEnabled) {
            if (!revisionsEnabled) {
                return RevisionExtraction.disabled();
            }
            return new RevisionExtraction(true, referenceIds, "references", null);
        }

        if (revisionsObject instanceof Map<?, ?> revisionsMapRaw) {
            Map<String, Object> revisionsMap = EasyPublishIndexUtils.toStringObjectMap(revisionsMapRaw);
            boolean enabled = EasyPublishIndexUtils.parseBoolean(
                    revisionsMap.containsKey("enabled") ? revisionsMap.get("enabled") : revisionsMap.get("active"),
                    false
            );
            if (!enabled) {
                return RevisionExtraction.disabled();
            }

            List<String> explicitPreviousIds = new ArrayList<>();
            for (String key : EasyPublishIndexUtils.REVISION_ID_KEYS) {
                explicitPreviousIds.addAll(EasyPublishIndexUtils.normalizeObjectIds(revisionsMap.get(key)));
            }

            List<String> effectivePreviousIds = explicitPreviousIds.isEmpty()
                    ? referenceIds
                    : EasyPublishIndexUtils.normalizeObjectIds(explicitPreviousIds);

            String source = explicitPreviousIds.isEmpty() ? "references" : "revision_setting";
            String changeDescription = EasyPublishIndexUtils.firstString(
                    revisionsMap.get("change"),
                    revisionsMap.get("changeDescription"),
                    revisionsMap.get("change_description"),
                    revisionsMap.get("note")
            );

            return new RevisionExtraction(true, effectivePreviousIds, source, changeDescription);
        }

        List<String> explicitPreviousIds = EasyPublishIndexUtils.normalizeObjectIds(revisionsObject);
        List<String> effectivePreviousIds = explicitPreviousIds.isEmpty()
                ? referenceIds
                : explicitPreviousIds;
        String source = explicitPreviousIds.isEmpty() ? "references" : "revision_setting";

        return new RevisionExtraction(true, effectivePreviousIds, source, null);
    }

    private record RevisionExtraction(
            boolean enabled,
            List<String> replaces,
            String source,
            String changeDescription
    ) {
        private static RevisionExtraction disabled() {
            return new RevisionExtraction(false, List.of(), null, null);
        }
    }
}
