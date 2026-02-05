package org.apache.whatif;



public class PredictionSummary {

    public final String datasetName;
    public final int realBuggy;
    public final int predictedBuggy;

    public PredictionSummary(String name, int real, int predicted) {
        this.datasetName = name;
        this.realBuggy = real;
        this.predictedBuggy = predicted;
    }
}