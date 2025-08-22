package org.apache.controller;



import java.util.ArrayList;
import java.util.List;

import org.apache.logging.CollectLogger;
import tech.tablesaw.api.*;



import java.util.*;
import java.util.logging.Logger;

public class CorrelationController {

    private final Table dataset;
    private static final String BUGGY= "Bugginess";

    public CorrelationController(Table dataset) {
        this.dataset = dataset;
    }

    // Definisce le feature actionable
    private static final Set<String> ACTIONABLE_FEATURES = Set.of(
            "NumberOfCodeSmells",
            "CycloComplexity",
            "LOC",
            "CognitiveComplexity",
            "ParameterCount",
            "NestingDepth"

    );


    /**
         * Classe interna per rappresentare una feature e la sua correlazione
         */

        public record FeatureCorrelation(String featureName, double correlation) {

        @Override
            public String toString() {
                return featureName + " -> " + correlation;
            }
        }

    /**
     * Calcola la correlazione di Pearson tra ogni feature numerica e la colonna 'bug'
     */
    public List<FeatureCorrelation> computeCorrelations() {
        List<FeatureCorrelation> correlations = new ArrayList<>();

        // Assicuriamoci che la colonna 'Bugginess' esista
        if (!dataset.columnNames().contains(BUGGY) || !(dataset.column(BUGGY) instanceof StringColumn)) {
            throw new IllegalArgumentException("La colonna 'Bugginess' deve esistere ed essere di tipo String (yes/no).");
        }

        StringColumn bugginessStr = dataset.stringColumn(BUGGY);

        // Converte yes/no in 1/0
        double[] bugValues = new double[bugginessStr.size()];
        for (int i = 0; i < bugginessStr.size(); i++) {
            bugValues[i] = "yes".equalsIgnoreCase(bugginessStr.get(i)) ? 1.0 : 0.0;
        }

        for (String colName : dataset.columnNames()) {
            boolean isBuggyCol = colName.equals(BUGGY);
            boolean isNotActionable = !ACTIONABLE_FEATURES.contains(colName);
            if (isBuggyCol || isNotActionable) continue;

            if (dataset.column(colName) instanceof NumericColumn<?> numCol) {
                double[] featureValues = numCol.asDoubleArray();
                double corr = pearsonCorrelation(featureValues, bugValues);
                correlations.add(new FeatureCorrelation(colName, corr));
            }
        }

        // Ordina per valore assoluto della correlazione (decrescente)
        correlations.sort((a, b) -> Double.compare(Math.abs(b.correlation()), Math.abs(a.correlation())));

        return correlations;
    }

    /**
     * Calcola la correlazione di Pearson tra due array
     */
    private double pearsonCorrelation(double[] x, double[] y) {
        if (x.length != y.length) throw new IllegalArgumentException("Gli array devono avere la stessa lunghezza");

        double meanX = Arrays.stream(x).average().orElse(0);
        double meanY = Arrays.stream(y).average().orElse(0);

        double numerator = 0;
        double sumSqX = 0;
        double sumSqY = 0;

        for (int i = 0; i < x.length; i++) {
            double dx = x[i] - meanX;
            double dy = y[i] - meanY;
            numerator += dx * dy;
            sumSqX += dx * dx;
            sumSqY += dy * dy;
        }

        double denominator = Math.sqrt(sumSqX * sumSqY);
        return denominator == 0 ? 0 : numerator / denominator;
    }

    /**
     * Ritorna la feature azionabile con correlazione assoluta più alta
     */
    public FeatureCorrelation getBestFeature() {
        List<FeatureCorrelation> list = computeCorrelations();
        return list.isEmpty() ? null : list.getFirst();
    }

    /**
     * Trova il metodo buggy con il valore più alto per la feature specificata
     * @param featureName nome della feature actionable
     * @return indice della riga del metodo con valore massimo per quella feature
     */
    public String findBuggyMethodWithMaxFeature(String featureName) {
        if (!ACTIONABLE_FEATURES.contains(featureName)) {
            throw new IllegalArgumentException("Feature non actionable: " + featureName);
        }

        if (!dataset.columnNames().contains("MethodName")) {
            throw new IllegalArgumentException("La colonna 'MethodName' deve esistere nel dataset.");
        }

        StringColumn bugColumn = dataset.stringColumn(BUGGY);
        NumericColumn<?> featureColumn = (NumericColumn<?>) dataset.column(featureName);
        var methodNameColumn = dataset.column("MethodName");

        double maxValue = Double.NEGATIVE_INFINITY;
        int bestMethodIndex = -1;

        for (int i = 0; i < dataset.rowCount(); i++) {
            // Considera solo i metodi buggy
            if ("yes".equalsIgnoreCase(bugColumn.get(i))) {
                double featureValue = featureColumn.getDouble(i);
                if (featureValue > maxValue) {
                    maxValue = featureValue;
                    bestMethodIndex = i;
                }
            }
        }

        return bestMethodIndex == -1 ? null : methodNameColumn.getString(bestMethodIndex);
    }


}
