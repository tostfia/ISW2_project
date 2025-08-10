package org.apache.utilities.enums;

import lombok.Getter;

@Getter
public enum FileExtension {
    ARFF(".arff"),
    CSV(".csv"),
    JSON(".json");

    private final String id;

    FileExtension(String id) {
        this.id = id;
    }

    public static FileExtension fromString(String text) {
        for (FileExtension ext : FileExtension.values()) {
            if (ext.id.equalsIgnoreCase(text)) {
                return ext;
            }
        }
        throw new IllegalArgumentException("No constant with text " + text + " found");
    }
}
