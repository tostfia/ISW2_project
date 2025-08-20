package org.apache.utilities.enumeration;


import lombok.Getter;

@Getter
public enum DataSet {
    TRAINING("TRAINING"),
    TESTING("TESTING");

    private final String id;

     DataSet(String id) {
        this.id = id;
    }



}