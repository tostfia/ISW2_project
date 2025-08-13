package org.apache.model;



import lombok.Getter;
import lombok.Setter;

@Getter
public class AnalyzedMethod {
    private final String signature; // La firma completa del metodo
    private final String simpleName;
    private final AnalyzedClass parentClass; // Riferimento alla classe che lo contiene

    @Setter
    private DataMetrics metrics;

    public AnalyzedMethod(String signature, String simpleName, AnalyzedClass parentClass) {
        this.signature = signature;
        this.simpleName = simpleName;
        this.parentClass = parentClass;
        this.metrics = new DataMetrics(); // Inizializziamo subito un oggetto metriche vuoto
    }
}
