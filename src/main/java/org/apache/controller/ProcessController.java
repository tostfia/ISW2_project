package org.apache.controller;

import org.apache.logging.CollectLogger;
import org.apache.model.AnalyzedClass;
import org.apache.model.Release;
import org.apache.model.Ticket;
import org.eclipse.jgit.api.errors.GitAPIException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
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
        long overallStatTime=System.currentTimeMillis();
        String startMsg=threadIdentity+"avviato";
        logger.info(startMsg);
        try {
            processing();
        } catch (Exception e) {
            java.io.StringWriter sw = new java.io.StringWriter();
            java.io.PrintWriter pw = new java.io.PrintWriter(sw);
            e.printStackTrace(pw);
            String sStackTrace = sw.toString(); // Contiene lo stack trace come stringa

            String errorMessage = String.format("Errore critico in %s: %n%s", threadIdentity, sStackTrace);
            logger.severe(errorMessage);
        } finally {
            latch.countDown();
            long endOverallTime = System.currentTimeMillis();
            double elapsedSeconds = (endOverallTime - overallStatTime) / 1000.0;
            String finalMessage= String.format("%s completato. Tempo impiegato: %.2f secondi", threadIdentity, elapsedSeconds);
            logger.info(finalMessage);
        }
    }
    private void processing() throws IOException, URISyntaxException, GitAPIException {
        //FASE 1: Analisi Jira
        JiraController jiraController =performJiraAnalysis();
        List<Release> releases = jiraController.getRealeases();
        List<Ticket> tickets = jiraController.getFixedTickets();

       //FASE 2: Analisi Git e correlazione
        logger.info(threadIdentity+"-Avvio analisi Git ...");
        GitController gitController = new GitController(targetName, project, releases);
        gitController.buildCommitHistory();
        gitController.setTickets(tickets);
        gitController.findBuggyFiles();
        logger.info(threadIdentity+"-Analisi con SZZ");
        gitController.findAllBugIntroducingCommits();
        List<AnalyzedClass> allClasses = gitController.processClass(releases,gitController.getAllCommits());
        logger.info("Creazione degli snapshot delle classi per ogni release");
        gitController.labelBuggynessWithSZZ(allClasses);
        logger.info(threadIdentity+"-Etichettatura delle classi completata.");


        // Nuova chiamata per trovare i bug-introducing commits con SZZ

        logger.info(threadIdentity+"-Analisi Git completata.");


        //FASE 4: Generazione dataset finale
        MetricsController metricsController = new MetricsController(gitController);
        for(AnalyzedClass classSnapshot: allClasses){
           metricsController.calculateMetrics(classSnapshot);
           metricsController.calculateStatics(classSnapshot);
        }
        metricsController.generateDataset(targetName);
        //FASE 5: Chiudo le risorse
        gitController.closeRepo();
    }
    // Questo metodo esegue l'analisi di Jira, iniettando le release e i ticket
    private JiraController performJiraAnalysis() throws IOException, URISyntaxException {
        logger.info(threadIdentity+"-Avvio analisi Jira ...");
        JiraController jiraController = new JiraController(targetName);
        jiraController.injectRelease();
        jiraController.injectTickets();
        logger.info(threadIdentity+"-Analisi Jira completata.");
        return jiraController;
    }


}