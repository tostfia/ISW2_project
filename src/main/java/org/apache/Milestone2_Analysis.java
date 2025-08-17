package org.apache;
//import org.apache.controller.ClassifierController;
import org.apache.controller.DatasetController;
//import org.apache.controller.WekaController;
import org.apache.logging.CollectLogger;
import tech.tablesaw.api.Table;
//import weka.classifiers.Classifier;

import java.util.logging.Logger;

public class Milestone2_Analysis {

    private static final Logger logger = CollectLogger.getInstance().getLogger();

    public static void main(String[] args) throws Exception {

        // --- IMPOSTAZIONI ---
        String projectName = "BOOKKEEPER";
        String csvFilePath = projectName + "_dataset.csv";

        logger.info("--- AVVIO MILESTONE 2 PER: " + projectName + " ---");

        // --- FASE 1: Preparazione Dati (con un controller dedicato) ---
        DatasetController datasetController = new DatasetController(projectName);
        Table datasetA = datasetController.prepareDatasetA();

        if (datasetA == null || datasetA.isEmpty()) {
            logger.severe("Analisi interrotta: il dataset 'A' non è stato creato o è vuoto.");
            return;
        }

        // --- FASE 2: Scelta del Classificatore ---
        //ClassifierController classifierController = new ClassifierController(datasetA);
        //Classifier bClassifier = classifierController.chooseBestClassifier();

        // --- FASE 3: Analisi di Correlazione e Refactoring ---
        //RefactoringController rc = new RefactoringController(datasetA);
        //String aFeature = proportionController.findActionableFeature();

        //if (aFeature != null) {
            //proportionController.findMethodToRefactor(aFeature);
        //}

        // --- FASE 4: Simulazione "What-If" ---

        //WekaController wekaController = new WekaController(datasetA, bClassifier, aFeature);
        //wekaController.performWhatIfAnalysis();

        logger.info("\n--- ANALISI MILESTONE 2 COMPLETATA ---");
    }
}
