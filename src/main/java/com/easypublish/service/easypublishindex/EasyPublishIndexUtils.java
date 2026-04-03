package com.easypublish.service.easypublishindex;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class EasyPublishIndexUtils {

    public static final Pattern OBJECT_ID_PATTERN = Pattern.compile("^0x[a-fA-F0-9]{64}$");
    public static final List<String> REVISION_ID_KEYS = List.of(
            "replaces",
            "replaced",
            "previous",
            "previousIds",
            "previous_ids",
            "previousDataItemIds",
            "previous_data_item_ids",
            "of",
            "items",
            "revisionOf",
            "revision_of"
    );

    private EasyPublishIndexUtils() {
    }

    public static Map<String, Object> parseEasyPublishMap(String content, ObjectMapper mapper) {
        if (content == null || content.isBlank()) {
            return null;
        }

        try {
            Map<String, Object> root = mapper.readValue(content, new TypeReference<Map<String, Object>>() {
            });
            Object easyPublish = root.get("easy_publish");
            if (easyPublish == null) {
                easyPublish = root.get("easyPublish");
            }
            if (!(easyPublish instanceof Map<?, ?> easyPublishMap)) {
                return null;
            }

            return toStringObjectMap(easyPublishMap);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed;
    }

    public static String firstString(Object... values) {
        for (Object value : values) {
            if (value instanceof String stringValue) {
                String normalized = normalize(stringValue);
                if (normalized != null) {
                    return normalized;
                }
            }
        }
        return null;
    }

    public static String firstObjectId(Object... values) {
        for (Object value : values) {
            List<String> ids = normalizeObjectIds(value);
            if (!ids.isEmpty()) {
                return ids.get(0);
            }
        }
        return null;
    }

    public static boolean parseBoolean(Object value, boolean defaultValue) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue) {
            String normalized = normalize(stringValue);
            if (normalized != null) {
                return Boolean.parseBoolean(normalized);
            }
        }
        return defaultValue;
    }

    public static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static List<String> normalizeObjectIds(Object value) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        collectObjectIds(value, ids);
        return List.copyOf(ids);
    }

    private static void collectObjectIds(Object value, Set<String> sink) {
        if (value == null) {
            return;
        }

        if (value instanceof String stringValue) {
            for (String token : stringValue.split(",")) {
                String normalized = normalize(token);
                if (normalized != null && OBJECT_ID_PATTERN.matcher(normalized).matches()) {
                    sink.add(normalized);
                }
            }
            return;
        }

        if (value instanceof Collection<?> collectionValue) {
            for (Object entry : collectionValue) {
                collectObjectIds(entry, sink);
            }
            return;
        }

        if (value instanceof Map<?, ?> mapValue) {
            collectObjectIds(mapValue.get("id"), sink);
            collectObjectIds(mapValue.get("object_id"), sink);
            collectObjectIds(mapValue.get("dataItemId"), sink);
            collectObjectIds(mapValue.get("data_item_id"), sink);
            collectObjectIds(mapValue.get("containerId"), sink);
            collectObjectIds(mapValue.get("container_id"), sink);
            collectObjectIds(mapValue.get("address"), sink);
            collectObjectIds(mapValue.get("value"), sink);
        }
    }

    public static Map<String, Object> toStringObjectMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }
}
