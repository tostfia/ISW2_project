package org.apache.controller.milestone2;

import org.apache.logging.Printer;
import org.apache.model.AggregatedClassifierResult;
import org.apache.model.AggregatedClassifierResultStore;
import weka.attributeSelection.*;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.meta.FilteredClassifier;
import weka.core.Instance;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.supervised.instance.SMOTE;
import weka.filters.unsupervised.attribute.Remove;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class WekaController {


    private final String projectName;
    private static final int SEED = 42;

    public WekaController(String projectName) {
        this.projectName = projectName;
    }

    /* =========================
       CROSS VALIDATION 10x10
       ========================= */
    public AggregatedClassifierResult runCrossValidation(
            Classifier cls,
            Instances data,
            boolean applyFs,   // <-- ora enum
            boolean applySmote,
            boolean applyDownsampling,
            int maxInstances) throws Exception {

        final int folds = 10;
        final int repeats = 10;

        // Rimuove attributo Release se presente
        int releaseIdx = data.attribute("Release") != null
                ? data.attribute("Release").index()
                : -1;
        if (releaseIdx != -1) {
            Remove remove = new Remove();
            remove.setAttributeIndicesArray(new int[]{releaseIdx});
            remove.setInputFormat(data);
            data = Filter.useFilter(data, remove);
        }

        String modelName = getBaseClassifierName(cls);
        AggregatedClassifierResult aggregated =
                new AggregatedClassifierResult(projectName, modelName);

        for (int r = 0; r < repeats; r++) {
            Printer.printlnGreen("=== CV repetition " + (r + 1) + "/" + repeats + " ===");

            // Somme per questa repetition
            double sumPrecisionRep = 0;
            double sumRecallRep = 0;
            double sumF1Rep = 0;
            double sumAUCRep = 0;
            double sumKappaRep = 0;
            double sumNPofB20Rep = 0;
            double sumAccuracyRep = 0;
            int validFolds = 0;

            Instances trainFull = new Instances(data);
            trainFull.randomize(new Random(SEED + r));
            if (trainFull.classAttribute().isNominal()) {
                trainFull.stratify(folds);
            }

            int startInst = trainFull.numInstances();

            // =========================
            // FEATURE SELECTION FILTRANTE
            // =========================
            if (applyFs) {
                Printer.printlnBlue("[R" + (r + 1) + "] Applicazione InfoGain prima del classificatore");
                trainFull = applyFeatureSelection(trainFull);
            }
            int afterFS = trainFull.numInstances();

            // =========================
            // SMOTE se necessario
            // =========================
            if (projectName.equalsIgnoreCase("BOOKKEEPER")) {
                applySmote = true;
            }
            if (applySmote) {
                Printer.printlnBlue("[R" + (r + 1) + "] Applicazione SMOTE");
                trainFull = applySMOTE(trainFull);
            }
            int afterSMOTE = trainFull.numInstances();

            // =========================
            // DOWNSAMPLING se necessario
            // =========================
            if (applyDownsampling) {
                Printer.printlnBlue("[R" + (r + 1) + "] Applicazione downsampling a " + maxInstances + " istanze");
                trainFull = downsample(trainFull, maxInstances);
            }
            int afterDownsampling = trainFull.numInstances();

            Printer.printlnBlue(
                    "[R" + (r + 1) + "] Instances: start=" + startInst +
                            ", afterFS=" + afterFS +
                            ", afterSMOTE=" + afterSMOTE +
                            ", afterDownsampling=" + afterDownsampling +
                            ", final=" + trainFull.numInstances()
            );
            int featuresNumber = Math.max(0, trainFull.numAttributes() - 1);

            int foldSize = trainFull.numInstances() / folds;
            int buggyClassIndex = trainFull.classAttribute().indexOfValue("yes");

            for (int f = 0; f < folds; f++) {
                int start = f * foldSize;
                int end = (f == folds - 1)
                        ? trainFull.numInstances()
                        : start + foldSize;

                Instances test = new Instances(trainFull, start, end - start);
                Instances train = new Instances(trainFull);
                for (int i = end - 1; i >= start; i--) {
                    train.delete(i);
                }

                // =========================
                // LOG FEATURE COUNT
                // =========================
                int featuresTrain = Math.max(0, train.numAttributes());
                int featuresTest = Math.max(0, test.numAttributes());
                int featuresAfterFS = Math.max(0, trainFull.numAttributes());
                Printer.printlnGreen(String.format("[R%d F%d] Features: train=%d, test=%d, afterFS=%d",
                        r, f, featuresTrain, featuresTest, featuresAfterFS));

                // =========================
                // COSTRUZIONE CLASSIFICATORE
                // =========================
                Classifier clsCopy = weka.classifiers.AbstractClassifier.makeCopy(cls);
                clsCopy.buildClassifier(train);
                Printer.printlnGreen("[R" + (r + 1) + " F" + (f + 1) + "] Classificatore pronto: " +
                        clsCopy.getClass().getSimpleName());

                // =========================
                // VALUTAZIONE
                // =========================
                Evaluation eval = new Evaluation(train);
                eval.evaluateModel(clsCopy, test);

                double npofb20 = computeNPofB20(clsCopy, test);

                double precision = eval.precision(buggyClassIndex);
                double recall = eval.recall(buggyClassIndex);
                double f1 = eval.fMeasure(buggyClassIndex);
                double auc = eval.areaUnderROC(buggyClassIndex);
                double kappa = eval.kappa();
                double accuracy = eval.pctCorrect() / 100.0;

                writeFoldResultToCSV(
                        "results_fold" + projectName + ".csv",
                        modelName,
                        applyFs ? "InfoGain" : "None",
                        featuresTrain,
                        f + 1,
                        precision,
                        recall,
                        f1,
                        auc,
                        kappa,
                        accuracy,
                        npofb20
                );

                // Accumula metriche
                if (!Double.isNaN(precision)) sumPrecisionRep += precision;
                if (!Double.isNaN(recall)) sumRecallRep += recall;
                if (!Double.isNaN(f1)) sumF1Rep += f1;
                if (!Double.isNaN(auc)) sumAUCRep += auc;
                if (!Double.isNaN(kappa)) sumKappaRep += kappa;
                if (!Double.isNaN(accuracy)) sumAccuracyRep += accuracy;
                if (!Double.isNaN(npofb20)) sumNPofB20Rep += npofb20;
                validFolds++;

                Printer.printlnBlue(String.format("[R%d F%d] Precision=%.3f, Recall=%.3f, F1=%.3f, AUC=%.3f, Kappa=%.3f, NPofB20=%.3f",
                        r, f, precision, recall, f1, auc, kappa, npofb20));
            }

            // =========================
            // MEDIE REPETITION
            // =========================
            double avgPrecisionRep = sumPrecisionRep / validFolds;
            double avgRecallRep = sumRecallRep / validFolds;
            double avgF1Rep = sumF1Rep / validFolds;
            double avgAUCRep = sumAUCRep / validFolds;
            double avgKappaRep = sumKappaRep / validFolds;
            double avgNPofB20Rep = sumNPofB20Rep / validFolds;
            double avgAccuracyRep = sumAccuracyRep / validFolds;

            aggregated.addRunResult(
                    avgPrecisionRep,
                    avgRecallRep,
                    avgF1Rep,
                    avgAUCRep,
                    avgKappaRep,
                    avgAccuracyRep,
                    avgNPofB20Rep
            );

            Printer.printlnGreen("[R" + (r + 1) + " SUMMARY] Precision=%.3f, Recall=%.3f, F1=%.3f, AUC=%.3f, Kappa=%.3f, NPofB20=%.3f"
                    .formatted(avgPrecisionRep, avgRecallRep, avgF1Rep, avgAUCRep, avgKappaRep, avgNPofB20Rep));
            /*writeRepetionSummaryToCSV(
                    "results_rep" + projectName + ".csv",
                    modelName,
                    applyFs ? "InfoGain" : "None",
                    featuresNumber,
                    r + 1,
                    avgPrecisionRep,
                    avgRecallRep,
                    avgF1Rep,
                    avgAUCRep,
                    avgKappaRep,
                    avgAccuracyRep,
                    avgNPofB20Rep
            );*/


        }


        addResult(aggregated);
        return aggregated;
    }

    /* =========================
       MODEL NAME UTILS
       ========================= */
    private String getBaseClassifierName(Classifier cls) {
        if (cls instanceof FilteredClassifier) {
            return ((FilteredClassifier) cls)
                    .getClassifier()
                    .getClass()
                    .getSimpleName();
        }
        return cls.getClass().getSimpleName();
    }

    /* =========================
       NPofB20
       ========================= */
    public static double computeNPofB20(Classifier cls, Instances test) throws Exception {
        int yesIdx = test.classAttribute().indexOfValue("yes");
        if (yesIdx == -1) throw new IllegalArgumentException("Classe 'yes' non presente");

        List<double[]> scored = new ArrayList<>();
        int totalBuggy = 0;

        for (int i = 0; i < test.numInstances(); i++) {
            Instance inst = test.instance(i);
            double score = cls.distributionForInstance(inst)[yesIdx];
            double actual = inst.classValue();
            if ((int) actual == yesIdx) totalBuggy++;
            scored.add(new double[]{score, actual});
        }

        scored.sort((a, b) -> Double.compare(b[0], a[0]));

        int topN = Math.min((int) Math.ceil(test.numInstances() * 0.2), totalBuggy);
        int foundBuggy = 0;

        for (int i = 0; i < topN; i++) {
            if ((int) scored.get(i)[1] == yesIdx) {
                foundBuggy++;
            }
        }

        return totalBuggy == 0 ? 0.0 : (double) foundBuggy / totalBuggy;
    }

    /* =========================
       PREPROCESSING: INFOGAIN
       ========================= */
    public Instances applyFeatureSelection(Instances data) throws Exception {
        AttributeSelection selector = new AttributeSelection();
        InfoGainAttributeEval evaluator = new InfoGainAttributeEval();

        Ranker search = new Ranker();
        search.setThreshold(0.01);

        selector.setEvaluator(evaluator);
        selector.setSearch(search);
        selector.SelectAttributes(data);

        int[] selected = selector.selectedAttributes();
        Printer.printGreen("Selected attributes:" + Arrays.toString(selected));
        return selector.reduceDimensionality(data);
    }

    /* =========================
       SMOTE
       ========================= */
    public Instances applySMOTE(Instances data) throws Exception {
        SMOTE smote = new SMOTE();
        smote.setPercentage(65);
        smote.setNearestNeighbors(5);
        smote.setInputFormat(data);
        return Filter.useFilter(data, smote);
    }

    /* =========================
       DOWNSAMPLING
       ========================= */
    public Instances downsample(Instances data, int maxInstances) {
        if (data.numInstances() <= maxInstances) return new Instances(data);
        Instances rand = new Instances(data);
        rand.randomize(new Random(SEED));
        return new Instances(rand, 0, maxInstances);
    }

    /* =========================
       SAVE RESULTS
       ========================= */
    public void addResult(AggregatedClassifierResult result) {
        try {
            Collection<AggregatedClassifierResult> existing =
                    AggregatedClassifierResultStore.load(projectName);
            existing.add(result);
            AggregatedClassifierResultStore.save(projectName, existing);
        } catch (Exception e) {
            Printer.printYellow("Failed saving results: " + e.getMessage());
        }
    }

    public void writeFoldResultToCSV(
            String csvPath,
            String modelName,
            String featureSelection,
            int featuresNumber,
            int fold,
            double precision,
            double recall,
            double f1,
            double auc,
            double kappa,
            double accuracy,
            double npofb20
    ) throws IOException {


        boolean writeHeader = !new java.io.File(csvPath).exists();

        try (FileWriter writer = new FileWriter(csvPath, true)) {
            if (writeHeader) {
                writer.append("Model,FeatureSelection,FeaturesNumber,Fold,")
                        .append("Precision,Recall,F1,AUC,Kappa,Accuracy,NPofB20\n");
            }

            writer.append(modelName).append(",")
                    .append(featureSelection).append(",")
                    .append(String.valueOf(featuresNumber)).append(",")
                    .append(String.valueOf(fold)).append(",")
                    .append(String.format(Locale.US, "%.3f", precision)).append(",")
                    .append(String.format(Locale.US, "%.3f", recall)).append(",")
                    .append(String.format(Locale.US, "%.3f", f1)).append(",")
                    .append(String.format(Locale.US, "%.3f", auc)).append(",")
                    .append(String.format(Locale.US, "%.3f", kappa)).append(",")
                    .append(String.format(Locale.US, "%.3f", accuracy)).append(",")
                    .append(String.format(Locale.US, "%.3f", npofb20)).append("\n");
        }
    }


    /*public void writeRepetionSummaryToCSV(
            String csvPath,
            String modelName,
            String featureSelection,
            int featuresNumber,
            int repetition,
            double precision,
            double recall,
            double f1,
            double auc,
            double kappa,
            double accuracy,
            double npofb20
    ) throws IOException {



        boolean writeHeader = !new java.io.File(csvPath).exists();

        try (FileWriter writer = new FileWriter(csvPath, true)) {

            if (writeHeader) {
                writer.append("Model,FeatureSelection,FeaturesNumber,Repetition,")
                        .append("AvgPrecision,AvgRecall,AvgF1,AvgAUC,AvgKappa, AvgAccuracy,AvgNPofB20\n");
            }

            writer.append(modelName).append(",")
                    .append(featureSelection).append(",")
                    .append(String.valueOf(featuresNumber)).append(",")
                    .append(String.valueOf(repetition)).append(",")
                    .append(String.format(Locale.US, "%.3f", precision)).append(",")
                    .append(String.format(Locale.US, "%.3f", recall)).append(",")
                    .append(String.format(Locale.US, "%.3f", f1)).append(",")
                    .append(String.format(Locale.US, "%.3f", auc)).append(",")
                    .append(String.format(Locale.US, "%.3f", kappa)).append(",")
                    .append(String.format(Locale.US, "%.3f", accuracy)).append(",")
                    .append(String.format(Locale.US, "%.3f", npofb20)).append("\n");

        }
    }*/

}
