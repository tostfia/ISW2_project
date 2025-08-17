package org.apache.controller;

import org.apache.logging.CollectLogger;
import org.apache.model.AnalyzedClass;
import org.apache.model.Release;

import org.apache.model.Ticket;

import org.apache.utilities.writer.CsvWriter;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.json.JSONObject;


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
        logger.info(threadIdentity + "- ESECUZIONE FASE 1: ANALISI ...");
        JiraController jiraController = performJiraAnalysis();
        List<Release> releases = jiraController.getRealeases();
        List<Ticket> tickets = jiraController.getFixedTickets();
        GitController gitController = new GitController(targetName, project, releases);
        gitController.setTickets(tickets);
        // --- AGGIUNGI QUESTO LOG ---
        logger.info(threadIdentity + " - Passati " + tickets.size() + " ticket al GitController.");

        gitController.buildCommitHistory();
        gitController.findBuggyFiles();
        gitController.buildFileCommitHistoryMap();
        gitController.findAllBugIntroducingCommits();
        // --- 2. Scrittura del CSV
        String csvFileName = targetName + "_dataset.csv";
        try(CsvWriter writer = new CsvWriter(csvFileName, targetName)) {
            writer.writeHeader();
            for(Release release : releases){
                logger.info(threadIdentity+ " - Processando release: " + release.getReleaseID());
                List<AnalyzedClass> classes = gitController.getClassesForRelease(release);
                gitController.labelBuggynessWithSZZ(classes);
                MetricsController metricsController= new MetricsController(classes, gitController);
                metricsController.processMetrics();
                writer.writeResultsForClass(classes);
            }

        }
        gitController.closeRepo();
        logger.info(threadIdentity + "- MILESTONE 1 COMPLETATA. File CSV creato: " + csvFileName);

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