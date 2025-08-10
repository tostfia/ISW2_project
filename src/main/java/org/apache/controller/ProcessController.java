package org.apache.controller;

import org.apache.logging.CollectLogger;
import org.apache.model.Release;
import org.apache.model.Ticket;
import org.apache.utilities.enums.FileExtension;
import org.apache.utilities.enums.ReportType;
import org.json.JSONObject;
import org.apache.utilities.Utility;

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
            logger.severe(e.getMessage());
        } finally {
            latch.countDown();
            long endOverallTime = System.currentTimeMillis();
            double elapsedSeconds = (endOverallTime - overallStatTime) / 1000.0;
            String finalMessage= String.format("%s completato. Tempo impiegato: %.2f secondi", threadIdentity, elapsedSeconds);
            logger.info(finalMessage);
        }
    }
    private void processing()  {
        //FASE 1: Analisi Jira
        JiraController jiraController =performJiraAnalysis();
        List<Release> releases = jiraController.getRealeases();

       //FASE 2: Analisi Git e correlazione
        GitController gitController= performGitAnalysis(releases,jiraController.getFixedTickets());

        //FASE 3: Calcolo metriche e salvataggio
        calculateAndStoreMetrics(gitController);
        //FASE 4: Generazione dataset finale
        MetricsController metricsController = new MetricsController(gitController);
        metricsController.generateDataset(targetName);
        //FASE 5: Chiudo le risorse
        gitController.closeRepo();
    }
    // Questo metodo esegue l'analisi di Jira, iniettando le release e i ticket
    private JiraController performJiraAnalysis(){
        logger.info(threadIdentity+"-Avvio analisi Jira ...");
        JiraController jiraController = new JiraController(targetName);
        jiraController.injectRelease();
        jiraController.injectTickets();
        logger.info(threadIdentity+"-Analisi Jira completata.");
        return jiraController;
    }
    // Questo metodo esegue l'analisi di Git, iniettando i commit e correlando i ticket
    private GitController performGitAnalysis(List<Release> releases, List<Ticket> tickets){
        logger.info(threadIdentity+"-Avvio analisi Git ...");
        GitController gitController = new GitController(targetName, project, releases);
        gitController.injectCommits();
        gitController.setTickets(tickets);
        gitController.processCommitsWithIssues();
        gitController.processClass();
        logger.info(threadIdentity+"-Analisi Git completata.");
        return gitController;
    }
    // Questo metodo calcola le metriche e salva i dati in formato JSON
    private void calculateAndStoreMetrics(GitController gitController){
        logger.info(threadIdentity+"-Avvio calcolo metriche ...");
        Utility.setupCsv(gitController);
        MetricsController metricsController = new MetricsController(gitController);
        metricsController.start();
        storeData(gitController.getJiraController(),gitController);
        logger.info(threadIdentity+"-Calcolo metriche completato.");
    }
    // Questo metodo salva i dati in formato JSON
    private void storeData(JiraController jiraController, GitController gitController) {
        logger.info(threadIdentity+"-Avvio salvataggio dati ...");
        Utility.setupJson(targetName, ReportType.RELEASE,new JSONObject(jiraController.getMapReleases()), FileExtension.JSON);
        Utility.setupJson(targetName, ReportType.TICKETS,new JSONObject(jiraController.getFixedTickets()), FileExtension.JSON);
        Utility.setupJson(targetName, ReportType.COMMITS,new JSONObject(gitController.getMapCommits()), FileExtension.JSON);
        Utility.setupJson(targetName, ReportType.SUMMARY,new JSONObject(gitController.getMapSummary()), FileExtension.JSON);
        logger.info(threadIdentity+"-Salvataggio dati completato.");
    }
}