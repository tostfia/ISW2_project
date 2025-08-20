package org.apache.controller;



import org.apache.logging.CollectLogger;
import org.apache.model.AggregatedClassifierResult;
import tech.tablesaw.api.Table;
import tech.tablesaw.api.Row;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;


public class ReportAnalyzer {

    private final String projectName;
    private final String reportFilePath;
    private final String outputDir;
    private static final Logger logger = CollectLogger.getInstance().getLogger();

    // Costanti per i pesi
    private static final double WEIGHT_FALSE_POSITIVE = 1.0;
    private static final double WEIGHT_FALSE_NEGATIVE = 10.0;

    public ReportAnalyzer(String projectName) {
        this.projectName = projectName;
        this.outputDir = "output" + File.separator + "results" + File.separator + projectName + File.separator;
        this.reportFilePath = outputDir + projectName + "_report.csv";

        // Crea la directory di output se non esiste
        new File(outputDir).mkdirs();
    }

    /**
     * **(NUOVO NOME)** Trova il miglior classificatore basato sul costo atteso minimo e restituisce il risultato.
     * Questo metodo non salva il risultato, serve per ottenere l'oggetto del BClassifier.
     */
    public void getBestClassifierByExpectedCost() { // CAMBIATO NOME e ora restituisce
        Map<String, AggregatedClassifierResult> aggregatedResults = loadAndAggregateResults();
        if (aggregatedResults == null || aggregatedResults.isEmpty()) {
            logger.severe("Nessun risultato aggregato disponibile per scegliere il miglior classificatore per costo atteso.");
            return;
        }

        AggregatedClassifierResult bestClassifier = aggregatedResults.values().stream()
                .min(Comparator.comparing(AggregatedClassifierResult::getAvgExpectedCost))
                .orElse(null);

        logger.info("Miglior Classificatore Trovato (minimo Expected Cost):");
        logger.info(bestClassifier.toString());
    }

    /**
     * Trova il miglior classificatore basato sull'AUC massima e salva il risultato.
     * (Ora chiama getBestClassifier generale per l'ottenimento)
     */
    public void chooseBestClassifierByAUC() {
        AggregatedClassifierResult bestClassifier = getBestClassifier("AUC"); // Chiamata al metodo generalizzato
        if (bestClassifier != null) {
            logger.info("Miglior Classificatore Trovato (massima AUC):");
            logger.info(bestClassifier.toString());
            saveBestClassifierToCSV(bestClassifier, "AUC", bestClassifier.getAvgAreaUnderROC());
        } else {
            logger.warning("Nessun miglior classificatore trovato per AUC.");
        }
    }

    /**
     * Trova il miglior classificatore basato sul Kappa massimo e salva il risultato.
     * (Ora chiama getBestClassifier generale per l'ottenimento)
     */
    public void chooseBestClassifierByKappa() {
        AggregatedClassifierResult bestClassifier = getBestClassifier("KAPPA"); // Chiamata al metodo generalizzato
        if (bestClassifier != null) {
            logger.info("Miglior Classificatore Trovato (massimo Kappa):");
            logger.info(bestClassifier.toString());
            saveBestClassifierToCSV(bestClassifier, "KAPPA", bestClassifier.getAvgKappa());
        } else {
            logger.warning("Nessun miglior classificatore trovato per Kappa.");
        }
    }

    /**
     * Trova il miglior classificatore usando un punteggio composito e salva il risultato.
     * (Ora chiama getBestClassifier generale per l'ottenimento)
     */
    public void chooseBestClassifierByCompositeScore() {
        AggregatedClassifierResult bestClassifier = getBestClassifier("COMPOSITE_SCORE"); // Chiamata al metodo generalizzato
        if (bestClassifier != null) {
            // Ricalcola il punteggio per il log/salvataggio, dato che getBestClassifier potrebbe non restituirlo
            double compositeScore = (0.3 * bestClassifier.getAvgAreaUnderROC()) +
                    (0.25 * bestClassifier.getAvgPrecision()) +
                    (0.25 * bestClassifier.getAvgRecall()) +
                    (0.2 * Math.max(0, bestClassifier.getAvgKappa()));
            logger.info("Miglior Classificatore Trovato (punteggio composito: " +
                    String.format("%.4f", compositeScore) + "):");
            logger.info(bestClassifier.toString());
            saveBestClassifierToCSV(bestClassifier, "COMPOSITE_SCORE", compositeScore);
        } else {
            logger.warning("Nessun miglior classificatore trovato per punteggio composito.");
        }
    }

