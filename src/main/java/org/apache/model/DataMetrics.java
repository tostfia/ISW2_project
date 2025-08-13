package org.apache.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
public class DataMetrics {
    @Setter
    private boolean isBuggy;
    private int size;

    //Metriche di Complessità e Smells
    private long LOC; // Lines of Code
    private long cycloComplexity; // Cyclomatic Complexity
    private long numCodeSmells; // Numero di Code Smells
    private long cognitiveComplexity; // Cognitive Complexity
    private long nestingDepth; // Nesting Depth

    //Metriche di Processo/Storiche
    private long churn;
    private long maxChurn;
    private long avgChurn;
    private long methodHistory;
    private long numAuthors;


    public DataMetrics(){
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

    public static String getCsvHeade(){
        return "LOC,CyclomaticComplexity,NumCodeSmells,CognitiveComplexity,NestingDepth,Churn,MaxChurn,AvgChurn,MethodHistory,NumAuthors,Size,Buggy";

    }
    public List<Object> getAsCsvRow(){
        List<Object> row = new ArrayList<>();
        row.add(LOC);
        row.add(cycloComplexity);
        row.add(numCodeSmells);
        row.add(cognitiveComplexity);
        row.add(nestingDepth);
        row.add(churn);
        row.add(maxChurn);
        row.add(avgChurn);
        row.add(methodHistory);
        row.add(numAuthors);
        row.add(size);
        row.add(isBuggy ? 1 : 0); // Convert boolean to int for CSV
        return  row;
    }

}
