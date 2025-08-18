package org.apache.controller.milestone1;

import org.apache.logging.CollectLogger;
import org.apache.model.AnalyzedClass;
import org.apache.model.Release;

import org.apache.model.Ticket;

import org.apache.utilities.metrics.CodeSmellParser;
import org.apache.utilities.metrics.NumofCodeSmells;
import org.apache.utilities.writer.CsvWriter;
import org.eclipse.jgit.api.errors.GitAPIException;


import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class ProcessController implements Runnable {
    private final String targetName;
    private final String project;
    private final CountDownLatch latch;
    private final String threadIdentity;
    private static final Logger logger = CollectLogger.getInstance().getLogger();
    private static final String PMD_REPORTS_BASE_DIR = "pmd_analysis";



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
        logger.info(threadIdentity+"-Fase 0: Pre-calcolo dati per il cold start di proportion ...");
        List<Double> coldStartProportions = new ArrayList<>();
        List<String> allProjectNames= List.of("BOOKKEEPER","STORM");
        for(String projectName:allProjectNames){
            if(projectName.equalsIgnoreCase(this.targetName)){
                continue;
            }
            logger.info(threadIdentity + "- Calcolo proporzioni per il progetto: " + projectName);
            JiraController tempJira= new JiraController(projectName);
            tempJira.injectRelease();
            tempJira.injectTickets();
            List<Ticket> ticketsWithIV= tempJira.getFixedTickets().stream().filter(t -> t.getInjectedVersion()!=null).toList();
            ProportionController tempProportionController = new ProportionController();
            double p = tempProportionController.calculateAverageProportion(ticketsWithIV);
            if(p>=0){
                coldStartProportions.add(p);
            }
        }
        logger.info(threadIdentity + "- ESECUZIONE FASE 1: ANALISI ...");
        JiraController jiraController = performJiraAnalysis();
        logger.info(threadIdentity + "- Fase 2: Applicazione di Proportion per " + this.targetName);

        // Chiama il metodo Proportion del JiraController, passandogli i dati di cold start
        jiraController.applyProportion(coldStartProportions);
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

        logger.info(threadIdentity + " --- Controllo prima della generazione PMD ---");
        logger.info(threadIdentity + " Numero di release trovate e pronte per l'analisi PMD: " + releases.size());

        if (releases.isEmpty()) {
            logger.severe(threadIdentity + " La lista delle release è vuota! Impossibile generare i report PMD. Controllare la query Jira o la logica di buildCommitHistory.");
        } else {
            // Se la lista non è vuota, procediamo come prima
            logger.info(threadIdentity + " --- INIZIO FASE DI GENERAZIONE REPORT PMD (può richiedere molto tempo) ---");
            NumofCodeSmells numofCodeSmells = new NumofCodeSmells(targetName, gitController.getRepoPath(), gitController.getGit(), releases);
            numofCodeSmells.generatePmdReports();
            logger.info(threadIdentity + " --- FINE FASE DI GENERAZIONE REPORT PMD ---");
        }

        // --- 2. Scrittura del CSV
        String csvFileName = targetName + "_dataset.csv";
        try(CsvWriter writer = new CsvWriter(csvFileName, targetName)) {
            writer.writeHeader();
            for(Release release : releases){
                logger.info(threadIdentity+ " - Processando release: " + release.getReleaseID());
                List<AnalyzedClass> classes = gitController.getClassesForRelease(release);
                gitController.labelBuggynessWithSZZ(classes);
                // 3. Costruisci il percorso del report PMD (come prima)
                String pmdReportPath = PMD_REPORTS_BASE_DIR + File.separator
                        + this.targetName + File.separator
                        + release.getId() + ".csv";

                // 4. Arricchisci con gli smell. Ora questa chiamata TROVERÀ i file.
                CodeSmellParser.extractCodeSmell(classes, pmdReportPath);

                MetricsController metricsController= new MetricsController( classes,gitController);
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