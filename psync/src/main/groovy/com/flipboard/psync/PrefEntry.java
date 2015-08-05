package com.flipboard.psync;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

/**
 * This represents a preference entry
 * <p>
 * T represents the type of value this preference is backed by, such as a boolean
 */
public final class PrefEntry<T> implements Comparable<PrefEntry> {

    public static final PrefEntry<Void> BLANK = new PrefEntry<>("", null, null);

    public static <T> PrefEntry<T> create(@NotNull String key, T defaultValue) {
        return create(key, defaultValue, null);
    }

    public static <T> PrefEntry<T> create(@NotNull String key, T defaultValue, String resType) {
        return new PrefEntry<>(key, defaultValue, resType);
    }

    public String key;
    public T defaultValue;
    public Class<?> defaultType;
    public Class<?> valueType;

    // For list types
    public boolean hasListAttributes = false;
    public String entriesGetterStmt = null;
    public String entryValuesGetterStmt = null;

    // Resource specific info
    public String resType = null;
    public boolean isResource = false;
    public String resourceDefaultValueGetterStmt = null;

    private PrefEntry(String key, T defaultValue, String resType) {
        this.key = key;
        this.defaultValue = defaultValue;

        if (resType != null) {
            this.resType = resType;
            this.isResource = true;
        }

        if (defaultValue == null) {
            this.valueType = this.defaultType =  null;
        } else if (isResource) {
            this.defaultType = String.class;
            resolveResourceInfo();
        } else if (defaultValue instanceof Boolean) {
            this.valueType = this.defaultType = boolean.class;
        } else if (defaultValue instanceof Integer) {
            this.valueType = this.defaultType = int.class;
        } else if (defaultValue instanceof String) {
            this.valueType = this.defaultType = String.class;
        } else {
            throw new UnsupportedOperationException("Unsupported type: " + defaultValue.getClass().getSimpleName());
        }
    }

    /**
     * Marks this entry as a list preference if the passed parameters are value @array resource refs
     */
    void markAsListPreference(String entries, String entryValues) {
        String entriesName = getArrayResourceName(entries);
        if (entriesName != null) {
            this.entriesGetterStmt = "getTextArray(R.array." + entriesName + ")";
            this.hasListAttributes = true;
        }
        String entryValuesName = getArrayResourceName(entryValues);
        if (entryValuesName != null) {
            this.entryValuesGetterStmt = "getTextArray(R.array." + entryValuesName + ")";
            this.hasListAttributes = true;
        }
    }

    private static String getArrayResourceName(String ref) {
        if (ref == null || !ref.startsWith("@array/") || ref.length() < 7) {
            return null;
        }

        return ref.substring(7);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o == null || getClass() != o.getClass()) {
            return false;
        }

        return hashCode() == o.hashCode();
    }

    @Override
    public int compareTo(@NotNull PrefEntry o) {
        return this.key.compareTo(o.key);
    }

    @Override
    public String toString() {
        return "PrefEntry{key="
                + this.key
                + ", defaultValue="
                + this.defaultValue
                + ", defaultType="
                + this.defaultType
                + ", valueType="
                + this.valueType
                + ", resType="
                + this.resType
                + ", isResource="
                + this.isResource
                + ", resourceDefaultValueGetterStmt="
                + this.resourceDefaultValueGetterStmt
                + ", hasListAttributes="
                + hasListAttributes
                + ", entriesGetterStmt="
                + entriesGetterStmt
                + ", entryValuesGetterStmt="
                + entryValuesGetterStmt
                + "}";
    }

    public boolean isBlank() {
        return StringUtils.isEmpty(key);
    }

    // TODO
    // Float
    // Long
    // String set
    // String[] overload for array
    // int[] overload for array
    private void resolveResourceInfo() {
        final String type = resType;
        String statement;

        switch (type) {
            case "bool":
                statement = "getBoolean(%s)";
                valueType = boolean.class;
                break;
            case "color":
                statement = "getColor(%s)";
                valueType = int.class;
                break;
            case "integer":
                statement = "getInteger(%s)";
                valueType = int.class;
                break;
            case "string":
                statement = "getString(%s)";
                valueType = String.class;
                break;
            case "array":
                statement = "getTextArray(%s)";
                valueType = CharSequence[].class;
                break;
            default:
                // We can't handle this type yet, force it to blank out
                this.key = "";
                return;
        }

        resourceDefaultValueGetterStmt = String.format(statement, "defaultResId");
    }
}
