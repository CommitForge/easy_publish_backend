package com.easypublish.dtos;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

public enum ContainerTreeIncludeEnum {
    CONTAINER,
    DATA_TYPE,
    DATA_ITEM,
    DATA_ITEM_VERIFICATION;


    // ---------------- single string ----------------
    public static EnumSet<ContainerTreeIncludeEnum> from(String value) {
        if (value == null || value.isBlank()) {
            return EnumSet.noneOf(ContainerTreeIncludeEnum.class);
        }

        return EnumSet.of(parse(value));
    }

    // ---------------- comma-separated string ----------------
    public static EnumSet<ContainerTreeIncludeEnum> fromCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return EnumSet.noneOf(ContainerTreeIncludeEnum.class);
        }

        EnumSet<ContainerTreeIncludeEnum> set =
                EnumSet.noneOf(ContainerTreeIncludeEnum.class);

        for (String token : csv.split(",")) {
            set.add(parse(token));
        }

        return set;
    }

    // ---------------- list of strings ----------------
    public static EnumSet<ContainerTreeIncludeEnum> fromList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return EnumSet.noneOf(ContainerTreeIncludeEnum.class);
        }

        EnumSet<ContainerTreeIncludeEnum> set =
                EnumSet.noneOf(ContainerTreeIncludeEnum.class);

        for (String value : values) {
            set.add(parse(value));
        }

        return set;
    }

    // ---------------- internal parser ----------------
    private static ContainerTreeIncludeEnum parse(String raw) {
        String normalized = raw.trim().toUpperCase();

        try {
            return ContainerTreeIncludeEnum.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid include value: " + raw +
                            ". Allowed values: " + Arrays.toString(values())
            );
        }
    }
}