    /**
     * Trova il miglior classificatore per F1-Score e salva il risultato.
     * (Ora chiama getBestClassifier generale per l'ottenimento)
     */
    public void chooseBestClassifierByF1Score() {
        AggregatedClassifierResult bestClassifier = getBestClassifier("F1_SCORE"); // Chiamata al metodo generalizzato
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


    /**
     * **(NUOVO METODO)** Restituisce il miglior classificatore aggregato basato sul criterio specificato.
     * Questo è il metodo generalizzato interno.
     */
    public AggregatedClassifierResult getBestClassifier(String selectionCriteria) { // Reso privato, chiamato dagli altri
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
                    double f1_r1 = (r1.getAvgPrecision() + r1.getAvgRecall() == 0) ? 0 : 2 * (r1.getAvgPrecision() * r1.getAvgRecall()) / (r1.getAvgPrecision() + r1.getAvgRecall());
                    double f1_r2 = (r2.getAvgPrecision() + r2.getAvgRecall() == 0) ? 0 : 2 * (r2.getAvgPrecision() * r2.getAvgRecall()) / (r2.getAvgPrecision() + r2.getAvgRecall());
                    return Double.compare(f1_r1, f1_r2);
                };
                break;
            case "COMPOSITE_SCORE":
                comparator = (r1, r2) -> {
                    double compositeScore1 = (0.3 * r1.getAvgAreaUnderROC()) + (0.25 * r1.getAvgPrecision()) + (0.25 * r1.getAvgRecall()) + (0.2 * Math.max(0, r1.getAvgKappa()));
                    double compositeScore2 = (0.3 * r2.getAvgAreaUnderROC()) + (0.25 * r2.getAvgPrecision()) + (0.25 * r2.getAvgRecall()) + (0.2 * Math.max(0, r2.getAvgKappa()));
                    return Double.compare(compositeScore1, compositeScore2);
                };
                break;
            default:
                logger.severe("Criterio di selezione del miglior classificatore non riconosciuto: " + selectionCriteria + ". Restituisco null.");
                return null; // O potresti voler lanciare un'eccezione
        }

        Optional<AggregatedClassifierResult> resultOptional = maximize ?
                aggregatedResults.values().stream().max(comparator) :
                aggregatedResults.values().stream().min(comparator);

        return resultOptional.orElse(null);
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
                        .append("\n"); // <<< L'HEADER DEVE FINIRE CON UN A CAPO
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
                    .append(String.format(Locale.ENGLISH, "%.6f", bestClassifier.getAvgExpectedCost())); // <<< NESSUNA VIRGOLA QUI, È L'ULTIMO CAMPO

            writer.append("\n"); // <<< QUESTA RIGA È FONDAMENTALE PER IL NUOVO A CAPO PER OGNI RIGA DI DATI!

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
                        .append(String.format(Locale.ENGLISH, "%.6f", result.getAvgPrecision())).append(",") // <<< Locale.ENGLISH
                        .append(String.format(Locale.ENGLISH, "%.6f", result.getAvgRecall())).append(",")    // <<< Locale.ENGLISH
                        .append(String.format(Locale.ENGLISH, "%.6f", result.getAvgAreaUnderROC())).append(",") // <<< Locale.ENGLISH
                        .append(String.format(Locale.ENGLISH, "%.6f", result.getAvgKappa())).append(",")      // <<< Locale.ENGLISH
                        .append(String.format(Locale.ENGLISH, "%.6f", result.getAvgExpectedCost())) // <<< Locale.ENGLISH, Rimosso virgola finale
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

        // Analizza per tutti i criteri
        getBestClassifierByExpectedCost();
        chooseBestClassifierByAUC();
        chooseBestClassifierByKappa();
        chooseBestClassifierByF1Score();
        chooseBestClassifierByCompositeScore();

        // Genera i ranking completi
        saveCompleteRankingToCSV("AUC");
        saveCompleteRankingToCSV("EXPECTED_COST");
        saveCompleteRankingToCSV("KAPPA");
        saveCompleteRankingToCSV("PRECISION");

        logger.info("Analisi completa terminata. Tutti i risultati sono stati salvati.");
    }

    /**
     * Carica e aggrega i risultati dal file CSV
     */
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
            logger.severe("ERRORE: Impossibile leggere il file CSV del report: " +
                    reportFilePath + " - " + e.getMessage());
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
                double truePositives = row.getInt("TRUE_POSITIVES");
                double falsePositives = row.getInt("FALSE_POSITIVES");
                double falseNegatives = row.getInt("FALSE_NEGATIVES");
                double trueNegatives = row.getInt("TRUE_NEGATIVES");

                // Calcola il costo atteso
                double expectedCost = (falsePositives * WEIGHT_FALSE_POSITIVE) +
                        (falseNegatives * WEIGHT_FALSE_NEGATIVE);

                String configKey = classifierName + "_" + featureSelection + "_" +
                        balancing + "_" + costSensitive;

                aggregatedResults.putIfAbsent(configKey,
                        new AggregatedClassifierResult(dataset,classifierName, featureSelection,
                                balancing, costSensitive));
                aggregatedResults.get(configKey).addRunResult(precision, recall,
                        areaUnderROC, kappa, expectedCost);
            } catch (Exception e) {
                logger.warning("Errore nel processare una riga del CSV: " + e.getMessage());

            }
        }

        logger.info("Caricati e aggregati " + aggregatedResults.size() + " configurazioni diverse");
        return aggregatedResults;
    }



}
