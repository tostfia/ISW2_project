package org.apache;

import org.apache.controller.WekaController;
import org.apache.controller.DatasetController;
import org.apache.controller.ReportAnalyzer;
import org.apache.controller.WhatIfAnalyzer;
import org.apache.controller.milestone1.JiraController;
import org.apache.logging.Printer;
import org.apache.model.AggregatedClassifierResult;
import org.apache.model.Release;
import org.apache.utilities.ClassifierFactory;
import tech.tablesaw.api.Table;
import weka.classifiers.Classifier;
import weka.core.Instances;
import weka.core.SerializationHelper;

import java.util.List;

public class Main {

    private static final double DEFAULT_CUT_PERCENTAGE = 0.34;
    private static final int SEED = 42;

    public static void main(String[] args) throws Exception {

        if (args.length == 0) {
            Printer.errorPrint("Project name required.");
            return;
        }

        String projectName = args[0];
        Printer.printlnGreen("START ANALYSIS: " + projectName);

        // =========================
        // Step 0: Jira & Releases
        // =========================
        JiraController jira = new JiraController(projectName);
        jira.injectRelease();
        List<Release> releases = jira.getRealeases();
        if (releases.isEmpty()) {
            Printer.errorPrint("No releases found for project " + projectName);
            return;
        }

        double cutPercentage = getCutPercentage();

        // =========================
        // Step 0.1: Dataset A
        // =========================
        DatasetController datasetController = new DatasetController(projectName);
        Table datasetA = datasetController.prepareDatasetA(cutPercentage);
        if (datasetA.isEmpty()) {
            Printer.errorPrint("Dataset A is empty.");
            return;
        }

        // =========================
        // Step 1: CROSS-VALIDATION → MODEL & FEATURE SELECTION
        // =========================
        Printer.printlnGreen("STEP 1: Cross-Validation for classifier & feature selection");
        long start= System.currentTimeMillis();
        Instances allData = datasetController.convertTablesawToWekaInstances(
                datasetA,
                releases.stream().map(Release::getReleaseName).toList(),
                projectName + "_CV"
        );
        allData.setClassIndex(allData.numAttributes() - 1);

        WekaController cvController = new WekaController(projectName); // CV controller

        // Esegui CV su tutti i classificatori definiti in ClassifierFactory
        List<String> classifiersToTest = List.of("NaiveBayes", "RandomForest", "IBk");
        for (String clsName : classifiersToTest) {
            Classifier cls = ClassifierFactory.build(clsName, SEED);
            boolean applyFS = true;      // sempre InfoGain
            boolean applySmote = false;
            boolean applyDownsampling = false;
            int maxInstances = Integer.MAX_VALUE;
            if (projectName.equalsIgnoreCase("STORM")) {
                applyDownsampling = true;
                maxInstances = 20000; // downsampling per dataset grande
            } else if (clsName.equals("RandomForest")) {
                applySmote = true; // oversampling solo su RF per progetti più piccoli
            }

            AggregatedClassifierResult cvResult = cvController.runCrossValidation(
                    cls,
                    allData,
                    applyFS,
                    applySmote,
                    applyDownsampling,
                    maxInstances
            );

            cvController.addResult(cvResult); // memorizza i risultati CV
        }

        // Recupera il miglior classificatore dai risultati CV
        ReportAnalyzer analyzer = new ReportAnalyzer(projectName);
        AggregatedClassifierResult best = analyzer.getBestClassifierByCompositeScore();
        analyzer.printRanking();
        Printer.println("Best classifier (from CV): " + best.getClassifierName());
        long middle = System.currentTimeMillis();
        long tot= start-middle;
        Printer.print("Sono passati "+ tot);
        // =========================
        // Step 2: TRAIN FINAL MODEL
        // =========================
        Printer.printlnGreen("STEP 2: Training final model on full dataset (no leakage)");

        Instances finalTraining = datasetController.convertTablesawToWekaInstances(
                datasetA,
                releases.stream().map(Release::getReleaseName).toList(),
                projectName + "_final"
        );
        finalTraining.setClassIndex(finalTraining.numAttributes() - 1);



        Classifier finalClassifier = ClassifierFactory.build(best.getClassifierName(), SEED);
        finalClassifier.buildClassifier(finalTraining);
        long middle1=System.currentTimeMillis();
        long tot1= start-middle1;
        Printer.print("Sono passati "+ tot1);
        String modelPath = "models/" + projectName + "_best.model";
        SerializationHelper.write(modelPath, finalClassifier);
        best.setModelFilePath(modelPath);

        Printer.printlnGreen("Final model saved to: " + modelPath);

        // =========================
        // Step 3: WHAT-IF ANALYSIS
        // =========================
        Printer.printlnGreen("STEP 3: Running What-If Analysis");
        WhatIfAnalyzer whatIf = new WhatIfAnalyzer(best, datasetA, finalTraining, projectName);
        whatIf.run();

        Printer.printlnGreen("ANALYSIS COMPLETED for project: " + projectName);
    }

    private static double getCutPercentage() {
        try {
            return Double.parseDouble(System.getenv().getOrDefault("SYS_CUT_PERCENTAGE",
                    String.valueOf(DEFAULT_CUT_PERCENTAGE)));
        } catch (Exception e) {
            return DEFAULT_CUT_PERCENTAGE;
        }
    }
}
