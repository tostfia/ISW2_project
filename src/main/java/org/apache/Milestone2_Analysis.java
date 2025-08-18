package org.apache;
import org.apache.controller.ClassifierController;
import org.apache.controller.CorrelationController;
import org.apache.controller.DatasetController;
import org.apache.controller.WekaController;
import org.apache.logging.CollectLogger; // Assicurati che i package siano corretti
import tech.tablesaw.api.Table;
import weka.classifiers.Classifier;

import java.util.logging.Logger;

public class Milestone2_Analysis {

    private static final Logger logger = CollectLogger.getInstance().getLogger();

    public static void main(String[] args) throws Exception {

        // --- IMPOSTAZIONI ---
        String projectName = args[0]; // Nome del progetto passato come argomento
        logger.info("--- AVVIO MILESTONE 2 PER: " + projectName + " ---");
        long overallStart = System.currentTimeMillis();

        // ==================================================================
        // PASSO 1: PREPARAZIONE DEL DATASET "A"
        // Equivalente alla parte iniziale di "injectAndProcess"
        // ==================================================================
        logger.info("Fase 1: Preparazione del Dataset 'A'...");
        long start = System.currentTimeMillis();

        DatasetController datasetController = new DatasetController(projectName);
        Table datasetA = datasetController.prepareDatasetA(); // Questo metodo carica il CSV e filtra il 50% delle release

        if (datasetA == null || datasetA.isEmpty()) {
            logger.severe("Analisi interrotta: il dataset 'A' non è stato creato o è vuoto.");
            return;
        }
        long end = System.currentTimeMillis();
        logger.info("Fase 1 completata in " + (end - start) / 1000.0 + " secondi.");

        // ==================================================================
        // PASSO 2: CLASSIFICAZIONE E SCELTA DEL MODELLO
        // Equivalente alla "Classification Phase" dell'esempio
        // ==================================================================
        logger.info("\nFase 2: Classificazione e Scelta del Miglior Modello...");
        start = System.currentTimeMillis();

        ClassifierController classifierController = new ClassifierController(datasetA);
        //Classifier bClassifier = classifierController.chooseBestClassifier(); // Questo metodo usa la divisione 66/33

        end = System.currentTimeMillis();
        logger.info("Fase 2 completata in " + (end - start) / 1000.0 + " secondi.");

        // ==================================================================
        // PASSO 3: ANALISI "WHAT-IF" E RISULTATI FINALI
        // Equivalente a "WekaProcessing.sinkResults()" ma con la simulazione
        // ==================================================================
        logger.info("\nFase 3: Analisi di Correlazione e Simulazione What-If...");
        start = System.currentTimeMillis();

        // 3a. Analisi di Correlazione
        CorrelationController correlationController = new CorrelationController(datasetA);
        String aFeature = correlationController.findActionableFeature();

        if (aFeature != null) {
            correlationController.findMethodToRefactor(aFeature);

            // 3b. Simulazione What-If
            //WekaController wekaController = new WekaController(datasetA, bClassifier, aFeature);
            //wekaController.performWhatIfAnalysis();
        } else {
            logger.warning("Nessuna feature actionable trovata, salto della simulazione What-If.");
        }

        end = System.currentTimeMillis();
        logger.info("Fase 3 completata in " + (end - start) / 1000.0 + " secondi.");

        // ==================================================================
        // PASSO 4: SCRITTURA DEL REPORT FINALE
        // Equivalente a "ReportUtility.writeFinalResults"
        // ==================================================================
        // Chiamare una classe 'ReportWriter' che prende i risultati
        // di ogni fase e li formatta in un file di testo o markdown per il tuo report.

        long overallEnd = System.currentTimeMillis();
        logger.info("\n--- ANALISI MILESTONE 2 COMPLETATA in " + (overallEnd - overallStart) / 1000.0 + " secondi ---");
    }
}
