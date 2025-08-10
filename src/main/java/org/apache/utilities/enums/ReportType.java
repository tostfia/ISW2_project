package org.apache.utilities.enums;

import lombok.Getter;

@Getter
public enum ReportType {
    RELEASE("releases"),
    TICKETS("tickets"),
    COMMITS("commits"),
    SUMMARY("summary");

    private final String id;

    ReportType(String id) {
        this.id = id;
    }

    public static ReportType fromString(String text) {
        for (ReportType type : ReportType.values()) {
            if (type.id.equalsIgnoreCase(text)) {
                return type;
            }
        }
        throw new IllegalArgumentException("No constant with text " + text + " found");
    }
}
