package org.apache.model;

import lombok.Getter;

@Getter
public class PredictionResult {

    private final int actualBuggy;
    private final int predictedBuggy;
    private final int correctlyPredictedBuggy;

    public PredictionResult(int actualBuggy, int predictedBuggy, int correctlyPredictedBuggy) {
        this.actualBuggy = actualBuggy;
        this.predictedBuggy = predictedBuggy;
        this.correctlyPredictedBuggy = correctlyPredictedBuggy;
    }

}
