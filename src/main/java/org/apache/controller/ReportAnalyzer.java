package org.apache.controller;

import org.apache.logging.CollectLogger;
import org.apache.model.AggregatedClassifierResult;
import tech.tablesaw.api.Row;
import tech.tablesaw.api.Table;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;

public class ReportAnalyzer {

    private final String projectName;
    private final String reportFilePath;
    private final String outputDir;
    private static final Logger logger = CollectLogger.getInstance().getLogger();

    // Pesi configurabili
    private final double weightFalsePositive;
    private final double weightFalseNegative;

    // Pesi configurabili per composite score
    private double weightAUC = 0.3;
    private double weightPrecision = 0.25;
    private double weightRecall = 0.25;
    private double weightKappa = 0.2;

    public ReportAnalyzer(String projectName) {
        this(projectName, 1.0, 10.0); // default: FN pesa 10 volte FP
    }

    public ReportAnalyzer(String projectName, double weightFP, double weightFN) {
        this.projectName = projectName;
        this.weightFalsePositive = weightFP;
        this.weightFalseNegative = weightFN;

        this.outputDir = "output" + File.separator + "results" + File.separator + projectName + File.separator;
        // Assicurati che la directory di output esista
         new File(outputDir);

        this.reportFilePath = outputDir + projectName + "_report.csv";
    }

    // Setter opzionali per personalizzare i pesi del composite score
    public void setCompositeWeights(double auc, double precision, double recall, double kappa) {
        this.weightAUC = auc;
        this.weightPrecision = precision;
        this.weightRecall = recall;
        this.weightKappa = kappa;
    }

    /** Expected Cost */
    public void chooseBestClassifierByExpectedCost() {
        AggregatedClassifierResult bestClassifier = getBestClassifier("EXPECTED_COST");
        if (bestClassifier != null) {
            logger.info("Miglior Classificatore Trovato (minimo Expected Cost):");
            logger.info(bestClassifier.toString());
            saveBestClassifierToCSV(bestClassifier, "EXPECTED_COST", bestClassifier.getAvgExpectedCost());
        } else {
            logger.warning("Nessun miglior classificatore trovato per Expected Cost.");
        }
    }

    /** AUC */
    public void chooseBestClassifierByAUC() {
        AggregatedClassifierResult bestClassifier = getBestClassifier("AUC");
        if (bestClassifier != null) {
            logger.info("Miglior Classificatore Trovato (massima AUC):");
            logger.info(bestClassifier.toString());
            saveBestClassifierToCSV(bestClassifier, "AUC", bestClassifier.getAvgAreaUnderROC());
        } else {
            logger.warning("Nessun miglior classificatore trovato per AUC.");
        }
    }

    /** Kappa */
    public void chooseBestClassifierByKappa() {
        AggregatedClassifierResult bestClassifier = getBestClassifier("KAPPA");
        if (bestClassifier != null) {
            logger.info("Miglior Classificatore Trovato (massimo Kappa):");
            logger.info(bestClassifier.toString());
            saveBestClassifierToCSV(bestClassifier, "KAPPA", bestClassifier.getAvgKappa());
        } else {
            logger.warning("Nessun miglior classificatore trovato per Kappa.");
        }
    }

    /** F1 */
    public void chooseBestClassifierByF1Score() {
        AggregatedClassifierResult bestClassifier = getBestClassifier("F1_SCORE");
        if (bestClassifier != null) {
            double precision = bestClassifier.getAvgPrecision();
            double recall = bestClassifier.getAvgRecall();
            double f1Score = (precision + recall == 0) ? 0 : 2 * (precision * recall) / (precision + recall);
            logger.info("Miglior Classificatore Trovato (massimo F1-Score: " +
                    String.format("%.4f", f1Score) + "):");
            logger.info(bestClassifier.toString());
            saveBestClassifierToCSV(bestClassifier, "F1_SCORE", f1Score);
        } else {
            logger.warning("Nessun miglior classificatore trovato per F1-Score.");
        }
    }

    /** Composite Score */
    public void chooseBestClassifierByCompositeScore() {
        AggregatedClassifierResult bestClassifier = getBestClassifier("COMPOSITE_SCORE");
        if (bestClassifier != null) {
            double compositeScore = (weightAUC * bestClassifier.getAvgAreaUnderROC()) +
                    (weightPrecision * bestClassifier.getAvgPrecision()) +
                    (weightRecall * bestClassifier.getAvgRecall()) +
                    (weightKappa * Math.max(0, bestClassifier.getAvgKappa()));
            logger.info("Miglior Classificatore Trovato (punteggio composito: " +
                    String.format("%.4f", compositeScore) + "):");
            logger.info(bestClassifier.toString());
            saveBestClassifierToCSV(bestClassifier, "COMPOSITE_SCORE", compositeScore);
        } else {
            logger.warning("Nessun miglior classificatore trovato per Composite Score.");
        }
    }

