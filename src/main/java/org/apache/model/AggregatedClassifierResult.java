package org.apache.model;


import lombok.Getter;
import lombok.Setter;

@Getter
public class AggregatedClassifierResult {
    private final String project;
    private final String classifierName;
    private final String featureSelection;
    private final String balancing;
    private final String costSensitive;
    private double avgPrecision;
    private double avgRecall;
    private double avgAreaUnderROC;
    private double avgKappa;
    private double avgExpectedCost;
    private int numberOfRuns;// Numero di iterazioni su cui è basata la media
    @Setter
    private String modelFilePath;

    // Costruttore
    public AggregatedClassifierResult(String project,String classifierName, String featureSelection, String balancing, String costSensitive, String modelFilePath) {
        this.classifierName = classifierName;
        this.featureSelection = featureSelection;
        this.balancing = balancing;
        this.costSensitive = costSensitive;
        this.avgPrecision = 0.0;
        this.avgRecall = 0.0;
        this.avgAreaUnderROC = 0.0;
        this.avgKappa = 0.0;
        this.avgExpectedCost = 0.0;
        this.numberOfRuns = 0;
        this.project = project;
        this.modelFilePath =modelFilePath;
    }

    // Metodo per aggiornare le medie con i risultati di una nuova iterazione
    public void addRunResult(double precision, double recall, double areaUnderROC, double kappa, double expectedCost) {
        this.avgPrecision = (this.avgPrecision * numberOfRuns + precision) / (numberOfRuns + 1);
        this.avgRecall = (this.avgRecall * numberOfRuns + recall) / (numberOfRuns + 1);
        this.avgAreaUnderROC = (this.avgAreaUnderROC * numberOfRuns + areaUnderROC) / (numberOfRuns + 1);
        this.avgKappa = (this.avgKappa * numberOfRuns + kappa) / (numberOfRuns + 1);
        this.avgExpectedCost = (this.avgExpectedCost * numberOfRuns + expectedCost) / (numberOfRuns + 1);
        this.numberOfRuns++;
    }



    @Override
    public String toString() {
        return "Classifier: " + classifierName +
                ", Feature Selection: " + featureSelection +
                ", Balancing: " + balancing +
                ", Cost Sensitive: " + costSensitive +
                ", Avg Expected Cost: " + String.format("%.2f", avgExpectedCost) +
                ", Avg Recall: " + String.format("%.2f", avgRecall) +
                ", Avg AUC: " + String.format("%.2f", avgAreaUnderROC) +
                " (based on " + numberOfRuns + " runs)";
    }


}
