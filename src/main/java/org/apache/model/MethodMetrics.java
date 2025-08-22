package org.apache.model;

import lombok.Getter;
import lombok.Setter;

@Getter
public class MethodMetrics {
    @Setter
    private boolean isBuggy;
    @Setter
    private int size;
    @Setter
    //Metriche di Complessità e Smells
    private long loc; // Lines of Code
    @Setter
    private long cycloComplexity;
    @Setter// Cyclomatic Complexity
    private long parameterCount; // Numero di Parametri
    @Setter
    private long cognitiveComplexity;
    @Setter// Cognitive Complexity
    private long nestingDepth; // Nesting Depth
    @Setter
    private long numberOfCodeSmells; // Numero di Code Smells
    @Setter
    private long fanIn;
    @Setter
    private  long fanOut;
    @Setter
    private long age;

    //Metriche di Processo/Storiche

    @Setter
    private long methodHistory;
    @Setter
    private  LOCMetrics removedLOCMetrics;
    @Setter
    private  LOCMetrics churnMetrics;
    @Setter
    private  LOCMetrics addedLOCMetrics;
    @Setter
    private long numAuthors;
    @Setter
    private long numberOfRevisions; // Numero di Revisioni del Metodo



    public MethodMetrics(){
        this.isBuggy = false;
        this.size = 0;
        this.loc = 0;
        this.cycloComplexity = 0;
        this.parameterCount=0;
        this.cognitiveComplexity = 0;
        this.nestingDepth = 0;
        this.removedLOCMetrics = new LOCMetrics();
        this.addedLOCMetrics = new LOCMetrics();
        this.churnMetrics = new LOCMetrics();
        this.numberOfRevisions = 0;
        this.methodHistory = 0;
        this.numAuthors = 0;
        this.parameterCount=0;
        this.numberOfCodeSmells=0;
    }
    public void incrementCodeSmellCount() {
        this.numberOfCodeSmells++;
    }


}
