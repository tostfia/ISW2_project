package org.apache.utilities.enums;

public enum DatasetType {
    TRAINING("training"),
    TESTING("testing");

    private final String id;
    DatasetType(String id) {
        this.id = id;
    }
    public String getId() {
        return id;
    }
    public static DatasetType fromString(String text) {
        for (DatasetType type : DatasetType.values()) {
            if (type.id.equalsIgnoreCase(text)) {
                return type;
            }
        }
        throw new IllegalArgumentException("No constant with text " + text + " found");
    }
}
