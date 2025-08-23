package org.apache.controller;
import org.apache.logging.Printer;
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


public class WhatIfAnalyzer {
    private final AggregatedClassifierResult bClassifier;
    private final Table datasetA;
    private final Instances wekaDatasetA;
    private final String projectName;
    private final CorrelationController cc;
    private Classifier loadedWekaClassifier;
    private static final String OUTPUT_DIR = "output";

    public WhatIfAnalyzer(AggregatedClassifierResult bClassifier, Table datasetA, Instances wekaDatasetA, String projectName) {
        this.bClassifier = bClassifier;
        this.datasetA = datasetA;
        this.wekaDatasetA = wekaDatasetA;
        this.projectName = projectName;
        this.cc = new CorrelationController(datasetA);

    }

    public void run() throws Exception {
        Printer.printGreen("Avvio dell'analisi 'What-If'...\n");

        // PASSO 1: Identificazione di AFeature
        String aFeature = identifyAFeature();
        String methodName = cc.findBuggyMethodWithMaxFeature(aFeature);

        Printer.print("Feature Azionabile (AFeature) identificata: " + aFeature + "\n");
        Printer.print("Metodo con il valore massimo di " + aFeature + " tra i metodi buggy: " + methodName + " - Fare il refactor\n");

        // Carica il modello del classificatore BClassifierA
        String modelFilePath = bClassifier.getModelFilePath();
        if (modelFilePath == null || modelFilePath.isEmpty()) {
            Printer.errorPrint("Percorso del modello del classificatore non trovato. Impossibile procedere con le predizioni.");
            return;
        }

        loadClassifierModel(modelFilePath);
        if (loadedWekaClassifier == null) {
            Printer.errorPrint("Impossibile caricare il modello del classificatore. Analisi interrotta.");
            return;
        }

        String smells = "NumberOfCodeSmells";

        // PASSO 2: Creazione dei dataset B+, C, B
        Printer.print("Creazione dei dataset B+, C e B...\n");
        Instances bPlusDataset = createBPlusDataset(smells);
        Instances cDataset = createCDataset(smells);
        Instances bDataset = createBDataset(bPlusDataset, smells);

        // Salva i dataset
        saveDatasetAsCSV(bPlusDataset, cDataset, bDataset);

        // PASSO 3: Predizioni su A, B, B+, C
        Printer.print("Esecuzione delle predizioni...\n");
        Map<String, PredictionResult> results = new HashMap<>();

        results.put("A", predict(wekaDatasetA, "A"));
        results.put("B+", predict(bPlusDataset, "B+"));
        results.put("C", predict(cDataset, "C"));
        results.put("B", predict(bDataset, "B"));

        // PASSO 4: Creazione e salvataggio della tabella dei risultati
        createAndSaveResultsTable(results);

        // PASSO 5: Analisi e Risposta
        analyzeResults(results, aFeature);

        Printer.printlnGreen("Analisi 'What-If' completata.\n");
    }

    private String identifyAFeature() {
        CorrelationController.FeatureCorrelation best = cc.getBestFeature();
        String aFeature = best.featureName();
        Printer.print("Feature azionabile (AFeature): " + aFeature + ", correlazione: " + best.correlation() + "\n");
        return aFeature;
    }

    private void loadClassifierModel(String modelPath) {
        try {
            loadedWekaClassifier = (Classifier) SerializationHelper.read(modelPath);
            Printer.print("Modello del classificatore caricato con successo da: " + modelPath + "\n");
        } catch (Exception e) {
            Printer.errorPrint("Errore durante il caricamento del modello del classificatore da " + modelPath + ": " + e.getMessage());
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

        Printer.printBlue("Dataset B+ creato con " + bPlus.numInstances() + " istanze (mantiene MethodName, Project, Release)\n");
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

        Printer.printBlue("Dataset C creato con " + cDataset.numInstances() + " istanze (mantiene MethodName, Project, Release)\n");
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

        Printer.printBlue("Dataset B creato con " + bDataset.numInstances() + " istanze (B+ con smells azzerati, mantiene MethodName, Project, Release)\n");
        return bDataset;
    }

    /**
     * Esegue le predizioni su un dataset Weka e restituisce i risultati
     */
    private PredictionResult predict(Instances dataToPredict, String datasetName) throws Exception {
        if (dataToPredict == null || dataToPredict.isEmpty()) {
            Printer.printYellow("Dataset '" + datasetName + "' è vuoto. Impossibile effettuare predizioni.");
            return new PredictionResult(0, 0, 0);
        }

        int actualBuggy = 0;
        int predictedBuggy = 0;
        int correctlyPredictedBuggy = 0;
        int actualNonBuggy = 0;
        int predictedNonBuggy = 0;
        int correctlyPredictedNonBuggy = 0;

        // Assicurati che il dataset abbia la classe impostata
        if (dataToPredict.classIndex() == -1) {
            dataToPredict.setClassIndex(dataToPredict.numAttributes() - 1);
        }

        for (int i = 0; i < dataToPredict.numInstances(); i++) {
            Instance inst = dataToPredict.instance(i);


            boolean isActuallyBuggy = inst.stringValue(inst.classIndex()).equals("yes");

            // Predizione del classificatore
            double predictedClassValue = loadedWekaClassifier.classifyInstance(inst);
            boolean isPredictedBuggy = dataToPredict.classAttribute().value((int) predictedClassValue).equals("yes");

            // Conteggi per actual
            if (isActuallyBuggy) {
                actualBuggy++;
            } else {
                actualNonBuggy++;
            }

            // Conteggi per predicted
            if (isPredictedBuggy) {
                predictedBuggy++;
                if (isActuallyBuggy) {
                    correctlyPredictedBuggy++;
                }
            } else {
                predictedNonBuggy++;
                if (!isActuallyBuggy) {
                    correctlyPredictedNonBuggy++;
                }
            }
        }

        Printer.printBlue("Predizioni per " + datasetName + ": " +
                "Totale=" + dataToPredict.numInstances() +
                ", Actual Buggy=" + actualBuggy +
                ", Predicted Buggy=" + predictedBuggy +
                ", Correctly Predicted Buggy=" + correctlyPredictedBuggy);

        return new PredictionResult(actualBuggy, predictedBuggy, correctlyPredictedBuggy,
                actualNonBuggy, predictedNonBuggy, correctlyPredictedNonBuggy);
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
            Printer.print("Tabella dei risultati salvata in: " + resultsPath + "\n");

            // Stampa anche la tabella nel log per visibilità immediata
            Printer.print("\n--- TABELLA DEI RISULTATI DELLE PREDIZIONI ---\n");
            Printer.print(resultsTable.print());

        } catch (Exception e) {
            Printer.errorPrint("Errore durante la creazione della tabella dei risultati: " + e.getMessage());
        }
    }

