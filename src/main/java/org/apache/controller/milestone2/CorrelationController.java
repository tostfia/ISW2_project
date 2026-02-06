package org.apache.controller.milestone2;

import org.apache.logging.Printer;
import tech.tablesaw.api.*;
import org.apache.commons.math3.stat.correlation.SpearmansCorrelation;

import java.util.*;

public class CorrelationController {

    private final Table dataset;
    private static final String BUG_COLUMN = "Bugginess";
    private static final String RELEASE_COLUMN = "ReleaseID";

    private static final Set<String> ACTIONABLE_FEATURES = Set.of(
            "NumberOfCodeSmells", "CycloComplexity", "LOC",
            "CognitiveComplexity", "ParameterCount", "NestingDepth"
    );

    public CorrelationController(Table dataset) {
        this.dataset = dataset;
    }

    /** Record per rappresentare valore feature di un metodo */
    public record FeatureValue(String methodName, double value, int index) { }

    /** Record per correlazione + p-value */
    public record FeatureCorrelation(String featureName, double correlation) { }

    /**
     * Calcola Spearman per tutte le feature numeriche
     * e salva ranking completo in CSV con colonna Actionable
     */
    public List<FeatureCorrelation> computeAndSaveFullRanking(String projectName) {
        List<FeatureCorrelation> ranking = new ArrayList<>();

        if (!dataset.columnNames().contains(BUG_COLUMN)) {
            throw new IllegalArgumentException("Colonna Bugginess mancante.");
        }

        // Prepara target: 1 = yes, 0 = no
        StringColumn bugginessStr = dataset.stringColumn(BUG_COLUMN);
        double[] bugValues = new double[bugginessStr.size()];
        for (int i = 0; i < bugginessStr.size(); i++) {
            bugValues[i] = "yes".equalsIgnoreCase(bugginessStr.get(i)) ? 1.0 : 0.0;
        }

        // Calcola Spearman per tutte le feature numeriche
        for (String colName : dataset.columnNames()) {
            if (colName.equalsIgnoreCase(BUG_COLUMN) || colName.equalsIgnoreCase(RELEASE_COLUMN)) continue;

            if (dataset.column(colName) instanceof NumericColumn<?> numCol) {
                double[] featureValues = numCol.asDoubleArray();
                FeatureCorrelation fc = calculateSpearman(colName, featureValues, bugValues);
                ranking.add(fc);
            }
        }

        // Ordina per rho assoluto decrescente
        ranking.sort((a, b) -> Double.compare(Math.abs(b.correlation()), Math.abs(a.correlation())));

        // Salva CSV
        try {
            IntColumn rankCol = IntColumn.create("Rank");
            StringColumn featureCol = StringColumn.create("Feature");
            DoubleColumn rhoCol = DoubleColumn.create("SpearmanRho");
            BooleanColumn actionableCol = BooleanColumn.create("Actionable");

            for (int i = 0; i < ranking.size(); i++) {
                FeatureCorrelation fc = ranking.get(i);
                rankCol.append(i + 1);
                featureCol.append(fc.featureName());
                rhoCol.append(fc.correlation());
                actionableCol.append(isActionable(fc.featureName()));
            }

            Table table = Table.create("Full_Feature_Ranking")
                    .addColumns(rankCol, featureCol, rhoCol, actionableCol);

            String path = "output/" + projectName + "_full_feature_ranking.csv";
            table.write().csv(path);
            Printer.printGreen("Ranking completo salvato in: " + path + "\n");

        } catch (Exception e) {
            Printer.errorPrint("Errore nel salvataggio del ranking completo: " + e.getMessage());
        }

        return ranking;
    }

    /**
     * Trova la feature massima per un metodo buggy
     * Limita la ricerca solo alle feature azionabili
     */
    public FeatureValue findBuggyMethodWithMaxFeature(String featureName) {
        if (!isActionable(featureName)) {
            return new FeatureValue("Nessun metodo trovato", Double.NaN, -1);
        }

        StringColumn bugColumn = dataset.stringColumn(BUG_COLUMN);
        NumericColumn<?> featureColumn = (NumericColumn<?>) dataset.column(featureName);
        TextColumn methodNameColumn = dataset.textColumn("MethodName");
        //StringColumn methodNameColumn = dataset.stringColumn("MethodName");

        String lastRelease = "";
        if (dataset.columnNames().contains(RELEASE_COLUMN)) {
            StringColumn uniqueReleases = dataset.column(RELEASE_COLUMN).unique().asStringColumn();
            uniqueReleases.sortDescending();
            if (!uniqueReleases.isEmpty()) lastRelease = uniqueReleases.get(0);
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

        if (bestIndex == -1) return new FeatureValue("Nessun metodo trovato", Double.NaN, -1);
        return new FeatureValue(String.valueOf(methodNameColumn.get(bestIndex)), maxValue, bestIndex);
    }

    /** Calcola Spearman rho + p-value tramite distribuzione t di Student */
    private FeatureCorrelation calculateSpearman(String name, double[] x, double[] y) {
        SpearmansCorrelation sc = new SpearmansCorrelation();
        double rho = sc.correlation(x, y);



        return new FeatureCorrelation(name, rho);
    }

    /** Verifica se una feature è azionabile */
    public boolean isActionable(String colName) {
        String normalized = colName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        return ACTIONABLE_FEATURES.stream()
                .anyMatch(f -> f.toLowerCase().equals(normalized));
    }

    /** Restituisce la feature azionabile con rho massimo */
    public FeatureCorrelation getBestFeature() {
        List<FeatureCorrelation> actionableRanking = computeAndSaveFullRanking("temp").stream()
                .filter(fc -> isActionable(fc.featureName()))
                .filter(fc -> fc.correlation() > 0)  // ← AGGIUNGI QUESTO!
                .toList();

        if (actionableRanking.isEmpty()) {
            Printer.errorPrint("Nessuna feature azionabile con correlazione positiva trovata!");
            return null;
        }

        FeatureCorrelation best = actionableRanking.getFirst();
        Printer.printlnGreen("Feature selezionata: " + best.featureName() +
                " (ρ = " + best.correlation() + ")");
        return best;
    }
}
