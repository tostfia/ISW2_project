package org.apache.controller;

import org.apache.logging.CollectLogger;
import org.apache.model.AnalyzedClass;
import org.apache.model.Release;

import org.apache.model.Ticket;
import org.apache.utilities.CsvWriter;

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

    private void processing() throws IOException, URISyntaxException, GitAPIException {
        // --- FASE 1: JIRA & SETUP INIZIALE GIT (leggeri) ---
        JiraController jiraController = performJiraAnalysis();
        List<Release> releases = jiraController.getRealeases();
        List<Ticket> tickets = jiraController.getFixedTickets();

        GitController gitController = new GitController(targetName, project, releases);
        gitController.buildCommitHistory(); // Carica i metadati dei commit (ancora necessario)
        gitController.setTickets(tickets);
        gitController.findBuggyFiles();
        logger.info(threadIdentity + "-Analisi con SZZ");
        gitController.findAllBugIntroducingCommits(); // Pre-calcola le informazioni sui bug
        gitController.buildFileCommitHistoryMap();

        logger.info(threadIdentity + "-Analisi Git completata.");

        // --- FASE 2 & 3 & 4: CICLO PER RELEASE (Memory-Safe) ---
        logger.info("Avvio calcolo metriche e scrittura per ogni release...");
        String csvFileName = targetName + "_dataset.csv";
        try (CsvWriter csvWriter = new CsvWriter(csvFileName)) {
            csvWriter.writeHeader();
            logger.info("CSV writer inizializzato: " + csvFileName);

            for (Release release : releases) {
                String releaseMsg = "Processando la release: " + release.getReleaseID();
                logger.info(releaseMsg);

                // 1. Estrai le classi SOLO per la release corrente
                List<AnalyzedClass> classesForThisRelease = gitController.getClassesForRelease(release);

                if (classesForThisRelease.isEmpty()) {
                    logger.info("Nessuna classe trovata per la release " + release.getReleaseID() + ", saltando.");
                    continue;
                }

                // 2. Etichetta la bugginess per questo sottoinsieme di classi
                gitController.labelBuggynessWithSZZ(classesForThisRelease);

                // 3. Calcola le metriche SOLO per questo sottoinsieme
                MetricsController metricsController = new MetricsController(releases, classesForThisRelease, gitController, targetName);
                metricsController.processMetrics();

                // 4. Scrivi i risultati e libera la memoria IMMEDIATAMENTE
                for (AnalyzedClass analyzedClass : classesForThisRelease) {
                    csvWriter.writeResultsForClass(analyzedClass);

                }
                logger.info("Scrittura completata per la release: " + release.getReleaseID());

                // La lista `classesForThisRelease` verrà distrutta dal garbage collector
                // alla fine di questa iterazione del loop, liberando memoria per la prossima release.
            }
        }

        // --- FASE 5: COMPLETAMENTO ---
        logger.info("Fase 5: Analisi completata.");
        gitController.closeRepo();
        logger.info("Repository chiuso correttamente.");
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