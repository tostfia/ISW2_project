package org.apache.controller;


import org.apache.logging.Printer;
import org.apache.utilities.ClassifierFactory;
import tech.tablesaw.api.*;
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
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class WhatIfAnalyzer {

    private final String bClassifier;
    private final Table datasetA;
    private Instances wekaDatasetA;
    private final String projectName;
    private final CorrelationController cc;
    private final int seed ;
    private static final String OUTPUT_DIR = "output";

    // Costanti per feature e dataset
    private static final String FEATURE_SMELLS = "NumberOfCodeSmells";

    private record FeatureRankingRow(
            int rank,
            String feature,
            double value,
            double correlation,
            double pValue
    ) {}


    public WhatIfAnalyzer(String bClassifier, Table datasetA, Instances wekaDatasetA, String projectName, int seed) {
        this.bClassifier = bClassifier;
        this.datasetA = datasetA;
        this.wekaDatasetA = wekaDatasetA;
        this.projectName = projectName;
        this.cc = new CorrelationController(datasetA);
        this.seed = seed;
    }

    public void run() throws Exception {

        Printer.printGreen("Avvio analisi What-If...\n");

        wekaDatasetA = normalizeAttributes(wekaDatasetA);
        wekaDatasetA = reorderBugginessValues(wekaDatasetA);

        CorrelationController.FeatureCorrelation bestFeature = cc.getBestFeature();
        String aFeature = bestFeature.featureName().replaceAll("[‘’“”'\"`]", "").trim();



        CorrelationController.FeatureValue buggyMethod = findBuggyMethod(aFeature);

        saveFeatureRankingToCSV(
                buildFeatureRankingForMethod(buggyMethod.index(), aFeature)
        );
        Printer.print(
                "Feature azionabile: " + aFeature +
                        " (ρ = " + bestFeature.correlation() + "metodo:"+ buggyMethod.methodName() +")\n"
        );


        //--- Crea dataset
        // --- B+: Portion of A with NSmells > 0
        Instances datasetBPlus = filterBySmell(wekaDatasetA, "greater");
        // --- C: Portion of A with NSmells = 0
        Instances datasetC = filterBySmell(wekaDatasetA, "equals");
        // --- B: Like B+ but with NSmells brought to  0
        Instances datasetB = new Instances(datasetBPlus);
        int nSmellsIndex = datasetB.attribute(FEATURE_SMELLS).index();
        if (nSmellsIndex == -1) throw new IllegalStateException("Feature 'NSmells' not found.");
        datasetB.forEach(instance -> instance.setValue(nSmellsIndex , 0));
        // --- Train BClassifier on A (BClassifierA) ---
        Classifier bClassifierA =
                ClassifierFactory.build(bClassifier, seed);
        bClassifierA.buildClassifier(wekaDatasetA);

        // Count "Actual" values
        int actualA = countActualBugs(wekaDatasetA);
        int actualBPlus = countActualBugs(datasetBPlus);
        int actualC = countActualBugs(datasetC);
        int actualB = countActualBugs(datasetB);

        // Count "Estimated" values
        int estimatedA = countBuggyPredictions(bClassifierA, wekaDatasetA);
        int estimatedBPlus = countBuggyPredictions(bClassifierA, datasetBPlus);
        int estimatedC = countBuggyPredictions(bClassifierA, datasetC);
        int estimatedB = countBuggyPredictions(bClassifierA, datasetB);

        String outputDir = String.format("whatIfResults/%s/", projectName.toLowerCase());
        String outputFile = outputDir + "whatIf.csv";
        saveWhatIfResultsToCsv(outputFile,
                actualA, estimatedA,
                actualBPlus, estimatedBPlus,
                actualB, estimatedB,
                actualC, estimatedC);

        saveDatasetToCsv(datasetB, outputDir, "DatasetB.csv");
        saveDatasetToCsv(datasetBPlus, outputDir, "DatasetBplus.csv");
        saveDatasetToCsv(datasetC, outputDir, "DatasetC.csv");

    }
    private Instances filterBySmell(Instances data, String comparison) {
        int attrIndex = data.attribute(FEATURE_SMELLS).index();
        if (attrIndex == -1) {
            throw new IllegalArgumentException("Attribute not found: " + FEATURE_SMELLS);
        }

        Instances filteredData = new Instances(data, 0);

        for (int i = 0; i < data.numInstances(); i++) {
            Instance inst = data.instance(i);
            double currentValue = inst.value(attrIndex);
            boolean conditionMet = false;

            switch (comparison) {
                case "equals":
                    if (currentValue == 0) conditionMet = true;
                    break;
                case "greater":
                    if (currentValue > 0) conditionMet = true;
                    break;
                case "less":
                    if (currentValue < 0) conditionMet = true;
                    break;
                default:
                    throw new IllegalArgumentException("Comparison type not supported: " + comparison);
            }

            if (conditionMet) {
                filteredData.add(inst);
            }
        }
        return filteredData;
    }


    private int countBuggyPredictions(Classifier classifier, Instances data) throws Exception {
        if (data.isEmpty()) return 0;
        int buggyCount = 0;
        int buggyClassIndex = data.classAttribute().indexOfValue("yes");
        for (int i = 0; i < data.numInstances(); i++) {
            if (classifier.classifyInstance(data.instance(i)) == buggyClassIndex) {
                buggyCount++;
            }
        }
        return buggyCount;
    }

    private int countActualBugs(Instances data) {
        if (data.isEmpty()) return 0;
        int actualBuggyCount = 0;
        int buggyClassIndex = data.classAttribute().indexOfValue("yes");
        for (int i = 0; i < data.numInstances(); i++) {
            if (data.instance(i).classValue() == buggyClassIndex) {
                actualBuggyCount++;
            }
        }
        return actualBuggyCount;
    }

    private void saveDatasetToCsv(Instances data, String directoryPath, String fileName) {
        try {
            File dir = new File(directoryPath);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            CSVSaver saver = new CSVSaver();
            saver.setInstances(data);
            saver.setFile(new File(directoryPath + fileName));
            saver.writeBatch();
        } catch (Exception e) {
            Printer.errorPrint(e.getMessage());
        }
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






    /** Trova il metodo buggy con valore massimo della feature */
    private CorrelationController.FeatureValue findBuggyMethod(String feature) {
        return cc.findBuggyMethodWithMaxFeature(feature);
    }
















    private List<FeatureRankingRow> buildFeatureRankingForMethod(
            int methodIndex,
            String aFeature
    ) {

        // Tutte le correlazioni (calcolate una sola volta)
        List<CorrelationController.FeatureCorrelation> correlations =
                cc.computeAndSaveFullRanking(projectName);

        // Map veloce: feature → correlazione
        Map<String, CorrelationController.FeatureCorrelation> corrMap =
                correlations.stream()
                        .collect(Collectors.toMap(
                                CorrelationController.FeatureCorrelation::featureName,
                                c -> c
                        ));

        List<FeatureRankingRow> rows = new ArrayList<>();

        for (String colName : datasetA.columnNames()) {

            if (!datasetA.column(colName).type().equals(ColumnType.DOUBLE)
                    && !datasetA.column(colName).type().equals(ColumnType.INTEGER))
                continue;

            if (!cc.isActionable(colName)) continue;

            var corr = corrMap.get(colName);
            if (corr == null) continue;

            double value = datasetA.numberColumn(colName).getDouble(methodIndex);

            rows.add(new FeatureRankingRow(
                    0, // temporaneo
                    colName,
                    value,
                    corr.correlation(),
                    corr.pValue()
            ));
        }

        // Ordina: AFeature prima, poi |correlation| desc
        rows.sort((a, b) -> {
            if (a.feature.equals(aFeature)) return -1;
            if (b.feature.equals(aFeature)) return 1;
            return Double.compare(
                    Math.abs(b.correlation),
                    Math.abs(a.correlation)
            );
        });

        // Assegna ranking
        for (int i = 0; i < rows.size(); i++) {
            rows.set(i, new FeatureRankingRow(
                    i + 1,
                    rows.get(i).feature,
                    rows.get(i).value,
                    rows.get(i).correlation,
                    rows.get(i).pValue
            ));
        }

        return rows;
    }


    private void saveFeatureRankingToCSV(
            List<FeatureRankingRow> ranking
    ) {

        try {
            IntColumn rankCol = IntColumn.create("Rank");
            StringColumn featureCol = StringColumn.create("Feature");
            DoubleColumn valueCol = DoubleColumn.create("FeatureValue");
            DoubleColumn rhoCol = DoubleColumn.create("SpearmanRho");


            for (FeatureRankingRow r : ranking) {
                rankCol.append(r.rank);
                featureCol.append(r.feature);
                valueCol.append(r.value);
                rhoCol.append(r.correlation);

            }

            Table table = Table.create("Feature_Ranking")
                    .addColumns(rankCol, featureCol, valueCol, rhoCol);

            String path = OUTPUT_DIR + File.separator + projectName + "_feature_ranking.csv";
            table.write().csv(path);

            Printer.printGreen("Ranking feature salvato in: " + path + "\n");

        } catch (Exception e) {
            Printer.errorPrint("Errore nel salvataggio ranking feature: " + e.getMessage());
        }
    }

    public void saveWhatIfResultsToCsv(
            String outputFile,
            int actualA, int estimatedA,
            int actualBPlus, int estimatedBPlus,
            int actualB, int estimatedB,
            int actualC, int estimatedC) {

        try {
            File file = new File(outputFile);
            file.getParentFile().mkdirs();

            StringColumn datasetCol = StringColumn.create("Dataset");
            IntColumn actualCol = IntColumn.create("ActualBuggy");
            IntColumn estimatedCol = IntColumn.create("EstimatedBuggy");
            DoubleColumn errorPercCol = DoubleColumn.create("ErrorPercentage");

            // Helper lambda
            BiConsumer<String, int[]> addRow = (name, values) -> {
                int actual = values[0];
                int estimated = values[1];

                double errorPerc = (actual == 0)
                        ? 0.0
                        : Math.abs(estimated - actual) / (double) actual;

                datasetCol.append(name);
                actualCol.append(actual);
                estimatedCol.append(estimated);
                errorPercCol.append(errorPerc);
            };

            addRow.accept("A", new int[]{actualA, estimatedA});
            addRow.accept("B+", new int[]{actualBPlus, estimatedBPlus});
            addRow.accept("B", new int[]{actualB, estimatedB});
            addRow.accept("C", new int[]{actualC, estimatedC});

            Table table = Table.create("WhatIfResults")
                    .addColumns(datasetCol, actualCol, estimatedCol, errorPercCol);

            table.write().csv(outputFile);

            Printer.printGreen(
                    "What-If results salvati in: " + outputFile + "\n"
            );

        } catch (Exception e) {
            Printer.errorPrint(
                    "Errore nel salvataggio What-If CSV: " + e.getMessage()
            );
        }
    }



}