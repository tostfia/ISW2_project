package org.apache.utilities.enumeration;



public enum FileExtension {
    ARFF("ARFF"),
    CSV("CSV");

    private final String id;

    private FileExtension(String id) {
        this.id = id;
    }

    public static FileExtension fromString(String id) {
        for (FileExtension type : values()) {
            if (type.getId().equals(id)) {
                return type;
            }
        }
        return null;
    }

    public String getId() {
        return id;
    }

}
