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
    private long numCodeSmells;
    @Setter// Numero di Code Smells
    private long cognitiveComplexity;
    @Setter// Cognitive Complexity
    private long nestingDepth; // Nesting Depth

    //Metriche di Processo/Storiche
    @Setter
    private long churn;
    @Setter
    private long maxChurn;
    @Setter
    private long avgChurn;
    @Setter
    private long methodHistory;
    @Setter
    private long numAuthors;


    public ClassMetrics(){
        this.isBuggy = false;
        this.size = 0;
        this.LOC = 0;
        this.cycloComplexity = 0;
        this.numCodeSmells = 0;
        this.cognitiveComplexity = 0;
        this.nestingDepth = 0;
        this.churn = 0;
        this.maxChurn = 0;
        this.avgChurn = 0;
        this.methodHistory = 0;
        this.numAuthors = 0;
    }


}
