package org.apache.controller.milestone1;

import org.apache.logging.Printer;
import org.apache.model.AnalyzedClass;

import org.apache.model.Release;

import org.apache.model.Ticket;

import org.apache.utilities.metrics.CodeSmellParser;
import org.apache.utilities.metrics.NumOfCodeSmells;
import org.apache.utilities.writer.CsvWriter;
import org.eclipse.jgit.api.errors.GitAPIException;


import java.io.IOException;
import java.net.URISyntaxException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;



public class ProcessController implements Runnable {
    private final String targetName;
    private final String project;
    private final CountDownLatch latch;
    private final String threadIdentity;

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
        Printer.printGreen(startMsg);
        try {
            processing();
        } catch (Exception e) {
            java.io.StringWriter sw = new java.io.StringWriter();
            java.io.PrintWriter pw = new java.io.PrintWriter(sw);
            e.printStackTrace(pw);
            String sStackTrace = sw.toString();

            String errorMessage = String.format("Errore critico in %s: %n%s", threadIdentity, sStackTrace);
            Printer.errorPrint(errorMessage);
        } finally {
            latch.countDown();
            long endOverallTime = System.currentTimeMillis();
            double elapsedSeconds = (endOverallTime - overallStatTime) / 1000.0;
            String finalMessage = String.format("%s completato. Tempo impiegato: %.2f secondi", threadIdentity, elapsedSeconds);
            Printer.printlnGreen(finalMessage);
        }
    }
    private void processing() throws IOException, URISyntaxException, GitAPIException {
        Printer.printlnBlue(threadIdentity+"-Fase 0: Pre-calcolo dati per il cold start di proportion ...\n");
        List<Double> coldStartProportions = new ArrayList<>();


        Printer.printBlue(threadIdentity + "- Calcolo proportion per il progetto: " + this.targetName+ "\n");
        JiraController tempJira= new JiraController(this.targetName);
        tempJira.injectRelease();
        tempJira.injectTickets();
        List<Ticket> ticketsWithIV= tempJira.getFixedTickets().stream().filter(t -> t.getInjectedVersion()!=null).toList();
        ProportionController tempProportionController = new ProportionController();
        double p = tempProportionController.calculateAverageProportion(ticketsWithIV);
        if(p>=0){
            coldStartProportions.add(p);
        }

        Printer.printBlue(threadIdentity + "- ESECUZIONE FASE 1: ANALISI ...\n");
        JiraController jiraController = performJiraAnalysis();
        Printer.printBlue(threadIdentity + "- Fase 2: Applicazione di Proportion per " + this.targetName+ "\n");

        // Chiama il metodo Proportion del JiraController, passandogli i dati di cold start
        jiraController.applyProportion(coldStartProportions);
        List<Release> releases = jiraController.getRealeases();
        List<Ticket> tickets = jiraController.getFixedTickets();


        GitController gitController = new GitController(targetName, project, releases);
        gitController.setTickets(tickets);

        Printer.print(threadIdentity + " - Passati " + tickets.size() + " ticket al GitController \n.");

        gitController.buildCommitHistory();
        gitController.findBuggyFiles();
        gitController.buildFileCommitHistoryMap();
        gitController.findAllBugIntroducingCommits();

        Printer.print(threadIdentity + " --- Controllo prima della generazione PMD ---\n");
        Printer.print(threadIdentity + " Numero di release trovate e pronte per l'analisi PMD: " + releases.size()+ "\n");

        try {
            CodeSmellParser.setRepoRootPath(gitController.getRepoPath());


            Printer.printBlue(threadIdentity + " --- INIZIO FASE DI GENERAZIONE REPORT PMD  ---\n");
            NumOfCodeSmells numofCodeSmells = new NumOfCodeSmells(targetName, gitController.getRepoPath(), gitController.getGit(), releases);
            numofCodeSmells.generatePmdReports();
            Printer.printBlue(threadIdentity + " --- FINE FASE DI GENERAZIONE REPORT PMD ---\n");



            // --- 2. Scrittura del CSV
            String csvFileName = targetName + "_dataset.csv";
            try (CsvWriter writer = new CsvWriter(csvFileName, targetName)) {
                writer.writeHeader();
                int total = releases.size();
                int index = 0;
                for (Release release : releases) {
                    Printer.print(threadIdentity + " - Processando release: " + release.getReleaseID()+ "\n");
                    index++;
                    Printer.printBlue("Analisi release " + index + "/" + total +
                            " (ID: " + release.getId() + ", Nome: " + release.getReleaseName() + ")\n");
                    List<AnalyzedClass> classes = gitController.getClassesForRelease(release);
                    gitController.labelBugginess(classes);
                    Path baseDir = Paths.get(PMD_REPORTS_BASE_DIR, targetName);
                    Files.createDirectories(baseDir);  // crea la cartella se non esiste

                    String releaseId = release.getReleaseID();
                    Path reportPath = baseDir.resolve(releaseId + ".xml");  // file unico per release

                    Printer.print(threadIdentity + " - Percorso report PMD per release " + releaseId + ": " + reportPath+ "\n");


                    CodeSmellParser.extractCodeSmell(classes, targetName, releaseId);


                    MetricsController metricsController = new MetricsController(classes, gitController);
                    metricsController.processMetrics();
                    writer.writeResultsForClass(classes);
                }

            }
            gitController.closeRepo();
            Printer.printlnGreen(threadIdentity + "- MILESTONE 1 COMPLETATA. File CSV creato: " + csvFileName+ "\n");
        }catch (Exception e) {
            Printer.errorPrint( threadIdentity + " Errore FATALE nel processo di analisi DOPO la generazione PMD: " + e.getMessage());


        }

    }



    private JiraController performJiraAnalysis() throws IOException, URISyntaxException {
        Printer.printGreen(threadIdentity + "-Avvio analisi Jira ...\n");
        JiraController jiraController = new JiraController(targetName);
        jiraController.injectRelease();
        jiraController.injectTickets();
        Printer.printGreen(threadIdentity + "-Analisi Jira completata.\n");
        return jiraController;
    }
}