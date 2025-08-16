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
    private long LOC; // Lines of Code
    @Setter
    private long cycloComplexity;
    @Setter// Cyclomatic Complexity
    private long parameterCount; // Numero di Parametri
    @Setter
    private long cognitiveComplexity;
    @Setter// Cognitive Complexity
    private long nestingDepth; // Nesting Depth

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
    private int totalStmtAdded;
    private int maxStmtAdded;
    private double avgStmtAdded;

    private int totalStmtDeleted;
    private int maxStmtDeleted;
    private double avgStmtDeleted;

    private int totalChurn;
    private int maxChurn;
    private double avgChurn;


    public MethodMetrics(){
        this.isBuggy = false;
        this.size = 0;
        this.LOC = 0;
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
    }
    public void setStmtAddedMetrics(int total, int max, double avg) {
        this.totalStmtAdded = total;
        this.maxStmtAdded = max;
        this.avgStmtAdded = avg;
    }

    public void setStmtDeletedMetrics(int total, int max, double avg) {
        this.totalStmtDeleted = total;
        this.maxStmtDeleted = max;
        this.avgStmtDeleted = avg;
    }

    public void setChurnMetrics(int total, int max, double avg) {
        this.totalChurn = total;
        this.maxChurn = max;
        this.avgChurn = avg;
    }

}
