package org.apache.controller;

import org.apache.logging.CollectLogger;
import org.apache.model.AnalyzedClass;
import org.apache.model.Release;

import org.apache.utilities.writer.CsvWriter;

import org.apache.utilities.writer.HistoricalDataWriter;
import org.apache.utilities.enums.AnalysisMode;
import org.eclipse.jgit.api.errors.GitAPIException;


import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class ProcessController implements Runnable {
    private final String targetName;
    private final String project;
    private final CountDownLatch latch;
    private final String threadIdentity;
    private static final Logger logger = CollectLogger.getInstance().getLogger();
    private final AnalysisMode mode;



    public ProcessController(int threadId, CountDownLatch latch, String targetName, String project, AnalysisMode mode) {
        this.targetName = targetName;
        this.project = project;
        this.latch = latch;
        this.threadIdentity = "Thread-" + threadId + " (" + targetName + ")";
        this.mode = mode;

    }

    @Override
    public void run() {
        long overallStatTime = System.currentTimeMillis();
        String startMsg = threadIdentity + " avviato";
        logger.info(startMsg);
        try {
            processing(this.mode);
        } catch (Exception e) {
            java.io.StringWriter sw = new java.io.StringWriter();
            java.io.PrintWriter pw = new java.io.PrintWriter(sw);
            e.printStackTrace(pw);
            String sStackTrace = sw.toString();

            String errorMessage = String.format("Errore critico in %s: %n%s", threadIdentity, sStackTrace);
            logger.severe(errorMessage);
        } finally {
            latch.countDown();
            long endOverallTime = System.currentTimeMillis();
            double elapsedSeconds = (endOverallTime - overallStatTime) / 1000.0;
            String finalMessage = String.format("%s completato. Tempo impiegato: %.2f secondi", threadIdentity, elapsedSeconds);
            logger.info(finalMessage);
        }
    }
    private void processing(AnalysisMode currentMode) throws IOException, URISyntaxException, GitAPIException {
        if (currentMode == AnalysisMode.ANALYZE_HISTORY) {
            // --- ESEGUI SOLO LA FASE 1 (LENTA) ---
            runHistoricalAnalysis();
        } else { // currentMode == AnalysisMode.BUILD_FINAL_DATASET
            // --- ESEGUI SOLO LA FASE 2 (VELOCE) ---
            runFinalDatasetBuild();
        }
    }

    /**
     * Contiene la logica per l'analisi storica e la scrittura del CSV intermedio.
     */
    private void runHistoricalAnalysis() throws IOException, URISyntaxException, GitAPIException {
        logger.info(threadIdentity + "- ESECUZIONE FASE 1: ANALISI STORICA...");

        // Esegui tutta l'analisi pesante di Jira e Git
        JiraController jiraController = performJiraAnalysis();
        List<Release> releases = jiraController.getRealeases();
        // ...
        GitController gitController = new GitController(targetName, project, releases);
        gitController.buildCommitHistory();
        gitController.buildFileCommitHistoryMap();
        gitController.findAllBugIntroducingCommits();

        // Scrivi il CSV intermedio
        String csvFileName = targetName + "_historical_data.csv";
        try (HistoricalDataWriter writer = new HistoricalDataWriter(csvFileName)) {
            writer.writeHeader();
            for (Release release : releases) {
                List<AnalyzedClass> classes = gitController.getClassesForRelease(release);
                gitController.labelBuggynessWithSZZ(classes);

                // Usa un MetricsController specializzato (o un metodo specializzato)
                MetricsController metricsController = new MetricsController(classes, gitController);
                metricsController.processClassLevelHistoricalMetrics(); // Metodo che calcola solo a livello di classe

                writer.writeResults(classes);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Contiene la logica per leggere i dati storici, calcolare le metriche statiche
     * e scrivere il CSV finale.
     */
    private void runFinalDatasetBuild() throws IOException, URISyntaxException, GitAPIException {
        logger.info(threadIdentity + "- ESECUZIONE FASE 2: COSTRUZIONE DATASET FINALE...");

        // 1. Carica i dati storici dal CSV intermedio
        Map<String, HistoricalData> historicalDataMap = loadHistoricalData(targetName + "_historical_data.csv");

        // 2. Usa GitController in modo "leggero"
        JiraController jiraController = performJiraAnalysis();
        List<Release> releases = jiraController.getRealeases();
        GitController gitController = new GitController(targetName, project, releases);
        // NON chiamare i metodi pesanti come buildCommitHistory, SZZ, etc.

        // 3. Scrivi il dataset finale
        String finalCsvFileName = targetName + "_final_dataset.csv";
        try (CsvWriter writer = new CsvWriter(finalCsvFileName)) {
            writer.writeHeader();
            for (Release release : releases) {
                List<AnalyzedClass> classes = gitController.getClassesForRelease(release);
                for (AnalyzedClass ac : classes) {
                    String key = release.getReleaseID() + ";" + ac.getClassName();
                    HistoricalData history = historicalDataMap.getOrDefault(key, ...);

                    // Usa un MetricsController che calcola solo le metriche a livello di metodo
                    MetricsController metricsController = new MetricsController(List.of(ac), gitController);
                    metricsController.processMethodLevelStaticMetrics(); // Metodo che calcola solo LOC, Cyclo, etc.

                    writer.writeResultsForClass(ac, history);
                }
            }
        }
    }







    private JiraController performJiraAnalysis() throws IOException, URISyntaxException {
        logger.info(threadIdentity + "-Avvio analisi Jira ...");
        JiraController jiraController = new JiraController(targetName);
        jiraController.injectRelease();
        jiraController.injectTickets();
        logger.info(threadIdentity + "-Analisi Jira completata.");
        return jiraController;
    }
}