    private void analyzeResults(Map<String, PredictionResult> results, String aFeature) {
        Printer.print("\n--- ANALISI WHAT-IF ---");

        PredictionResult bPlusRes = results.get("B+");
        PredictionResult bRes = results.get("B");

        if (bPlusRes != null && bRes != null) {
            int preventableBuggyMethods = bPlusRes.getPredictedBuggy() - bRes.getPredictedBuggy();
            if (preventableBuggyMethods < 0) preventableBuggyMethods = 0;

            Printer.print("Metodi con smells predetti come buggy (B+): " + bPlusRes.getPredictedBuggy() + "\n");
            Printer.print("Metodi (ex B+ con smells azzerati) predetti come buggy (B): " + bRes.getPredictedBuggy());
            Printer.print("RISPOSTA: Circa " + preventableBuggyMethods +
                    " metodi difettosi avrebbero potuto essere prevenuti azzerando " + aFeature + "\n");

            if (bPlusRes.getPredictedBuggy() > 0) {
                double proportion = (double) preventableBuggyMethods / bPlusRes.getPredictedBuggy() * 100;
                Printer.println(String.format("Proporzione: %.2f%% dei metodi buggy con smells", proportion));
            }
        }
    }

    private void saveDatasetAsCSV(Instances bPlusDataset, Instances cDataset, Instances bDataset) {
        CSVSaver saver = new CSVSaver();

        try {
            // *** NUOVA LOGICA: Assicurati che la directory di output esista ***
            File outputDirectory = new File(OUTPUT_DIR);
            if (!outputDirectory.exists()) {
                if (outputDirectory.mkdirs()) { // Tenta di creare la/le directory
                    Printer.print("Creata directory di output: " + outputDirectory.getAbsolutePath() + "\n");
                } else {
                    Printer.errorPrint("Impossibile creare la directory di output: " + outputDirectory.getAbsolutePath() + ". Impossibile salvare i file CSV.");
                    return; // Interrompe il salvataggio se la directory non può essere creata
                }
            }


            // *** MODIFICA QUI: Rimuovi \n dai percorsi dei file ***
            // Salva bPlusDataset
            String bPlusPath = OUTPUT_DIR + File.separator + projectName + "_BPlus.csv";
            saver.setInstances(bPlusDataset);
            saver.setFile(new File(bPlusPath));
            saver.writeBatch();
            Printer.println("Dataset BPlus salvato in: " + bPlusPath); // Stampa qui la newline

            // Salva bDataset
            String bDatasetPath = OUTPUT_DIR + File.separator + projectName + "_BDataset.csv";
            saver.setInstances(bDataset);
            saver.setFile(new File(bDatasetPath));
            saver.writeBatch();
            Printer.println("Dataset B salvato in: " + bDatasetPath); // Stampa qui la newline

            // Salva cDataset
            String cDatasetPath = OUTPUT_DIR + File.separator + projectName + "_CDataset.csv";
            saver.setInstances(cDataset);
            saver.setFile(new File(cDatasetPath));
            saver.writeBatch();
            Printer.println("Dataset C salvato in: " + cDatasetPath); // Stampa qui la newline

        } catch (Exception e) {
            Printer.errorPrint("Errore durante il salvataggio dei dataset B+, B, C in CSV: " + e.getMessage());
        }
    }


}