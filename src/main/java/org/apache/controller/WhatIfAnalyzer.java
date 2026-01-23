package org.apache.controller;


import org.apache.logging.Printer;
import org.apache.model.AggregatedClassifierResult;
import org.apache.model.PredictionResult;
import tech.tablesaw.api.Table;
import tech.tablesaw.api.IntColumn;
import tech.tablesaw.api.StringColumn;
import weka.classifiers.Classifier;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.SerializationHelper;
import weka.core.converters.CSVSaver;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Remove;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class WhatIfAnalyzer {

    private final AggregatedClassifierResult bClassifier;
    private final Table datasetA;
    private Instances wekaDatasetA;
    private final String projectName;
    private final CorrelationController cc;
    private Classifier loadedWekaClassifier;

    private static final String OUTPUT_DIR = "output";

    // Costanti per feature e dataset
    private static final String FEATURE_SMELLS = "NumberOfCodeSmells";
    private static final String DATASET_BPLUS = "B+";
    private static final String DATASET_B = "B";
    private static final String DATASET_C = "C";
    private static final String DATASET_A = "A";

    public WhatIfAnalyzer(AggregatedClassifierResult bClassifier, Table datasetA, Instances wekaDatasetA, String projectName) {
        this.bClassifier = bClassifier;
        this.datasetA = datasetA;
        this.wekaDatasetA = wekaDatasetA;
        this.projectName = projectName;
        this.cc = new CorrelationController(datasetA);
    }

    /** Flusso principale */
    public void run() throws Exception {
        Printer.printGreen("Avvio dell'analisi 'What-If'...\n");

        // Preprocessing iniziale
        // Pulizia nomi attributi e riordino classe no/yes
        this.wekaDatasetA = normalizeAttributes(this.wekaDatasetA);
        this.wekaDatasetA = reorderBugginessValues(this.wekaDatasetA);
        // 1. Identificazione feature e metodo target
        String aFeature = identifyActionableFeature();
        String buggyMethod = findBuggyMethod(aFeature);

        Printer.print("Feature azionabile: " + aFeature + ", Metodo target: " + buggyMethod + "\n");

        // 2. Caricamento modello
        loadedWekaClassifier = loadClassifier(bClassifier.getModelFilePath());

        // 3. Generazione dataset B+, B, C
        Map<String, Instances> datasets = generateWhatIfDatasets(FEATURE_SMELLS);
        // Allineamento Feature (Fondamentale se il modello è stato addestrato con Feature Selection)
        alignAllDatasets(datasets);


        // 4. Predizioni
        Map<String, PredictionResult> predictionResults = runPredictions(datasets);
        predictionResults.put(DATASET_A, predict(wekaDatasetA, DATASET_A));
        // 5. Salvataggio dataset
        saveDatasets(datasets);
        // 6. Salvataggio tabella dei risultati e analisi
        saveResults(predictionResults);
        analyze(predictionResults, aFeature);

        Printer.printlnGreen("Analisi 'What-If' completata.\n");
    }

    /**
     * Normalizza i nomi degli attributi rimuovendo caratteri speciali
     * (Concetto preso da WhatIfDatasetBuilder)
     */
    private Instances normalizeAttributes(Instances data) {
        for (int i = 0; i < data.numAttributes(); i++) {
            String cleanName = data.attribute(i).name().replaceAll("[‘’“”'\"`]", "").trim();
            data.renameAttribute(i, cleanName);
        }
        return data;
    }

    /**
     * Forza l'ordine della classe nominale a {no, yes}
     * (Concetto critico da WhatIfPredictor per evitare errori di classificazione)
     */
    private Instances reorderBugginessValues(Instances data) throws Exception {
        int classIdx = data.classIndex() == -1 ? data.numAttributes() - 1 : data.classIndex();
        data.setClassIndex(classIdx);

        Attribute classAttr = data.classAttribute();
        if (classAttr.indexOfValue("no") == 0 && classAttr.indexOfValue("yes") == 1) return data;

        ArrayList<String> newValues = new ArrayList<>();
        newValues.add("no");
        newValues.add("yes");
        Attribute newClassAttr = new Attribute("Bugginess_New", newValues);
        data.insertAttributeAt(newClassAttr, data.numAttributes());

        int newIdx = data.numAttributes() - 1;
        for (int i = 0; i < data.numInstances(); i++) {
            String val = data.instance(i).stringValue(classIdx).toLowerCase();
            data.instance(i).setValue(newIdx, val.equals("yes") ? "yes" : "no");
        }

        data.setClassIndex(newIdx);
        Remove remove = new Remove();
        remove.setAttributeIndices("" + (classIdx + 1));
        remove.setInputFormat(data);
        Instances filtered = Filter.useFilter(data, remove);
        filtered.renameAttribute(filtered.numAttributes() - 1, "Bugginess");
        filtered.setClassIndex(filtered.numAttributes() - 1);
        return filtered;
    }




    /** Identifica la feature più correlata */
    private String identifyActionableFeature() {
        CorrelationController.FeatureCorrelation best = cc.getBestFeature();
        String aFeature = best.featureName().replaceAll("[‘’“”'\"`]", "").trim();
        Printer.print("Feature azionabile (AFeature): " + aFeature + ", correlazione: " + best.correlation() + "\n");
        return aFeature;
    }

    /** Trova il metodo buggy con valore massimo della feature */
    private String findBuggyMethod(String feature) {
        return cc.findBuggyMethodWithMaxFeature(feature);
    }

    /** Carica il modello Weka, lancia eccezione se fallisce */
    private Classifier loadClassifier(String modelPath) throws Exception {
        if (modelPath == null || modelPath.isEmpty())
            throw new IllegalArgumentException("Percorso del modello del classificatore non valido.");
        Classifier cls = (Classifier) SerializationHelper.read(modelPath);
        Printer.print("Modello del classificatore caricato da: " + modelPath + "\n");
        return cls;
    }

    /** Genera B+, B, C dataset usando un metodo generico */
    private Map<String, Instances> generateWhatIfDatasets(String aFeature) {
        Map<String, Instances> map = new HashMap<>();
        int featureIndex = wekaDatasetA.attribute(aFeature).index();

        // B+: Buggy che hanno la feature > 0
        Instances bPlus = filterDataset(wekaDatasetA,
                inst -> inst.value(featureIndex) > 0 && inst.stringValue(inst.classIndex()).equals("yes"),
                inst -> {}
        );
        map.put(DATASET_BPLUS, bPlus);

        // B: B+ ma con la feature azzerata (What-if scenario)
        Instances bDataset = filterDataset(bPlus, inst -> true, inst -> inst.setValue(featureIndex, 0));
        map.put(DATASET_B, bDataset);

        // C: Istanze che non hanno la feature (Clean)
        Instances cDataset = filterDataset(wekaDatasetA, inst -> inst.value(featureIndex) == 0, inst -> {});
        map.put(DATASET_C, cDataset);

        return map;
    }

    /**
     * Allinea i dataset per assicurarsi che abbiano gli stessi attributi del modello
     * (Gestisce il caso in cui il dataset A abbia releaseID o feature rimosse)
     */
    private void alignAllDatasets(Map<String, Instances> datasets) throws Exception {
        // Rimuoviamo releaseID se presente (Concetto da WhatIfPredictor)
        for (String key : datasets.keySet()) {
            Instances ds = datasets.get(key);
            if (ds.attribute("releaseID") != null) {
                Remove rm = new Remove();
                rm.setAttributeIndices("" + (ds.attribute("releaseID").index() + 1));
                rm.setInputFormat(ds);
                datasets.put(key, Filter.useFilter(ds, rm));
            }
        }
    }



    /** Metodo generico per filtrare e trasformare dataset */
    private Instances filterDataset(Instances base, Predicate<Instance> condition, Consumer<Instance> transform) {
        Instances result = new Instances(base, 0);
        for (int i = 0; i < base.numInstances(); i++) {
            Instance inst = base.instance(i);
            if (condition.test(inst)) {
                Instance copy = (Instance) inst.copy();
                transform.accept(copy);
                copy.setDataset(result);
                result.add(copy);
            }
        }
        Printer.printBlue("Dataset filtrato: " + result.numInstances() + " istanze.\n");
        return result;
    }

    /** Esegue predizioni per tutti i dataset della mappa */
    private Map<String, PredictionResult> runPredictions(Map<String, Instances> datasets) throws Exception {
        Map<String, PredictionResult> results = new HashMap<>();
        for (Map.Entry<String, Instances> entry : datasets.entrySet()) {
            results.put(entry.getKey(), predict(entry.getValue(), entry.getKey()));
        }
        return results;
    }

    /** Predizione singolo dataset */
    private PredictionResult predict(Instances dataToPredict, String datasetName) throws Exception {
        if (dataToPredict == null || dataToPredict.isEmpty()) {
            Printer.printYellow("Dataset '" + datasetName + "' è vuoto.");
            return new PredictionResult(0, 0, 0);
        }

        int actualBuggy = 0, predictedBuggy = 0, correctlyPredictedBuggy = 0;
        int actualNonBuggy = 0, predictedNonBuggy = 0, correctlyPredictedNonBuggy = 0;

        if (dataToPredict.classIndex() == -1) dataToPredict.setClassIndex(dataToPredict.numAttributes() - 1);

        for (int i = 0; i < dataToPredict.numInstances(); i++) {
            Instance inst = dataToPredict.instance(i);
            boolean isActuallyBuggy = inst.stringValue(inst.classIndex()).equals("yes");

            double predictedValue = loadedWekaClassifier.classifyInstance(inst);
            boolean isPredictedBuggy = dataToPredict.classAttribute().value((int) predictedValue).equals("yes");

            if (isActuallyBuggy) actualBuggy++; else actualNonBuggy++;
            if (isPredictedBuggy) {
                predictedBuggy++;
                if (isActuallyBuggy) correctlyPredictedBuggy++;
            } else {
                predictedNonBuggy++;
                if (!isActuallyBuggy) correctlyPredictedNonBuggy++;
            }
        }

        Printer.printBlue("Predizioni per " + datasetName + ": Totale=" + dataToPredict.numInstances() +
                ", Actual Buggy=" + actualBuggy + ", Predicted Buggy=" + predictedBuggy +
                ", Correctly Predicted Buggy=" + correctlyPredictedBuggy);

        return new PredictionResult(actualBuggy, predictedBuggy, correctlyPredictedBuggy,
                actualNonBuggy, predictedNonBuggy, correctlyPredictedNonBuggy);
    }

    /** Salva dataset in CSV */
    private void saveDatasets(Map<String, Instances> datasets) {
        try {
            File dir = new File(OUTPUT_DIR);
            if (!dir.exists() && !dir.mkdirs()) throw new RuntimeException("Impossibile creare directory output.");

            CSVSaver saver = new CSVSaver();
            for (Map.Entry<String, Instances> entry : datasets.entrySet()) {
                String path = OUTPUT_DIR + File.separator + projectName + "_" + entry.getKey() + ".csv";
                saver.setInstances(entry.getValue());
                saver.setFile(new File(path));
                saver.writeBatch();
                Printer.println("Dataset " + entry.getKey() + " salvato in: " + path);
            }
        } catch (Exception e) {
            Printer.errorPrint("Errore nel salvataggio dataset: " + e.getMessage());
        }
    }

    /** Crea e salva tabella dei risultati */
    private void saveResults(Map<String, PredictionResult> results) {
        try {
            StringColumn datasetCol = StringColumn.create("Dataset");
            IntColumn totalCol = IntColumn.create("Total_Instances");
            IntColumn actualBuggyCol = IntColumn.create("Actual_Buggy");
            IntColumn predictedBuggyCol = IntColumn.create("Estimated_Buggy");
            IntColumn actualNonBuggyCol = IntColumn.create("Actual_NonBuggy");
            IntColumn predictedNonBuggyCol = IntColumn.create("Estimated_NonBuggy");
            IntColumn correctBuggyCol = IntColumn.create("Correct_Buggy_Predictions");
            IntColumn correctNonBuggyCol = IntColumn.create("Correct_NonBuggy_Predictions");

            for (Map.Entry<String, PredictionResult> entry : results.entrySet()) {
                String name = entry.getKey();
                PredictionResult r = entry.getValue();
                datasetCol.append(name);
                totalCol.append(r.getTotalInstances());
                actualBuggyCol.append(r.getActualBuggy());
                predictedBuggyCol.append(r.getPredictedBuggy());
                actualNonBuggyCol.append(r.getActualNonBuggy());
                predictedNonBuggyCol.append(r.getPredictedNonBuggy());
                correctBuggyCol.append(r.getCorrectlyPredictedBuggy());
                correctNonBuggyCol.append(r.getCorrectlyPredictedNonBuggy());
            }

            Table table = Table.create("Prediction_Results")
                    .addColumns(datasetCol, totalCol, actualBuggyCol, predictedBuggyCol,
                            actualNonBuggyCol, predictedNonBuggyCol, correctBuggyCol, correctNonBuggyCol);

            String path = OUTPUT_DIR + File.separator + projectName + "_prediction_results.csv";
            table.write().csv(path);
            Printer.print("Tabella risultati salvata in: " + path + "\n");
            Printer.print("\n" + table.print());
        } catch (Exception e) {
            Printer.errorPrint("Errore nel salvataggio risultati: " + e.getMessage());
        }
    }

    /** Analisi dei risultati B+ vs B */
    private void analyze(Map<String, PredictionResult> results, String aFeature) {
        Printer.print("\n--- ANALISI WHAT-IF ---");
        PredictionResult bPlusRes = results.get(DATASET_BPLUS);
        PredictionResult bRes = results.get(DATASET_B);

        if (bPlusRes != null && bRes != null) {
            int preventable = Math.max(bPlusRes.getPredictedBuggy() - bRes.getPredictedBuggy(), 0);
            Printer.print("Metodi buggy previsti B+: " + bPlusRes.getPredictedBuggy());
            Printer.print("Metodi buggy previsti B: " + bRes.getPredictedBuggy());
            Printer.print("RISPOSTA: Circa " + preventable + " metodi difettosi avrebbero potuto essere prevenuti azzerando " + aFeature);

            if (bPlusRes.getPredictedBuggy() > 0) {
                double proportion = (double) preventable / bPlusRes.getPredictedBuggy() * 100;
                Printer.println(String.format("Proporzione: %.2f%% dei metodi buggy con smells", proportion));
            }
        }
    }
}