    /** Metodo interno generalizzato */
    public AggregatedClassifierResult getBestClassifier(String selectionCriteria) {
        Map<String, AggregatedClassifierResult> aggregatedResults = loadAndAggregateResults();
        if (aggregatedResults == null || aggregatedResults.isEmpty()) {
            return null;
        }

        Comparator<AggregatedClassifierResult> comparator;
        boolean maximize = true;

        switch (selectionCriteria.toUpperCase()) {
            case "EXPECTED_COST":
                comparator = Comparator.comparing(AggregatedClassifierResult::getAvgExpectedCost);
                maximize = false;
                break;
            case "AUC":
                comparator = Comparator.comparing(AggregatedClassifierResult::getAvgAreaUnderROC);
                break;
            case "KAPPA":
                comparator = Comparator.comparing(AggregatedClassifierResult::getAvgKappa);
                break;
            case "F1_SCORE":
                comparator = (r1, r2) -> {
                    double f1_r1 = (r1.getAvgPrecision() + r1.getAvgRecall() == 0) ? 0 :
                            2 * (r1.getAvgPrecision() * r1.getAvgRecall()) / (r1.getAvgPrecision() + r1.getAvgRecall());
                    double f1_r2 = (r2.getAvgPrecision() + r2.getAvgRecall() == 0) ? 0 :
                            2 * (r2.getAvgPrecision() * r2.getAvgRecall()) / (r2.getAvgPrecision() + r2.getAvgRecall());
                    return Double.compare(f1_r1, f1_r2);
                };
                break;
            case "COMPOSITE_SCORE":
                comparator = (r1, r2) -> {
                    double composite1 = (weightAUC * r1.getAvgAreaUnderROC()) +
                            (weightPrecision * r1.getAvgPrecision()) +
                            (weightRecall * r1.getAvgRecall()) +
                            (weightKappa * Math.max(0, r1.getAvgKappa()));
                    double composite2 = (weightAUC * r2.getAvgAreaUnderROC()) +
                            (weightPrecision * r2.getAvgPrecision()) +
                            (weightRecall * r2.getAvgRecall()) +
                            (weightKappa * Math.max(0, r2.getAvgKappa()));
                    return Double.compare(composite1, composite2);
                };
                break;
            default:
                logger.severe("Criterio non riconosciuto: " + selectionCriteria);
                return null;
        }

        return maximize ?
                aggregatedResults.values().stream().max(comparator).orElse(null) :
                aggregatedResults.values().stream().min(comparator).orElse(null);
    }

    /** Carica e aggrega risultati dal CSV */
    private Map<String, AggregatedClassifierResult> loadAndAggregateResults() {
        File reportFile = new File(reportFilePath);
        if (!reportFile.exists()) {
            logger.severe("ERRORE: File report non trovato: " + reportFilePath);
            return null;
        }

        Table reportTable;
        try {
            reportTable = Table.read().csv(reportFilePath);
        } catch (Exception e) {
            logger.severe("ERRORE: Impossibile leggere il CSV: " + e.getMessage());
            return null;
        }

        if (reportTable.isEmpty()) {
            logger.warning("ATTENZIONE: Il file report è vuoto: " + reportFilePath);
            return null;
        }

        Map<String, AggregatedClassifierResult> aggregatedResults = new HashMap<>();

        for (Row row : reportTable) {
            try {
                String dataset = row.getString("DATASET");
                String classifierName = row.getString("CLASSIFIER");
                String featureSelection = row.getString("FEATURE_SELECTION");
                String balancing = row.getString("BALANCING");
                String costSensitive = row.getString("COST_SENSITIVE");

                double precision = row.getDouble("PRECISION");
                double recall = row.getDouble("RECALL");
                double areaUnderROC = row.getDouble("AREA_UNDER_ROC");
                double kappa = row.getDouble("KAPPA");
                double falsePositives = row.getInt("FALSE_POSITIVES");
                double falseNegatives = row.getInt("FALSE_NEGATIVES");

                double expectedCost = (falsePositives * weightFalsePositive) +
                        (falseNegatives * weightFalseNegative);

                String configKey = dataset + "_" + classifierName + "_" + featureSelection + "_" +
                        balancing + "_" + costSensitive;

                aggregatedResults.putIfAbsent(configKey,
                        new AggregatedClassifierResult(dataset, classifierName, featureSelection,
                                balancing, costSensitive, ""));
                aggregatedResults.get(configKey).addRunResult(precision, recall,
                        areaUnderROC, kappa, expectedCost);
            } catch (Exception e) {
                logger.warning("Errore nel processare una riga del CSV: " + e.getMessage());
            }
        }

        logger.info("Caricati e aggregati " + aggregatedResults.size() + " configurazioni diverse");
        return aggregatedResults;
    }

