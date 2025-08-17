package org.apache.controller;

import org.apache.logging.CollectLogger;
import org.apache.model.AnalyzedClass;
import org.apache.model.Release;

import org.apache.model.Ticket;
import org.apache.utilities.writer.CsvWriter;

import org.apache.utilities.writer.HistoricalDataWriter;
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




    public ProcessController(int threadId, CountDownLatch latch, String targetName, String project) {
        this.targetName = targetName;
        this.project = project;
        this.latch = latch;
        this.threadIdentity = "Thread-" + threadId + " (" + targetName + ")";


    }

    @Override
    public void run() {
        long overallStatTime = System.currentTimeMillis();
        String startMsg = threadIdentity + " avviato";
        logger.info(startMsg);
        try {
            processing();
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
            runHistoricalAnalysis();
        } else { // currentMode == AnalysisMode.BUILD_FINAL_DATASET
            runFinalDatasetBuild();
        }
    }

    /**
     * [NUOVO METODO]
     * Esegue la Fase 1: analisi pesante di Jira e Git, calcolo delle metriche storiche
     * e salvataggio dei risultati in un CSV intermedio.
     */
    private void runHistoricalAnalysis() throws IOException, URISyntaxException, GitAPIException {
        logger.info(threadIdentity + "- ESECUZIONE FASE 1: ANALISI STORICA...");

        // --- 1. Analisi Jira e Git (il tuo codice esistente) ---
        JiraController jiraController = performJiraAnalysis();
        List<Release> releases = jiraController.getRealeases(); // Tutte le release
        List<Ticket> tickets = jiraController.getFixedTickets();

        GitController gitController = new GitController(targetName, project, releases);
        gitController.setTickets(tickets);
        gitController.buildCommitHistory(); // Analizza TUTTA la storia
        gitController.buildFileCommitHistoryMap();
        gitController.findAllBugIntroducingCommits();

        // --- 2. Scrittura del CSV intermedio usando il nuovo writer ---
        String csvFileName = targetName + "_historical_data.csv";
        try (HistoricalDataWriter writer = new HistoricalDataWriter(csvFileName)) {
            writer.writeHeader();

            for (Release release : releases) {
                logger.info(threadIdentity + " - Processando dati storici per release: " + release.getReleaseID());

                List<AnalyzedClass> classes = gitController.getClassesForRelease(release);
                gitController.labelBuggynessWithSZZ(classes);

                // Usa un MetricsController per calcolare le metriche storiche
                // (Assicurati che MetricsController abbia un metodo specializzato o usa quello completo)
                MetricsController metricsController = new MetricsController(classes, gitController);
                // Questa è la chiamata a TUTTI i calcoli, lenti e veloci.
                // In questa fase ha senso, perché dobbiamo popolare tutti i dati.
                metricsController.processMetrics();

                // Salva i risultati di questa release
                writer.writeResults(classes);
            }
        }

        gitController.closeRepo();
        logger.info(threadIdentity + "- FASE 1 COMPLETATA. File dati storici creato: " + csvFileName);
    }

    /**
     * [NUOVO METODO - Per ora vuoto, lo svilupperemo dopo]
     * Esegue la Fase 2: lettura del CSV intermedio, calcolo delle metriche statiche
     * e scrittura del CSV finale.
     */
    private void runFinalDatasetBuild() throws IOException {
        logger.info(threadIdentity + "- ESECUZIONE FASE 2: COSTRUZIONE DATASET FINALE...");
        // Per ora, lasciamo questo metodo vuoto. Lo implementeremo in un secondo momento.
        // Qui andrà la logica per:
        // 1. Leggere il file "_historical_data.csv".
        // 2. Usare un GitController "leggero" per prendere il codice sorgente.
        // 3. Usare un MetricsController che calcola solo le metriche statiche.
        // 4. Usare un FinalCsvWriter per combinare e scrivere l'output finale.
        logger.info(threadIdentity + "- FASE 2 COMPLETATA.");
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