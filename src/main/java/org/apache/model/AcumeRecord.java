package org.apache.model;



import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AcumeRecord {
    private final int id;
    private final int size; // Corrisponde a LOC
    private final double predictedProbability;
    private final String actual; // "YES" o "NO"
}
