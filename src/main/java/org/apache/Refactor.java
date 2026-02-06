package org.apache;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import org.apache.controller.milestone1.MetricsController;
import org.apache.logging.Printer;
import org.apache.model.AnalyzedClass;
import org.apache.model.Release;
import org.apache.utilities.writer.CsvWriter;
import weka.attributeSelection.InfoGainAttributeEval;
import weka.attributeSelection.Ranker;
import weka.classifiers.Classifier;
import weka.classifiers.meta.Bagging;
import weka.classifiers.misc.InputMappedClassifier;
import weka.classifiers.trees.RandomTree;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.CSVLoader;
import weka.core.converters.ConverterUtils.DataSource;
import weka.filters.Filter;
import weka.filters.supervised.attribute.AttributeSelection;
import weka.filters.supervised.instance.SMOTE;
import weka.filters.unsupervised.attribute.Remove;
import weka.filters.unsupervised.attribute.RemoveType;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Refactor {

    private static final String STORM_CLASS_PATH = "refactor/JdbcClient.java";
    private static final String BOOKKEEPER_CLASS_PATH = "refactor/BookieServer.java";

    private static final String STORM_CSV = "refactor/storm_refactored_metrics.csv";
    private static final String BOOKKEEPER_CSV = "refactor/bookkeeper_refactored_metrics.csv";

    private static final String STORM_CLEANED_CSV = "refactor/storm_cleaned_temp.csv";
    private static final String BOOKKEEPER_CLEANED_CSV = "refactor/bookkeeper_cleaned_temp.csv";

    public static void main(String[] args) throws Exception {

        Printer.printlnGreen("\n=== REFACTOR ANALYSIS - FASE 1: CALCOLO METRICHE ===\n");

        // 1. Carico i file sorgente
        String stormCode = Files.readString(Paths.get(STORM_CLASS_PATH));
        String bookkeeperCode = Files.readString(Paths.get(BOOKKEEPER_CLASS_PATH));

        // 2. Creo le release
        Release releaseStorm = new Release("0.10.2", "Apache Storm", "2026-01-04");
        Release releaseBookkeeper = new Release("4.2.1", "Apache Bookkeeper", "2024-05-14");

        // 3. Analizzo STORM
        List<AnalyzedClass> stormSnapshot = new ArrayList<>();
        stormSnapshot.add(new AnalyzedClass("JdbcClient", stormCode, releaseStorm,
                "org.apache.storm.jdbc.common", "JdbcClient.java"));

        MetricsController mcStorm = new MetricsController(stormSnapshot, null);
        mcStorm.processMetrics();

        try (CsvWriter csvWriter = new CsvWriter(STORM_CSV, "Storm")) {
            csvWriter.writeResultsForClass(stormSnapshot);
            Printer.printlnGreen("✓ Metriche STORM salvate in: " + STORM_CSV);
        } catch (IOException e) {
            Printer.errorPrint("Errore salvataggio metriche STORM: " + e.getMessage());
            return;
        }

        // 4. Analizzo BOOKKEEPER
        List<AnalyzedClass> bookkeeperSnapshot = new ArrayList<>();
        bookkeeperSnapshot.add(new AnalyzedClass("BookieServer", bookkeeperCode, releaseBookkeeper,
                "org.apache.bookkeeper.bookie", "BookieServer.java"));

        MetricsController mcBookkeeper = new MetricsController(bookkeeperSnapshot, null);
        mcBookkeeper.processMetrics();

        try (CsvWriter csvWriter = new CsvWriter(BOOKKEEPER_CSV, "Bookkeeper")) {
            csvWriter.writeResultsForClass(bookkeeperSnapshot);
            Printer.printlnGreen("✓ Metriche BOOKKEEPER salvate in: " + BOOKKEEPER_CSV);
        } catch (IOException e) {
            Printer.errorPrint("Errore salvataggio metriche BOOKKEEPER: " + e.getMessage());
            return;
        }

        Printer.printlnGreen("\n=== REFACTOR ANALYSIS - FASE 2: PREDIZIONI BUGGINESS ===\n");

        // 5. Predici per STORM
        predictWithWhatIfApproach("storm", STORM_CSV, STORM_CLEANED_CSV,
                "refactor/verdetto_storm_refactor.txt");

        // 6. Predici per BOOKKEEPER
        predictWithWhatIfApproach("bookkeeper", BOOKKEEPER_CSV, BOOKKEEPER_CLEANED_CSV,
                "refactor/verdetto_bookkeeper_refactor.txt");

        Printer.printlnGreen("\n=== ANALISI COMPLETATA ===");
    }

    private static void predictWithWhatIfApproach(String project, String refactoredCsvPath,
                                                  String cleanedCsvPath, String reportPath) {
        try {
            Printer.printlnBlue("\n--- Predizioni What-If per " + project.toUpperCase() + " ---");

            String trainingPath = project.toLowerCase() + "/datasetA.csv";

            // 1. PULIZIA CSV DI INPUT
            cleanCsvFile(refactoredCsvPath, cleanedCsvPath);
            Printer.printlnGreen("✓ CSV pulito: " + cleanedCsvPath);

            List<String> methodNames = readMethodNamesFromCsv(cleanedCsvPath);

            // 2. PREPARAZIONE TRAINING SET (ARFF)
            Instances trainRaw = loadTrainingSet(trainingPath);
            if (trainRaw == null) return;

            Instances trainProcessed = preprocessLikeOriginal(trainRaw);
            Printer.printlnGreen("✓ Training preprocessato (feature selection applicata)");
            prepareBugginessAttribute(trainProcessed, true);

            if (project.equalsIgnoreCase("STORM")) trainProcessed = downsample(trainProcessed);
            if (project.equalsIgnoreCase("BOOKKEEPER")) trainProcessed = applySMOTE(trainProcessed);

            // 3. COSTRUZIONE MODELLO
            Classifier baseModel = buildBaggingRandomTree();
            baseModel.buildClassifier(trainProcessed);

            InputMappedClassifier mappedModel = new InputMappedClassifier();
            mappedModel.setClassifier(baseModel);
            mappedModel.setSuppressMappingReport(true);
            mappedModel.buildClassifier(trainProcessed);

            Printer.printlnGreen("✓ Modello addestrato con " +
                    (trainProcessed.numAttributes() - 1) + " feature");

            // 4. TEST SET
            Instances testRaw = loadCsvAsInstances(cleanedCsvPath);
            prepareBugginessAttribute(testRaw, false);
            Printer.printlnGreen("✓ Test set preparato: " + testRaw.numInstances() + " metodi");

            // 5. PREDIZIONI
            executePredictionsAndWriteReport(
                    project, testRaw, trainProcessed, mappedModel, methodNames, reportPath);

            Printer.printlnGreen("\n✓ Report generato in: " + reportPath);

        } catch (Exception e) {
            Printer.errorPrint("ERRORE durante le predizioni per " + project + ": " + e.getMessage());
        }
    }

    private static Instances loadTrainingSet(String trainingPath) {
        try {
            Instances trainRaw = new DataSource(trainingPath).getDataSet();
            if (trainRaw.classIndex() == -1) {
                trainRaw.setClassIndex(trainRaw.numAttributes() - 1);
            }
            return trainRaw;
        } catch (Exception e) {
            Printer.errorPrint("✗ Training set non trovato: " + trainingPath);
            Printer.printYellow("Suggerimento: assicurati che il file ARFF di training esista");
            return null;
        }
    }




    private static List<String> readMethodNamesFromCsv(String cleanedCsvPath) throws IOException {
        List<String> methodNames = new ArrayList<>();
        try (CSVReader reader = new CSVReader(new FileReader(cleanedCsvPath))) {
            String[] line;
            reader.readNext(); // salta header
            while ((line = reader.readNext()) != null) {
                methodNames.add(line[0]); // MethodName è ora la prima colonna
            }
        } catch (CsvValidationException e) {
            Printer.errorPrint(e.getMessage());
        }
        return methodNames;
    }

    private static Instances loadCsvAsInstances(String cleanedCsvPath) throws IOException {
        CSVLoader loader = new CSVLoader();
        loader.setSource(new File(cleanedCsvPath));
        loader.setFieldSeparator(",");
        return loader.getDataSet();
    }

    private static void executePredictionsAndWriteReport(String project, Instances testRaw, Instances trainProcessed,
                                                         InputMappedClassifier mappedModel, List<String> methodNames,
                                                         String reportPath) throws Exception {

        int buggyCount = 0;
        int totalMethods = testRaw.numInstances();

        try (PrintWriter writer = new PrintWriter(reportPath)) {
            writer.println("=== PREDIZIONI WHAT-IF (MODELLO RIFATTORIZZATO) ===");
            writer.println("Progetto: " + project.toUpperCase());
            writer.println("Data: " + new java.util.Date());
            writer.println("Feature utilizzate: " + (trainProcessed.numAttributes() - 1));
            writer.println("--------------------------------------------------");
            writer.println();

            Printer.printYellow("\nRisultati predizioni:");
            Printer.printYellow(String.format("%-30s | %-10s | %-12s", "Metodo", "Predizione", "Prob YES"));
            Printer.printYellow("-".repeat(60));

            for (int i = 0; i < testRaw.numInstances(); i++) {
                Instance inst = testRaw.instance(i);

                double[] distribution = mappedModel.distributionForInstance(inst);
                double pred = mappedModel.classifyInstance(inst);
                String label = trainProcessed.classAttribute().value((int) pred);
                double probYes = distribution[1];

                String methodName = methodNames.get(i);
                writer.printf("Metodo: %-30s | Pred: %-5s | Prob YES: %.2f%%%n",
                        methodName, label.toUpperCase(), probYes * 100);

                if ("yes".equalsIgnoreCase(label)) {
                    buggyCount++;
                    Printer.errorPrint(String.format("%-30s | %-10s | %.2f%%", truncate(methodName), "BUGGY", probYes * 100));
                } else {
                    Printer.printGreen(String.format("%-30s | %-10s | %.2f%%", truncate(methodName), "OK", probYes * 100));
                }
            }

            writer.println();
            writer.println("--------------------------------------------------");
            writer.println("RIEPILOGO:");
            writer.println("Totale metodi analizzati: " + totalMethods);
            writer.println("Metodi predetti come BUGGY: " + buggyCount +
                    " (" + String.format("%.1f%%", 100.0 * buggyCount / totalMethods) + ")");
            writer.println("Metodi predetti come OK: " + (totalMethods - buggyCount) +
                    " (" + String.format("%.1f%%", 100.0 * (totalMethods - buggyCount) / totalMethods) + ")");
        }
    }



    private static void cleanCsvFile(String sourcePath, String destPath) throws IOException, CsvValidationException {

        CSVReader reader = new CSVReader(new FileReader(sourcePath));
        CSVWriter writer = new CSVWriter(new FileWriter(destPath));

        String[] line;
        String[] header = {"LOC","ParameterCount","CycloComplexity","CognitiveComplexity","NestingDepth","NumberOfCodeSmells"};
        writer.writeNext(header);

        while ((line = reader.readNext()) != null) {
            if (line.length >= 14) {
                String[] cleanedLine = new String[] {
                        line[3],  // LOC
                        line[4],  // ParameterCount
                        line[5],  // CycloComplexity
                        line[6],  // CognitiveComplexity
                        line[7],  // NestingDepth
                        line[13]  // NumberOfCodeSmells
                };
                writer.writeNext(cleanedLine);
            }
        }

        reader.close();
        writer.close();

    }


    private static Instances preprocessLikeOriginal(Instances data) throws Exception {
        // 1. Rimuovi Stringhe
        RemoveType removeStrings = new RemoveType();
        removeStrings.setOptions(new String[]{"-T", "string"});
        removeStrings.setInputFormat(data);
        data = Filter.useFilter(data, removeStrings);

        // 2. RIMOZIONE METRICHE STORICHE (non azionabili per What-If)
        String[] toDelete = {
                // Metriche storiche da CsvWriter
                "Revisions", "Authors", "TotalChurn", "MaxChurn", "AvgChurn",
                // Altre metriche storiche possibili
                "Release"
        };
        for (String colName : toDelete) {
            Attribute attr = data.attribute(colName);
            if (attr != null) {
                Remove rm = new Remove();
                rm.setAttributeIndices("" + (attr.index() + 1));
                rm.setInputFormat(data);
                data = Filter.useFilter(data, rm);
            }
        }

        // 3. FEATURE SELECTION (Information Gain)
        AttributeSelection fs = new AttributeSelection();
        InfoGainAttributeEval eval = new InfoGainAttributeEval();
        Ranker search = new Ranker();
        search.setThreshold(0.00);
        fs.setEvaluator(eval);
        fs.setSearch(search);
        fs.setInputFormat(data);
        data = Filter.useFilter(data, fs);


        return data;
    }

    /**
     * Prepara l'attributo Bugginess con valori {no, yes}
     */
    private static void prepareBugginessAttribute(Instances data, boolean isTraining) {
        List<String> values = new ArrayList<>();
        values.add("no");
        values.add("yes");
        Attribute newClassAttr = new Attribute("Bugginess", values);

        if (isTraining) {
            // 1. Recuperiamo l'indice della vecchia classe
            int oldIdx = data.classIndex();
            if (oldIdx == -1) oldIdx = data.numAttributes() - 1;

            // 2. IMPORTANTE: Reset dell'indice della classe prima di modificare/eliminare
            data.setClassIndex(-1);

            data.renameAttribute(oldIdx, "Old_Bugginess");
            data.insertAttributeAt(newClassAttr, data.numAttributes());

            int updatedOldIdx = data.attribute("Old_Bugginess").index();
            int newIdx = data.numAttributes() - 1;

            for (int i = 0; i < data.numInstances(); i++) {
                String val = data.instance(i).stringValue(updatedOldIdx).toLowerCase();
                data.instance(i).setValue(newIdx, (val.contains("yes") || val.contains("true")) ? "yes" : "no");
            }

            // 4. Ora possiamo eliminare Old_Bugginess perché non è più la "class"
            data.deleteAttributeAt(updatedOldIdx);
        } else {
            // Nel Test (CSV), aggiungiamo solo se manca
            if (data.attribute("Bugginess") == null) {
                data.insertAttributeAt(newClassAttr, data.numAttributes());
            }
        }

        data.setClassIndex(data.numAttributes() - 1);
    }


    public static Classifier buildBaggingRandomTree()  {
        // 1. Crea il RandomTree base learner
        RandomTree baseTree = new RandomTree();
        baseTree.setKValue(0);        // consider all features
        baseTree.setMinNum(1);        // min instances per leaf
        baseTree.setMaxDepth(0);      // 0 = unlimited
        baseTree.setDoNotCheckCapabilities(true);
        baseTree.setSeed(42);
        baseTree.setNumFolds(0);      // usato per reduced-error pruning
        baseTree.setMinVarianceProp(0.001); // -V 0.001

        // 2. Crea il Bagging meta-classifier
        Bagging bagger = new Bagging();
        bagger.setClassifier(baseTree);
        bagger.setNumIterations(100);  // 100 alberi
        bagger.setSeed(42);
        bagger.setCalcOutOfBag(false); // puoi mettere true se vuoi OOB estimate
        bagger.setBagSizePercent(100); // ogni albero usa il 100% del campione bootstrap

        return bagger;
    }

    /**
     * Downsampling per dataset molto grandi
     */
    private static Instances downsample(Instances data) {
        if (data.size() <= 20000) return data;
        data.randomize(new java.util.Random(42));
        return new Instances(data, 0, 20000);
    }
    private static Instances applySMOTE(Instances data) throws Exception {
        SMOTE smote = new SMOTE();
        smote.setInputFormat(data);
        smote.setPercentage(65.0);
        return Filter.useFilter(data, smote);
    }



    /**
     * Tronca una stringa a una lunghezza massima
     */
    private static String truncate(String str) {
        if (str == null) return "";
        if (str.length() <= 30) return str;
        return str.substring(0, 30 - 3) + "...";
    }


}