    /**
     * Salva il miglior classificatore in un file CSV
     */
    private void saveBestClassifierToCSV(AggregatedClassifierResult bestClassifier,
                                         String selectionCriteria, double criteriaValue) {
        String outputFilePath = outputDir + projectName + "_best_classifier.csv";
        boolean isNewFile = !new File(outputFilePath).exists();

        try (FileWriter writer = new FileWriter(outputFilePath, true)) { // append mode

            // Scrivi l'header solo se è un nuovo file
            if (isNewFile) {
                // Header corretto senza le colonne STD_
                writer.append("TIMESTAMP,PROJECT,SELECTION_CRITERIA,CRITERIA_VALUE,CLASSIFIER,")
                        .append("FEATURE_SELECTION,BALANCING,COST_SENSITIVE,RUN_COUNT,")
                        .append("AVG_PRECISION,AVG_RECALL,AVG_AUC,AVG_KAPPA,AVG_EXPECTED_COST")
                        .append("\n");
            }

            // Timestamp corrente
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            // Scrivi i dati del miglior classificatore
            writer.append(timestamp).append(",")
                    .append(projectName).append(",")
                    .append(selectionCriteria).append(",")
                    .append(String.format(Locale.ENGLISH, "%.6f", criteriaValue)).append(",")
                    .append(bestClassifier.getClassifierName()).append(",")
                    .append(bestClassifier.getFeatureSelection()).append(",")
                    .append(bestClassifier.getBalancing()).append(",")
                    .append(bestClassifier.getCostSensitive()).append(",")
                    .append(String.valueOf(bestClassifier.getNumberOfRuns())).append(",")
                    .append(String.format(Locale.ENGLISH, "%.6f", bestClassifier.getAvgPrecision())).append(",")
                    .append(String.format(Locale.ENGLISH, "%.6f", bestClassifier.getAvgRecall())).append(",")
                    .append(String.format(Locale.ENGLISH, "%.6f", bestClassifier.getAvgAreaUnderROC())).append(",")
                    .append(String.format(Locale.ENGLISH, "%.6f", bestClassifier.getAvgKappa())).append(",")
                    .append(String.format(Locale.ENGLISH, "%.6f", bestClassifier.getAvgExpectedCost()));

            writer.append("\n");

            logger.info("Risultato salvato in: " + outputFilePath);

        } catch (IOException e) {
            logger.severe("ERRORE: Impossibile salvare il risultato nel file CSV: " +
                    outputFilePath + " - " + e.getMessage());
        }
    }

