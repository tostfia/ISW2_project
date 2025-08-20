package org.apache.utilities.enumeration;


import lombok.Getter;

@Getter
public enum FileExtension {
    ARFF("ARFF"),
    CSV("CSV");

    private final String id;

    FileExtension(String id) {
        this.id = id;
    }



}
