package com.easypublish.service.easypublishindex;

import com.easypublish.entities.offchain.OffchainFollowContainer;
import com.easypublish.entities.onchain.DataItem;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class FollowContainersIndexFeature implements EasyPublishIndexFeature {

    @Override
    public String featureKey() {
        return "follow_containers";
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

        String actorAddress = dataItem.getCreator() == null
                ? null
                : EasyPublishIndexUtils.normalize(dataItem.getCreator().getCreatorAddr());
        if (actorAddress == null) {
            return;
        }

        BigInteger sequenceIndex = dataItem.getSequenceIndex();
        List<FollowAction> followActions = parseFollowActions(easyPublishMap);
        if (followActions.isEmpty()) {
            return;
        }

        Map<String, String> containerCreatorById = context.containerCreatorById();
        for (FollowAction followAction : followActions) {
            String targetContainerId = EasyPublishIndexUtils.normalize(followAction.containerId());
            if (targetContainerId == null || !containerCreatorById.containsKey(targetContainerId)) {
                continue;
            }

            String targetCreator = containerCreatorById.get(targetContainerId);
            if (targetCreator != null && Objects.equals(targetCreator, actorAddress)) {
                continue;
            }

            accumulator.addFollowAction(
                    new OffchainFollowContainer(
                            dataItemId,
                            actorAddress,
                            targetContainerId,
                            followAction.enabled(),
                            sequenceIndex
                    )
            );
        }
    }

    private List<FollowAction> parseFollowActions(Map<String, Object> easyPublishMap) {
        Object direct = easyPublishMap.get("follow_containers");
        if (direct == null) {
            direct = easyPublishMap.get("followContainers");
        }

        if (direct == null) {
            Object followNamespace = easyPublishMap.get("follow");
            if (followNamespace instanceof Map<?, ?> followMap) {
                Map<String, Object> map = EasyPublishIndexUtils.toStringObjectMap(followMap);
                direct = map.get("containers");
                if (direct == null) {
                    direct = map.get("targets");
                }
            }
        }

        if (direct == null) {
            return List.of();
        }

        List<FollowAction> actions = new ArrayList<>();
        if (direct instanceof Collection<?> entries) {
            for (Object entry : entries) {
                FollowAction parsed = parseFollowAction(entry);
                if (parsed != null) {
                    actions.add(parsed);
                }
            }
            return actions;
        }

        FollowAction parsed = parseFollowAction(direct);
        return parsed == null ? List.of() : List.of(parsed);
    }

    private FollowAction parseFollowAction(Object rawEntry) {
        if (rawEntry == null) {
            return null;
        }

        if (rawEntry instanceof String rawString) {
            String id = EasyPublishIndexUtils.normalize(rawString);
            if (id == null) {
                return null;
            }
            return new FollowAction(id, true);
        }

        if (rawEntry instanceof Map<?, ?> rawMap) {
            Map<String, Object> map = EasyPublishIndexUtils.toStringObjectMap(rawMap);
            String containerId = EasyPublishIndexUtils.firstObjectId(
                    map.get("container_id"),
                    map.get("containerId"),
                    map.get("id"),
                    map.get("object_id"),
                    map.get("value")
            );
            if (containerId == null) {
                return null;
            }

            boolean enabled = EasyPublishIndexUtils.parseBoolean(
                    map.containsKey("enabled") ? map.get("enabled") : map.get("active"),
                    true
            );

            return new FollowAction(containerId, enabled);
        }

        return null;
    }

    private record FollowAction(String containerId, boolean enabled) {
    }
}