    /**
     * Genera un file CSV con il ranking completo dei classificatori per una specifica metrica
     */
    public void saveCompleteRankingToCSV(String criteria) {
        Map<String, AggregatedClassifierResult> aggregatedResults = loadAndAggregateResults();
        if (aggregatedResults == null || aggregatedResults.isEmpty()) {
            logger.warning("Nessun dato disponibile per generare il ranking.");
            return;
        }

        List<AggregatedClassifierResult> rankedResults = new ArrayList<>(aggregatedResults.values());

        // Ordina in base ai criteri
        switch (criteria.toUpperCase()) {
            case "EXPECTED_COST":
                rankedResults.sort(Comparator.comparing(AggregatedClassifierResult::getAvgExpectedCost));
                break;
            case "AUC":
                rankedResults.sort(Comparator.comparing(AggregatedClassifierResult::getAvgAreaUnderROC).reversed());
                break;
            case "PRECISION":
                rankedResults.sort(Comparator.comparing(AggregatedClassifierResult::getAvgPrecision).reversed());
                break;
            case "RECALL":
                rankedResults.sort(Comparator.comparing(AggregatedClassifierResult::getAvgRecall).reversed());
                break;
            case "KAPPA":
                rankedResults.sort(Comparator.comparing(AggregatedClassifierResult::getAvgKappa).reversed());
                break;
            case "F1_SCORE": // Nuovo caso per F1-Score
                rankedResults.sort((r1, r2) -> {
                    double f1_r1 = (r1.getAvgPrecision() + r1.getAvgRecall() == 0) ? 0 :
                            2 * (r1.getAvgPrecision() * r1.getAvgRecall()) / (r1.getAvgPrecision() + r1.getAvgRecall());
                    double f1_r2 = (r2.getAvgPrecision() + r2.getAvgRecall() == 0) ? 0 :
                            2 * (r2.getAvgPrecision() * r2.getAvgRecall()) / (r2.getAvgPrecision() + r2.getAvgRecall());
                    return Double.compare(f1_r2, f1_r1); // Ordinamento decrescente
                });
                break;
            case "COMPOSITE_SCORE": // Nuovo caso per Composite Score
                rankedResults.sort((r1, r2) -> {
                    double composite1 = (weightAUC * r1.getAvgAreaUnderROC()) +
                            (weightPrecision * r1.getAvgPrecision()) +
                            (weightRecall * r1.getAvgRecall()) +
                            (weightKappa * Math.max(0, r1.getAvgKappa()));
                    double composite2 = (weightAUC * r2.getAvgAreaUnderROC()) +
                            (weightPrecision * r2.getAvgPrecision()) +
                            (weightRecall * r2.getAvgRecall()) +
                            (weightKappa * Math.max(0, r2.getAvgKappa()));
                    return Double.compare(composite2, composite1); // Ordinamento decrescente
                });
                break;
            default:
                logger.warning("Criterio di ranking non riconosciuto: " + criteria);
                return;
        }

        String outputFilePath = outputDir + projectName + "_ranking_" + criteria.toLowerCase() + ".csv";

        try (FileWriter writer = new FileWriter(outputFilePath)) {
            // Header
            writer.append("RANK,PROJECT,CLASSIFIER,FEATURE_SELECTION,BALANCING,COST_SENSITIVE,RUN_COUNT,")
                    .append("AVG_PRECISION,AVG_RECALL,AVG_AUC,AVG_KAPPA,AVG_EXPECTED_COST\n");

            // Dati
            int rank = 1;
            for (AggregatedClassifierResult result : rankedResults) {
                writer.append(String.valueOf(rank)).append(",")
                        .append(String.valueOf(result.getProject())).append(",")
                        .append(result.getClassifierName()).append(",")
                        .append(result.getFeatureSelection()).append(",")
                        .append(result.getBalancing()).append(",")
                        .append(result.getCostSensitive()).append(",")
                        .append(String.valueOf(result.getNumberOfRuns())).append(",")
                        .append(String.format(Locale.ENGLISH, "%.6f", result.getAvgPrecision())).append(",")
                        .append(String.format(Locale.ENGLISH, "%.6f", result.getAvgRecall())).append(",")
                        .append(String.format(Locale.ENGLISH, "%.6f", result.getAvgAreaUnderROC())).append(",")
                        .append(String.format(Locale.ENGLISH, "%.6f", result.getAvgKappa())).append(",")
                        .append(String.format(Locale.ENGLISH, "%.6f", result.getAvgExpectedCost()))
                        .append("\n");
                rank++;
            }

            logger.info("Ranking completo salvato in: " + outputFilePath);

        } catch (IOException e) {
            logger.severe("ERRORE: Impossibile salvare il ranking nel file CSV: " +
                    outputFilePath + " - " + e.getMessage());
        }
    }

    /**
     * Metodo convenience per analizzare tutti i criteri e salvare tutti i risultati
     */
    public void analyzeAllCriteriaAndSave() {
        logger.info("Inizio analisi completa per progetto: " + projectName);

        // Analizza per tutti i criteri per scegliere il "migliore"
        chooseBestClassifierByExpectedCost(); // Inserito il richiamo per Expected Cost
        chooseBestClassifierByAUC();
        chooseBestClassifierByKappa();
        chooseBestClassifierByF1Score();
        chooseBestClassifierByCompositeScore();

        // Genera i ranking completi per tutti i criteri rilevanti
        saveCompleteRankingToCSV("AUC");
        saveCompleteRankingToCSV("EXPECTED_COST");
        saveCompleteRankingToCSV("KAPPA");
        saveCompleteRankingToCSV("PRECISION");
        saveCompleteRankingToCSV("RECALL"); // Aggiunto per completezza
        saveCompleteRankingToCSV("F1_SCORE"); // Nuovo ranking per F1-Score
        saveCompleteRankingToCSV("COMPOSITE_SCORE"); // Nuovo ranking per Composite Score


        logger.info("Analisi completa terminata. Tutti i risultati sono stati salvati.");
    }
}
