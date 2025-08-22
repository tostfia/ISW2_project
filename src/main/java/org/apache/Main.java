package  org.apache;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import org.apache.controller.*;


import org.apache.controller.milestone1.JiraController;
import org.apache.logging.CollectLogger;
import org.apache.model.AggregatedClassifierResult;
import org.apache.model.Release;
import tech.tablesaw.api.Table;
import weka.classifiers.Classifier;
import weka.core.Instances;
import weka.core.SerializationHelper;

public class Main {


    private static final Logger logger = CollectLogger.getInstance().getLogger();


    public static final String SYS_CUT_PERCENTAGE = "SYS_CUT_PERCENTAGE";
    private static final double DEFAULT_CUT_PERCENTAGE = 0.34; // Corrisponde a "ignora l'ultimo 66%"



    public static void main(String[] args) throws Exception {


        if (args.length == 0) {
            logger.severe("Errore: Il nome del progetto deve essere passato come primo argomento.");
            return;
        }
        String projectName = args[0]; // Nome del progetto passato come argomento
        logger.info(()->"INIZIO ANALISI per : %s"+ projectName) ;

        JiraController jiraController = new JiraController(projectName);
        jiraController.injectRelease();
        // Recupera la percentuale di taglio
        double cutPercentage = getCutPercentage();


        int walkForwardIterations = jiraController.getRealeases().size()/2;


        logger.info("Fase 1: Preparazione del Dataset 'A' e generazione dei file ARFF per Walk-Forward...");
        long start = System.currentTimeMillis();

        DatasetController datasetController = new DatasetController(projectName);
        // Passiamo la percentuale di taglio a prepareDatasetA()
        Table datasetA = datasetController.prepareDatasetA(cutPercentage);
        datasetA.write().csv("output"+File.separator+projectName + "datasetA.csv");
        logger.info ("Dataset A salvato in: output/datasetA.csv");

        if (datasetA.isEmpty()) {
            logger.severe("Analisi interrotta: il dataset 'A' non è stato creato o è vuoto.");
            return;
        }

        // Generazione dei file ARFF per il Walk-Forward
        int actualIteration ; // Inizializziamo a 0 per tenere traccia delle iterazioni effettive
        try {
            actualIteration=datasetController.generateWalkForwardArffFiles(datasetA, projectName, walkForwardIterations);

            logger.info(()->"Generazione dei file ARFF completata per %d iterazioni di Walk-Forward."+ actualIteration);
        } catch (IOException e) {
            logger.severe(()->"Errore durante la generazione dei file ARFF per il Walk-Forward: " + e.getMessage());
            return;
        } catch (Exception e) {
            logger.severe(()->"Errore generico durante la generazione dei file ARFF: " + e.getMessage());
            return;
        }

        long end = System.currentTimeMillis();
        long time = (end - start) / 1000;
        logger.info(()->"Fase 2 completata in"+ time+" secondi.");


        logger.info("\nFase 2: Esecuzione Classificazione con WekaController...");
        start = System.currentTimeMillis();
        //Comparo l'accuratezza dei tre classifier (Ibk, Naive e RandomForest)
        WekaController wekaClassifierRunner = new WekaController(projectName, actualIteration);
        wekaClassifierRunner.classify();
        wekaClassifierRunner.saveResults();

        // Scegli il miglior classificatore dal report
        ReportAnalyzer reportAnalyzer = new ReportAnalyzer(projectName);
        reportAnalyzer.analyzeAllCriteriaAndSave();
        AggregatedClassifierResult bClassifier = reportAnalyzer.getBestClassifier("AUC");
        if (bClassifier == null) {
            logger.severe("Nessun classificatore trovato valido. Analisi interrotta.");
            return;
        }

        logger.info(String.format("Miglior classificatore selezionato: %s ", bClassifier.getClassifierName()));

        long endFase2 = System.currentTimeMillis();
        long timeFaseDue = (endFase2 - start) / 1000;
        logger.info(()->"Fase 2 completata in"+ timeFaseDue+" secondi.");

        List<String> allReleases = jiraController.getRealeases()
                .stream()
                .map(Release::getReleaseName)
                .toList();

        Instances wekaDatasetA = datasetController.convertTablesawToWekaInstances(datasetA,allReleases,projectName+"_datasetA");
        Classifier classifier;
        switch(bClassifier.getClassifierName()) {
            case "RandomForest" -> classifier = new weka.classifiers.trees.RandomForest();
            case "NaiveBayes" -> classifier = new weka.classifiers.bayes.NaiveBayes();
            case "IBk" -> classifier = new weka.classifiers.lazy.IBk();
            default -> {
                logger.severe(String.format("Classificatore non supportato: %s. Analisi interrotta.", bClassifier.getClassifierName()));
                return;
            }
        }
        wekaDatasetA.setClassIndex(wekaDatasetA.numAttributes() - 1);
        classifier.buildClassifier(wekaDatasetA);
        String modelPath ="models"+ File.separator+ projectName+"_BClassifierA.model";
        SerializationHelper.write(modelPath, classifier);
        bClassifier.setModelFilePath(modelPath);
        logger.info(()->"Modello caricato e salvato in: %s"+ modelPath);




        logger.info("\nFase 3: Analisi di Correlazione e Simulazione What-If...");
        start = System.currentTimeMillis();

        WhatIfAnalyzer whatIfAnalyzer = new WhatIfAnalyzer(bClassifier, datasetA,wekaDatasetA, projectName);
        try {
            whatIfAnalyzer.run();
        } catch (Exception e) {
            logger.severe(()->"Errore durante l'analisi What-If: " + e.getMessage());
            return;
        }


        end = System.currentTimeMillis();
        long timeEnd = (end - start) / 1000;
        logger.info(()->"Fase 3 completata in"+ timeEnd+" secondi.");
    }

    private static double getCutPercentage() {
        String cut = System.getenv(SYS_CUT_PERCENTAGE);
        try {
            double aDouble = Double.parseDouble(cut);
            String msg = "Checking percentage: " + aDouble;
            logger.info(msg);
            System.setProperty(SYS_CUT_PERCENTAGE, cut); // Imposta come proprietà di sistema (potrebbe non servire qui)
            return aDouble;
        } catch (NumberFormatException | NullPointerException e) {
            String exceptionMsg = SYS_CUT_PERCENTAGE  + " exception: " + e.getClass().getSimpleName() + " ";
            exceptionMsg += e instanceof NumberFormatException ? " " + e.getMessage() : "env variable not setup";
            String warning = exceptionMsg +  " Invalid percentage: " + cut;
            logger.warning(warning);
            System.setProperty(SYS_CUT_PERCENTAGE, "" + DEFAULT_CUT_PERCENTAGE); // Imposta default come proprietà di sistema
            double aDouble = DEFAULT_CUT_PERCENTAGE; // Usa il default
            warning = "Now is setup to: " + aDouble;
            logger.warning(warning);
            return aDouble;
        }
    }
}
