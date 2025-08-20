package org.apache.utilities.enumeration;


import lombok.Getter;

@Getter
public enum FileExtension {
    ARFF("arff"),
    CSV("csv");

    private final String id;

    FileExtension(String id) {
        this.id = id;
    }




}
