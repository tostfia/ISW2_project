package org.apache.model;

import lombok.Getter;

/**
 * Classe che rappresenta i risultati delle predizioni per un dataset
 */
@Getter
public class PredictionResult {
    // Getters
    private final int actualBuggy;
    private final int predictedBuggy;
    private final int correctlyPredictedBuggy;
    private final int actualNonBuggy;
    private final int predictedNonBuggy;
    private final int correctlyPredictedNonBuggy;

    // Costruttore per compatibilità con il codice esistente
    public PredictionResult(int actualBuggy, int predictedBuggy, int correctlyPredictedBuggy) {
        this.actualBuggy = actualBuggy;
        this.predictedBuggy = predictedBuggy;
        this.correctlyPredictedBuggy = correctlyPredictedBuggy;
        this.actualNonBuggy = 0;
        this.predictedNonBuggy = 0;
        this.correctlyPredictedNonBuggy = 0;
    }

    // Costruttore completo
    public PredictionResult(int actualBuggy, int predictedBuggy, int correctlyPredictedBuggy,
                            int actualNonBuggy, int predictedNonBuggy, int correctlyPredictedNonBuggy) {
        this.actualBuggy = actualBuggy;
        this.predictedBuggy = predictedBuggy;
        this.correctlyPredictedBuggy = correctlyPredictedBuggy;
        this.actualNonBuggy = actualNonBuggy;
        this.predictedNonBuggy = predictedNonBuggy;
        this.correctlyPredictedNonBuggy = correctlyPredictedNonBuggy;
    }

    public int getTotalInstances() {
        return actualBuggy + actualNonBuggy;
    }

    // Metriche derivate
    public double getPrecisionBuggy() {
        return predictedBuggy > 0 ? (double) correctlyPredictedBuggy / predictedBuggy : 0.0;
    }

    public double getRecallBuggy() {
        return actualBuggy > 0 ? (double) correctlyPredictedBuggy / actualBuggy : 0.0;
    }

    public double getAccuracy() {
        int totalInstances = getTotalInstances();
        return totalInstances > 0 ?
                (double) (correctlyPredictedBuggy + correctlyPredictedNonBuggy) / totalInstances : 0.0;
    }

    public double getF1ScoreBuggy() {
        double precision = getPrecisionBuggy();
        double recall = getRecallBuggy();
        return (precision + recall) > 0 ? 2 * (precision * recall) / (precision + recall) : 0.0;
    }

    @Override
    public String toString() {
        return String.format("PredictionResult{actualBuggy=%d, predictedBuggy=%d, correctBuggy=%d, " +
                        "actualNonBuggy=%d, predictedNonBuggy=%d, correctNonBuggy=%d, " +
                        "accuracy=%.3f, precision=%.3f, recall=%.3f, f1=%.3f}",
                actualBuggy, predictedBuggy, correctlyPredictedBuggy,
                actualNonBuggy, predictedNonBuggy, correctlyPredictedNonBuggy,
                getAccuracy(), getPrecisionBuggy(), getRecallBuggy(), getF1ScoreBuggy());
    }
}
