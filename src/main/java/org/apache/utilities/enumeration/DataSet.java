package org.apache.utilities.enumeration;



public enum DataSet {
    TRAINING("TRAINING"),
    TESTING("TESTING");

    private final String id;

     DataSet(String id) {
        this.id = id;
    }

    public static DataSet fromString(String id) {
        for (DataSet type : values()) {
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