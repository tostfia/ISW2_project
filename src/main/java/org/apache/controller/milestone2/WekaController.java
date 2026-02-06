package org.apache.controller.milestone2;

import org.apache.logging.Printer;
import org.apache.model.AggregatedClassifierResult;
import org.apache.model.AggregatedClassifierResultStore;
import weka.attributeSelection.*;
import weka.classifiers.AbstractClassifier;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.meta.FilteredClassifier;
import weka.core.Instance;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.supervised.instance.SMOTE;
import weka.filters.unsupervised.attribute.Remove;
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
            boolean applyFs,
            boolean applySmote,
            boolean applyDownsampling,
            int maxInstances) throws Exception {

        final int folds = 10;
        final int repeats = 10;

        // Rimuove attributo Release se presente
        int releaseIdx = data.attribute("Release") != null ? data.attribute("Release").index() : -1;
        if (releaseIdx != -1) {
            Remove remove = new Remove();
            remove.setAttributeIndicesArray(new int[]{releaseIdx});
            remove.setInputFormat(data);
            data = Filter.useFilter(data, remove);
        }

        String modelName = getBaseClassifierName(cls);
        AggregatedClassifierResult aggregated = new AggregatedClassifierResult(projectName, modelName);

        for (int r = 0; r < repeats; r++) {
            Printer.printlnGreen("=== CV repetition " + (r + 1) + "/" + repeats + " ===");

            Instances trainFull = new Instances(data);
            // Sostituire la riga 58 con:
            trainFull.randomize(new Random((long) SEED + r));
            if (trainFull.classAttribute().isNominal()) trainFull.stratify(folds);

            trainFull = preprocess(trainFull, applyFs, applySmote, applyDownsampling, maxInstances, r);

            int foldSize = trainFull.numInstances() / folds;
            int buggyClassIndex = trainFull.classAttribute().indexOfValue("yes");

            // Somme metriche per repetition
            double sumPrecisionRep = 0;
            double sumRecallRep = 0;
            double sumF1Rep = 0;
            double sumAUCRep = 0;
            double sumKappaRep = 0;
            double sumNPofB20Rep = 0;
            double sumAccuracyRep = 0;
            int validFolds = 0;

            for (int f = 0; f < folds; f++) {
                Instances[] foldData = prepareFoldData(trainFull, f, foldSize);
                Instances train = foldData[0];
                Instances test = foldData[1];

                Classifier clsCopy = buildAndTrainClassifier(cls, train, r, f);

                double[] metrics = evaluateFold(clsCopy, train, test, buggyClassIndex, r, f);

                sumPrecisionRep += metrics[0];
                sumRecallRep += metrics[1];
                sumF1Rep += metrics[2];
                sumAUCRep += metrics[3];
                sumKappaRep += metrics[4];
                sumAccuracyRep += metrics[5];
                sumNPofB20Rep += metrics[6];
                validFolds++;
            }

            // Medie per repetition
            aggregated.addRunResult(
                    sumPrecisionRep / validFolds,
                    sumRecallRep / validFolds,
                    sumF1Rep / validFolds,
                    sumAUCRep / validFolds,
                    sumKappaRep / validFolds,
                    sumAccuracyRep / validFolds,
                    sumNPofB20Rep / validFolds
            );


        }

        addResult(aggregated);
        return aggregated;
    }


    private Instances preprocess(Instances data, boolean applyFs, boolean applySmote,
                                 boolean applyDownsampling, int maxInstances, int r) throws Exception {

        int startInst = data.numInstances();

        if (applyFs) {
            Printer.printlnBlue("[R" + (r + 1) + "] Applicazione InfoGain prima del classificatore");
            data = applyFeatureSelection(data);
        }

        if (projectName.equalsIgnoreCase("BOOKKEEPER")) applySmote = true;
        if (applySmote) {
            Printer.printlnBlue("[R" + (r + 1) + "] Applicazione SMOTE");
            data = applySMOTE(data);
        }

        if (applyDownsampling) {
            Printer.printlnBlue("[R" + (r + 1) + "] Applicazione downsampling a " + maxInstances + " istanze");
            data = downsample(data, maxInstances);
        }

        Printer.printlnBlue("[R" + (r + 1) + "] Instances: start=" + startInst + ", final=" + data.numInstances());
        return data;
    }

    private Instances[] prepareFoldData(Instances trainFull, int foldIndex, int foldSize) {
        int start = foldIndex * foldSize;
        int end = (foldIndex == 9) ? trainFull.numInstances() : start + foldSize;

        Instances test = new Instances(trainFull, start, end - start);
        Instances train = new Instances(trainFull);
        for (int i = end - 1; i >= start; i--) train.delete(i);

        return new Instances[]{train, test};
    }

    private Classifier buildAndTrainClassifier(Classifier cls, Instances train, int r, int f) throws Exception {
        Classifier clsCopy = AbstractClassifier.makeCopy(cls);
        clsCopy.buildClassifier(train);
        Printer.printlnGreen("[R" + (r + 1) + " F" + (f + 1) + "] Classificatore pronto: " +
                clsCopy.getClass().getSimpleName());
        return clsCopy;
    }

    private double[] evaluateFold(Classifier clsCopy, Instances train, Instances test, int buggyClassIndex, int r, int f) throws Exception {
        Evaluation eval = new Evaluation(train);
        eval.evaluateModel(clsCopy, test);
        double npofb20 = computeNPofB20(clsCopy, test);

        double precision = eval.precision(buggyClassIndex);
        double recall = eval.recall(buggyClassIndex);
        double f1 = eval.fMeasure(buggyClassIndex);
        double auc = eval.areaUnderROC(buggyClassIndex);
        double kappa = eval.kappa();
        double accuracy = eval.pctCorrect() / 100.0;



        Printer.printlnBlue(String.format("[R%d F%d] Precision=%.3f, Recall=%.3f, F1=%.3f, AUC=%.3f, Kappa=%.3f, NPofB20=%.3f",
                r, f, precision, recall, f1, auc, kappa, npofb20));

        return new double[]{precision, recall, f1, auc, kappa, accuracy, npofb20};
    }







    /* =========================
       MODEL NAME UTILS
       ========================= */

    private String getBaseClassifierName(Classifier cls) {
        if (cls instanceof FilteredClassifier filteredclassifier) {
            return filteredclassifier.getClassifier().getClass().getSimpleName();
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



}
