package org.apache.controller;
import org.apache.logging.CollectLogger;
import org.apache.model.AggregatedClassifierResult;
import org.apache.model.PredictionResult;

import tech.tablesaw.api.Table;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.IntColumn;

import weka.classifiers.Classifier;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.SerializationHelper;
import weka.core.converters.CSVSaver;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class WhatIfAnalyzer {
    private final AggregatedClassifierResult bClassifier;
    private final Table datasetA;
    private final Instances wekaDatasetA;
    private final String projectName;
    private final CorrelationController cc;
    private final Logger logger;
    private Classifier loadedWekaClassifier;
    private static final String OUTPUT_DIR = "output";

    public WhatIfAnalyzer(AggregatedClassifierResult bClassifier, Table datasetA, Instances wekaDatasetA, String projectName) {
        this.bClassifier = bClassifier;
        this.datasetA = datasetA;
        this.wekaDatasetA = wekaDatasetA;
        this.projectName = projectName;
        this.cc = new CorrelationController(datasetA);
        this.logger = CollectLogger.getInstance().getLogger();
    }

    public void run() throws Exception {
        logger.info("Avvio dell'analisi 'What-If'...");

        // PASSO 1: Identificazione di AFeature
        String aFeature = identifyAFeature();
        String methodName = cc.findBuggyMethodWithMaxFeature(aFeature);

        logger.info(()->"Feature Azionabile (AFeature) identificata: " + aFeature);
        logger.info(()->"Metodo con il valore massimo di " + aFeature + " tra i metodi buggy: " + methodName + " - Fare il refactor");

        // Carica il modello del classificatore BClassifierA
        String modelFilePath = bClassifier.getModelFilePath();
        if (modelFilePath == null || modelFilePath.isEmpty()) {
            logger.severe("Percorso del modello del classificatore non trovato. Impossibile procedere con le predizioni.");
            return;
        }

        loadClassifierModel(modelFilePath);
        if (loadedWekaClassifier == null) {
            logger.severe("Impossibile caricare il modello del classificatore. Analisi interrotta.");
            return;
        }

        String smells = "NumberOfCodeSmells";

        // PASSO 2: Creazione dei dataset B+, C, B
        logger.info("Creazione dei dataset B+, C e B...");
        Instances bPlusDataset = createBPlusDataset(smells);
        Instances cDataset = createCDataset(smells);
        Instances bDataset = createBDataset(bPlusDataset, smells);

        // Salva i dataset
        saveDatasetAsCSV(bPlusDataset, cDataset, bDataset);

        // PASSO 3: Predizioni su A, B, B+, C
        logger.info("Esecuzione delle predizioni...");
        Map<String, PredictionResult> results = new HashMap<>();

        results.put("A", predict(wekaDatasetA, "A"));
        results.put("B+", predict(bPlusDataset, "B+"));
        results.put("C", predict(cDataset, "C"));
        results.put("B", predict(bDataset, "B"));

        // PASSO 4: Creazione e salvataggio della tabella dei risultati
        createAndSaveResultsTable(results);

        // PASSO 5: Analisi e Risposta
        analyzeResults(results, aFeature);

        logger.info("Analisi 'What-If' completata.");
    }

    private String identifyAFeature() {
        CorrelationController.FeatureCorrelation best = cc.getBestFeature();
        String aFeature = best.featureName();
        logger.info(()->"Feature azionabile (AFeature): " + aFeature + ", correlazione: " + best.correlation());
        return aFeature;
    }

    private void loadClassifierModel(String modelPath) {
        try {
            loadedWekaClassifier = (Classifier) SerializationHelper.read(modelPath);
            logger.info(()->"Modello del classificatore caricato con successo da: " + modelPath);
        } catch (Exception e) {
            logger.severe(()->"Errore durante il caricamento del modello del classificatore da " + modelPath + ": " + e.getMessage());
            loadedWekaClassifier = null;
        }
    }

    private Instances createBPlusDataset(String smells) {
        // Crea una copia completa della struttura del dataset originale
        Instances bPlus = new Instances(wekaDatasetA, 0);
        int smellsIndex = wekaDatasetA.attribute(smells).index();
        int bugIndex = wekaDatasetA.classIndex();

        for (int i = 0; i < wekaDatasetA.numInstances(); i++) {
            Instance inst = wekaDatasetA.instance(i);
            double smellsVal = inst.value(smellsIndex);
            String bugValue = inst.stringValue(bugIndex);

            if (smellsVal > 0 && bugValue.equals("yes")) {
                // Crea una copia completa dell'istanza mantenendo tutti gli attributi
                Instance newInst = (Instance) inst.copy();
                newInst.setDataset(bPlus);
                bPlus.add(newInst);
            }
        }

        logger.info(()->"Dataset B+ creato con " + bPlus.numInstances() + " istanze (mantiene MethodName, Project, Release)");
        return bPlus;
    }

    private Instances createCDataset(String smells) {
        // Crea una copia completa della struttura del dataset originale
        Instances cDataset = new Instances(wekaDatasetA, 0);
        int smellsIndex = wekaDatasetA.attribute(smells).index();

        for (int i = 0; i < wekaDatasetA.numInstances(); i++) {
            Instance inst = wekaDatasetA.instance(i);
            if (inst.value(smellsIndex) == 0) {
                // Crea una copia completa dell'istanza mantenendo tutti gli attributi
                Instance newInst = (Instance) inst.copy();
                newInst.setDataset(cDataset);
                cDataset.add(newInst);
            }
        }

        logger.info(()->"Dataset C creato con " + cDataset.numInstances() + " istanze (mantiene MethodName, Project, Release)");
        return cDataset;
    }

    private Instances createBDataset(Instances bPlus, String smells) {
        // Crea una copia completa di B+ mantenendo tutti gli attributi
        Instances bDataset = new Instances(bPlus);
        int smellsIndex = bDataset.attribute(smells).index();

        // Azzera solo il valore degli smells, mantenendo tutti gli altri attributi
        for (int i = 0; i < bDataset.numInstances(); i++) {
            Instance inst = bDataset.instance(i);
            inst.setValue(smellsIndex, 0); // Azzera solo gli smells
        }

        logger.info(()->"Dataset B creato con " + bDataset.numInstances() + " istanze (B+ con smells azzerati, mantiene MethodName, Project, Release)");
        return bDataset;
    }

    /**
     * Esegue le predizioni su un dataset Weka e restituisce i risultati
     */
    private PredictionResult predict(Instances dataToPredict, String datasetName) throws Exception {
        if (dataToPredict == null || dataToPredict.isEmpty()) {
            logger.warning(()->"Dataset '" + datasetName + "' è vuoto. Impossibile effettuare predizioni.");
            return new PredictionResult(0, 0, 0);
        }

        if (dataToPredict.classIndex() == -1) {
            dataToPredict.setClassIndex(dataToPredict.numAttributes() - 1);
        }

        int[] counts = countBuggyPredictions(dataToPredict);

        logger.info(()->"Predizioni per " + datasetName + ": " +
                "Totale=" + dataToPredict.numInstances() +
                ", Actual Buggy=" + counts[0] +
                ", Predicted Buggy=" + counts[1] +
                ", Correctly Predicted Buggy=" + counts[2]);

        return new PredictionResult(
                counts[0], counts[1], counts[2],
                counts[3], counts[4], counts[5]
        );
    }

    private int[] countBuggyPredictions(Instances data) throws Exception {
        int actualBuggy = 0;
        int predictedBuggy = 0;
        int correctlyPredictedBuggy = 0;
        int actualNonBuggy = 0;
        int predictedNonBuggy = 0;
        int correctlyPredictedNonBuggy = 0;

        for (int i = 0; i < data.numInstances(); i++) {
            Instance inst = data.instance(i);
            boolean isActuallyBuggy = inst.stringValue(inst.classIndex()).equals("yes");
            boolean isPredictedBuggy = data.classAttribute().value(
                    (int) loadedWekaClassifier.classifyInstance(inst)
            ).equals("yes");

            if (isActuallyBuggy) actualBuggy++;
            else actualNonBuggy++;

            if (isPredictedBuggy) {
                predictedBuggy++;
                if (isActuallyBuggy) correctlyPredictedBuggy++;
            } else {
                predictedNonBuggy++;
                if (!isActuallyBuggy) correctlyPredictedNonBuggy++;
            }
        }
        return new int[] {
                actualBuggy, predictedBuggy, correctlyPredictedBuggy,
                actualNonBuggy, predictedNonBuggy, correctlyPredictedNonBuggy
        };
    }
    /**
     * Crea e salva una tabella con i risultati delle predizioni
     */
    private void createAndSaveResultsTable(Map<String, PredictionResult> results) {
        try {
            // Crea le colonne per la tabella
            StringColumn datasetColumn = StringColumn.create("Dataset");
            IntColumn totalInstancesColumn = IntColumn.create("Total_Instances");
            IntColumn actualBuggyColumn = IntColumn.create("Actual_Buggy");
            IntColumn predictedBuggyColumn = IntColumn.create("Estimated_Buggy");
            IntColumn actualNonBuggyColumn = IntColumn.create("Actual_NonBuggy");
            IntColumn predictedNonBuggyColumn = IntColumn.create("Estimated_NonBuggy");
            IntColumn correctBuggyColumn = IntColumn.create("Correct_Buggy_Predictions");
            IntColumn correctNonBuggyColumn = IntColumn.create("Correct_NonBuggy_Predictions");

            // Popola la tabella con i risultati
            for (Map.Entry<String, PredictionResult> entry : results.entrySet()) {
                String datasetName = entry.getKey();
                PredictionResult result = entry.getValue();

                datasetColumn.append(datasetName);
                totalInstancesColumn.append(result.getTotalInstances());
                actualBuggyColumn.append(result.getActualBuggy());
                predictedBuggyColumn.append(result.getPredictedBuggy());
                actualNonBuggyColumn.append(result.getActualNonBuggy());
                predictedNonBuggyColumn.append(result.getPredictedNonBuggy());
                correctBuggyColumn.append(result.getCorrectlyPredictedBuggy());
                correctNonBuggyColumn.append(result.getCorrectlyPredictedNonBuggy());
            }

            // Crea la tabella
            Table resultsTable = Table.create("Prediction_Results")
                    .addColumns(datasetColumn, totalInstancesColumn, actualBuggyColumn,
                            predictedBuggyColumn, actualNonBuggyColumn, predictedNonBuggyColumn,
                            correctBuggyColumn, correctNonBuggyColumn);

            // Salva la tabella come CSV
            String resultsPath = OUTPUT_DIR + File.separator + projectName + "_prediction_results.csv";
            resultsTable.write().csv(resultsPath);
            logger.info(()->"Tabella dei risultati salvata in: " + resultsPath);

            // Stampa anche la tabella nel log per visibilità immediata
            logger.info("\n--- TABELLA DEI RISULTATI DELLE PREDIZIONI ---");
            logger.info(resultsTable.print());

        } catch (Exception e) {
            logger.severe(()->"Errore durante la creazione della tabella dei risultati: " + e.getMessage());
        }
    }

    private void analyzeResults(Map<String, PredictionResult> results, String aFeature) {
        logger.info("\n--- ANALISI WHAT-IF ---");

        PredictionResult bPlusRes = results.get("B+");
        PredictionResult bRes = results.get("B");

        if (bPlusRes != null && bRes != null) {
            int preventableBuggyMethods = bPlusRes.getPredictedBuggy() - bRes.getPredictedBuggy();
            if (preventableBuggyMethods < 0) preventableBuggyMethods = 0;

            logger.info(()->"Metodi con smells predetti come buggy (B+): " + bPlusRes.getPredictedBuggy());
            logger.info(()->"Metodi (ex B+ con smells azzerati) predetti come buggy (B): " + bRes.getPredictedBuggy());
            logger.info("RISPOSTA: Circa " + preventableBuggyMethods +
                    " metodi difettosi avrebbero potuto essere prevenuti azzerando " + aFeature);

            if (bPlusRes.getPredictedBuggy() > 0) {
                double proportion = (double) preventableBuggyMethods / bPlusRes.getPredictedBuggy() * 100;
                logger.info(String.format("Proporzione: %.2f%% dei metodi buggy con smells", proportion));
            }
        }
    }

    private void saveDatasetAsCSV(Instances bPlusDataset, Instances cDataset, Instances bDataset) {
        CSVSaver saver = new CSVSaver();

        try {
            // Verifica e logga gli attributi presenti nei dataset
            logDatasetAttributes("B+", bPlusDataset);
            logDatasetAttributes("C", cDataset);
            logDatasetAttributes("B", bDataset);

            // Salva bPlusDataset
            saver.setInstances(bPlusDataset);
            saver.setFile(new File(OUTPUT_DIR + File.separator + projectName + "_BPlus.csv"));
            saver.writeBatch();
            logger.info(()->"Dataset BPlus salvato in: output" + File.separator + projectName + "_BPlus.csv");

            // Salva bDataset
            saver.setInstances(bDataset);
            saver.setFile(new File(OUTPUT_DIR + File.separator + projectName + "_BDataset.csv"));
            saver.writeBatch();
            logger.info(()->"Dataset B salvato in: output" + File.separator + projectName + "_BDataset.csv");

            // Salva cDataset
            saver.setInstances(cDataset);
            saver.setFile(new File(OUTPUT_DIR + File.separator + projectName + "_CDataset.csv"));
            saver.writeBatch();
            logger.info(()->"Dataset C salvato in: output" + File.separator + projectName + "_CDataset.csv");

        } catch (Exception e) {
            logger.severe(()->"Errore durante il salvataggio dei dataset B+, B, C in CSV: " + e.getMessage());
        }
    }

    /**
     * Logga gli attributi presenti in un dataset per verifica
     */
    private void logDatasetAttributes(String datasetName, Instances dataset) {
        logger.info("Dataset " + datasetName + " contiene " + dataset.numInstances() + " istanze e " + dataset.numAttributes() + " attributi:");

        // Verifica specificamente per le colonne che ci interessano
        String[] importantColumns = {"MethodName", "Project", "Release", "NumberOfCodeSmells", "bugginess"};
        for (String colName : importantColumns) {
            if (dataset.attribute(colName) != null) {
                logger.info(()->"  ✓ " + colName + " presente (indice: " + dataset.attribute(colName).index() + ")");
            } else {
                logger.warning(()->"  ✗ " + colName + " MANCANTE!");
            }
        }

        // Mostra i primi 3 attributi per debug
        StringBuilder attrs = new StringBuilder("  Primi attributi: ");
        for (int i = 0; i < Math.min(5, dataset.numAttributes()); i++) {
            attrs.append(dataset.attribute(i).name()).append(", ");
        }
        logger.info(attrs.toString());
    }
}