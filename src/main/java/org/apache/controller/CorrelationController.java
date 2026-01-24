package org.apache.controller;

import org.apache.commons.math3.distribution.TDistribution;
import org.apache.commons.math3.stat.correlation.SpearmansCorrelation;
import tech.tablesaw.api.*;


import java.util.*;

public class CorrelationController {

    private final Table dataset;
    private static final String BUG_COLUMN = "Bugginess";
    private static final String RELEASE_COLUMN = "ReleaseID"; // Preso da AFMethodFinder

    public CorrelationController(Table dataset) {
        this.dataset = dataset;

    }

    // Feature actionable di riferimento
    private static final Set<String> ACTIONABLE_FEATURES = Set.of(
            "NumberOfCodeSmells", "CycloComplexity", "LOC",
            "CognitiveComplexity", "ParameterCount", "NestingDepth"
    );

    public record FeatureValue(String methodName, double value) { }


    /**
     * Record esteso con Rho di Spearman e P-Value (Concetto da SpearmanWithPValue)
     */
    public record FeatureCorrelation(String featureName, double correlation, double pValue) {
        @Override
        public String toString() {
            return String.format("%s -> Rho: %.3f (p-val: %.4f)", featureName, correlation, pValue);
        }
    }

    /**
     * Calcola la correlazione di Spearman e la significatività (Concetto da SpearmanCalculator)
     */
    public List<FeatureCorrelation> computeCorrelations() {
        List<FeatureCorrelation> correlations = new ArrayList<>();

        if (!dataset.columnNames().contains(BUG_COLUMN)) {
            throw new IllegalArgumentException("Colonna Bugginess mancante.");
        }

        // Preparazione target (1.0 per yes, 0.0 per no)
        StringColumn bugginessStr = dataset.stringColumn(BUG_COLUMN);
        double[] bugValues = new double[bugginessStr.size()];
        for (int i = 0; i < bugginessStr.size(); i++) {
            bugValues[i] = "yes".equalsIgnoreCase(bugginessStr.get(i)) ? 1.0 : 0.0;
        }

        for (String colName : dataset.columnNames()) {
            if (colName.equalsIgnoreCase(BUG_COLUMN) || colName.equalsIgnoreCase(RELEASE_COLUMN)) continue;

            // Filtro feature actionable
            if (!isActionable(colName)) continue;

            if (dataset.column(colName) instanceof NumericColumn<?> numCol) {
                double[] featureValues = numCol.asDoubleArray();

                // Calcolo Spearman + P-Value
                FeatureCorrelation result = calculateSpearman(colName, featureValues, bugValues);
                correlations.add(result);
            }
        }

        // Ordina per Rho decrescente
        correlations.sort((a, b) -> Double.compare(Math.abs(b.correlation()), Math.abs(a.correlation())));
        return correlations;
    }

    /**
     * Implementazione della statistica di Spearman con p-value (Concetto da SpearmanWithPValue)
     */
    private FeatureCorrelation calculateSpearman(String name, double[] x, double[] y) {
        SpearmansCorrelation sc = new SpearmansCorrelation();
        double rho = sc.correlation(x, y);

        // Calcolo p-value tramite distribuzione T di Student
        int n = x.length;
        double t = rho * Math.sqrt((n - 2.0) / (1.0 - rho * rho));
        TDistribution tDist = new TDistribution(n - 2.0);
        double pValue = 2.0 * (1.0 - tDist.cumulativeProbability(Math.abs(t)));

        return new FeatureCorrelation(name, rho, pValue);
    }

    public FeatureValue findBuggyMethodWithMaxFeature(String featureName) {
        StringColumn bugColumn = dataset.stringColumn(BUG_COLUMN);
        NumericColumn<?> featureColumn = (NumericColumn<?>) dataset.column(featureName);

        StringColumn methodNameColumn = dataset.stringColumn("MethodName");
        //TextColumn methodNameColumn = dataset.textColumn("MethodName");

        // Identifica l'ultima release
        String lastRelease = "";
        if (dataset.columnNames().contains(RELEASE_COLUMN)) {
            StringColumn uniqueReleases = dataset.column(RELEASE_COLUMN).unique().asStringColumn();
            uniqueReleases.sortDescending();
            if (!uniqueReleases.isEmpty()) {
                lastRelease = uniqueReleases.get(0);
            }
        }

        double maxValue = Double.NEGATIVE_INFINITY;
        int bestIndex = -1;

        for (int i = 0; i < dataset.rowCount(); i++) {
            boolean isBuggy = "yes".equalsIgnoreCase(bugColumn.get(i));
            boolean isLastRelease = lastRelease.isEmpty() || lastRelease.equalsIgnoreCase(dataset.column(RELEASE_COLUMN).getString(i));

            if (isBuggy && isLastRelease) {
                double val = featureColumn.getDouble(i);
                if (val > maxValue) {
                    maxValue = val;
                    bestIndex = i;
                }
            }
        }

        if (bestIndex == -1) return new FeatureValue("Nessun metodo trovato", Double.NaN);

        return new FeatureValue(String.valueOf(methodNameColumn.get(bestIndex)), maxValue);
    }


    /**
     * Helper per matchare i nomi delle feature ignorando case e caratteri speciali
     */
    private boolean isActionable(String colName) {
        String normalized = colName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        return ACTIONABLE_FEATURES.stream()
                .anyMatch(f -> f.toLowerCase().equals(normalized));
    }

    public FeatureCorrelation getBestFeature() {
        List<FeatureCorrelation> list = computeCorrelations();
        return list.isEmpty() ? null : list.get(0);
    }
}