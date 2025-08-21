package org.apache.controller.milestone1;

import org.apache.logging.CollectLogger;
import org.apache.model.AnalyzedClass;
import org.apache.model.Release;

import org.apache.model.Ticket;

import org.apache.utilities.metrics.CodeSmellParser;
import org.apache.utilities.metrics.NumOfCodeSmells;
import org.apache.utilities.writer.CsvWriter;
import org.eclipse.jgit.api.errors.GitAPIException;


import java.io.IOException;
import java.net.URISyntaxException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Level;
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
        logger.info(threadIdentity+"-Fase 0: Pre-calcolo dati per il cold start di proportion ...");
        List<Double> coldStartProportions = new ArrayList<>();

        logger.info(threadIdentity + "- Calcolo proporzioni per il progetto: " + this.targetName);
        JiraController tempJira= new JiraController(this.targetName);
        tempJira.injectRelease(); // Necessario per popolare le release prima di getFixedTickets
        tempJira.injectTickets();
        List<Ticket> ticketsWithIV= tempJira.getFixedTickets().stream().filter(t -> t.getInjectedVersion()!=null).toList();
        ProportionController tempProportionController = new ProportionController();
        double p = tempProportionController.calculateAverageProportion(ticketsWithIV);
        if(p>=0){
            coldStartProportions.add(p);
        }

        logger.info(threadIdentity + "- ESECUZIONE FASE 1: ANALISI ...");
        JiraController jiraController = performJiraAnalysis(); // Popola il main JiraController con dati

        logger.info(threadIdentity + "- Fase 2: Applicazione di Proportion (Anti-Snoring) per " + this.targetName);


        jiraController.applyProportion(coldStartProportions);

        List<Release> releasesForGitAnalysis = jiraController.getRealeases();
        List<Ticket> ticketsForGitAnalysis = jiraController.getFixedTickets();

        GitController gitController = new GitController(targetName, project, releasesForGitAnalysis);

        // Passa i ticket al GitController per findBuggyFiles() e le etichette di Affected Versions
        gitController.setTickets(ticketsForGitAnalysis);

        logger.info(threadIdentity + " - Passati " + ticketsForGitAnalysis.size() + " ticket al GitController.");

        gitController.buildCommitHistory(); // Popola allCommits, associa a Release
        gitController.buildFileCommitHistoryMap(); // Costruisce commitsPerFile map

        gitController.findBuggyFiles(); // Trova fixingCommits, buggyFilesPerCommit (dipende da tickets già settati)




        logger.info(threadIdentity + " --- Controllo prima della generazione PMD ---");
        logger.info(threadIdentity + " Numero di release trovate e pronte per l'analisi PMD: " + releasesForGitAnalysis.size());

        List<AnalyzedClass> allClassesToLabelAndWrite = new ArrayList<>();

        try {
            CodeSmellParser.setRepoRootPath(gitController.getRepoPath());

            logger.info(threadIdentity + " --- INIZIO FASE DI GENERAZIONE REPORT PMD  ---");
            NumOfCodeSmells numofCodeSmells = new NumOfCodeSmells(targetName, gitController.getRepoPath(), gitController.getGit(), releasesForGitAnalysis);
            numofCodeSmells.generatePmdReports(); // Genera i file XML dei report PMD
            logger.info(threadIdentity + " --- FINE FASE DI GENERAZIONE REPORT PMD ---");

            // --- Ciclo per Raccogliere Classi e Calcolare Metriche per ogni Release ---
            // Questo crea gli snapshot delle classi per ogni release e calcola tutte le metriche
            int totalReleases = releasesForGitAnalysis.size();
            for (int i = 0; i < totalReleases; i++) {
                Release release = releasesForGitAnalysis.get(i);
                logger.info(threadIdentity + " - Processando release per raccolta classi e metriche: " + release.getReleaseID());
                logger.info("Analisi release " + (i+1) + "/" + totalReleases +
                        " (ID: " + release.getId() + ", Nome: " + release.getReleaseName() + ")");

                List<AnalyzedClass> classesInRelease = gitController.getClassesForRelease(release);

                // Estrai i code smell da PMD per le classi di questa release
                String releaseId = release.getReleaseID();
                CodeSmellParser.extractCodeSmell(classesInRelease, targetName, releaseId);

                // Calcola le altre metriche per le classi di questa release
                MetricsController metricsController = new MetricsController(classesInRelease, gitController);
                metricsController.processMetrics();


                allClassesToLabelAndWrite.addAll(classesInRelease);
            }


            if (allClassesToLabelAndWrite.isEmpty()) {
                logger.warning(threadIdentity + "- Nessuna classe da etichettare. Il dataset risulterà vuoto.");
                return; // Esce se non ci sono classi
            }
            gitController.applyBugginessLabels(allClassesToLabelAndWrite); // <<< CHIAMATA AL TUO NUOVO METODO ORCHESTRATORE
            logger.info(threadIdentity + " - Etichettatura bugginess completata per " + allClassesToLabelAndWrite.size() + " classi.");


            // --- 2. Scrittura del CSV del Dataset Finale ---
            String csvFileName = targetName + "_dataset.csv";
            try (CsvWriter writer = new CsvWriter(csvFileName, targetName)) {
                writer.writeHeader();

                writer.writeResultsForClass(allClassesToLabelAndWrite);
            }
            logger.info(threadIdentity + "- MILESTONE 1 COMPLETATA. File CSV creato: " + csvFileName);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Errore critico durante l'elaborazione del progetto " + targetName + ": " + e.getMessage(), e);
        } finally {
            gitController.closeRepo(); // Assicurati che il repository venga chiuso anche in caso di errori
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