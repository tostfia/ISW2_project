package org.apache.model;

import lombok.Getter;
import lombok.Setter;


@Getter
public class ClassMetrics {
    @Setter
    private boolean isBuggy;
    @Setter
    private int size;
    @Setter
    //Metriche di Complessità e Smells
    private long LOC; // Lines of Code
    @Setter
    private long cycloComplexity;
    @Setter// Cyclomatic Complexity
    private long parameterCount;
    @Setter
    private long cognitiveComplexity;
    @Setter// Cognitive Complexity
    private long nestingDepth; // Nesting Depth
    @Setter
    private long numberOfCodeSmells; // Numero di Code Smells


    private final LOCMetrics removedLOCMetrics;
    private final LOCMetrics churnMetrics;
    private final LOCMetrics addedLOCMetrics;
    @Setter
    private long methodHistory;
    @Setter
    private long numAuthors;
    @Setter
    private long numberOfRevisions; // Numero di Revisioni della Classe


    public ClassMetrics(){
        this.isBuggy = false;
        this.size = 0;
        this.LOC = 0;
        this.numberOfRevisions = 0;
        this.cycloComplexity = 0;
        this.parameterCount=0;
        this.cognitiveComplexity = 0;
        this.nestingDepth = 0;
        this.removedLOCMetrics = new LOCMetrics();
        this.churnMetrics = new LOCMetrics();
        this.addedLOCMetrics = new LOCMetrics();
        this.methodHistory = 0;
        this.numAuthors = 0;
    }


    public void setChurnMetrics(int churn, int maxChurningFactor, double avgChurningFactor) {
        this.churnMetrics.setVal(churn);
        this.churnMetrics.setMaxVal(maxChurningFactor);
        this.churnMetrics.setAvgVal(avgChurningFactor);
    }


}
