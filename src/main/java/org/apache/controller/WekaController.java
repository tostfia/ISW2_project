package org.apache.controller;

import org.apache.logging.Printer;
import org.apache.model.AcumeRecord;
import org.apache.model.AggregatedClassifierResult;
import org.apache.model.AggregatedClassifierResultStore;
import org.apache.utilities.writer.AcumeUtils;
import weka.attributeSelection.AttributeSelection;
import weka.attributeSelection.InfoGainAttributeEval;
import weka.attributeSelection.Ranker;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.meta.FilteredClassifier;
import weka.core.Instance;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.supervised.instance.SMOTE;
import weka.filters.unsupervised.attribute.Remove;
import weka.filters.unsupervised.attribute.RemoveUseless;

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
            boolean applyFS,
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
            Printer.printlnGreen("CV repetition " + (r + 1) + "/" + repeats);

            // Somme per questa repetition
            double sumPrecisionRep = 0;
            double sumRecallRep = 0;
            double sumF1Rep = 0;
            double sumAUCRep = 0;
            double sumKappaRep = 0;
            double sumNPofB20Rep = 0;
            int validFolds = 0;

            Instances trainFull = new Instances(data);
            trainFull.randomize(new Random(SEED + r));
            if (trainFull.classAttribute().isNominal()) {
                trainFull.stratify(folds);
            }

            int startInst = trainFull.numInstances();
            if (applyFS) {
                trainFull = applyFeatureSelection(trainFull);
            }
            int afterFS = trainFull.numInstances();

            if (projectName.equalsIgnoreCase("BOOKKEEPER")) {
                applySmote = true;
            }
            if (applySmote) {
                trainFull = applySMOTE(trainFull);
            }
            int afterSMOTE = trainFull.numInstances();

            if (applyDownsampling) {
                trainFull = downsample(trainFull, maxInstances);
            }
            int afterDownsampling = trainFull.numInstances();

            Printer.printlnBlue(
                    "[CV R" + (r + 1) + "] Instances: " +
                            "start=" + startInst +
                            ", afterFS=" + afterFS +
                            ", afterSMOTE=" + afterSMOTE +
                            ", afterDownsampling=" + afterDownsampling +
                            ", final=" + trainFull.numInstances()
            );

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

                Classifier clsCopy =
                        weka.classifiers.AbstractClassifier.makeCopy(cls);
                clsCopy.buildClassifier(train);

                Evaluation eval = new Evaluation(train);
                eval.evaluateModel(clsCopy, test);

                // Calcola NPofB20 per questo fold
                double npofb20 = computeNPofB20(clsCopy, test);

                // Accumula le metriche per questa repetition (gestendo NaN)
                double precision = eval.precision(buggyClassIndex);
                double recall = eval.recall(buggyClassIndex);
                double f1 = eval.fMeasure(buggyClassIndex);
                double auc = eval.areaUnderROC(buggyClassIndex);
                double kappa = eval.kappa();

                // Evita NaN sommando solo valori validi
                if (!Double.isNaN(precision)) {
                    sumPrecisionRep += precision;
                }
                if (!Double.isNaN(recall)) {
                    sumRecallRep += recall;
                }
                if (!Double.isNaN(f1)) {
                    sumF1Rep += f1;
                }
                if (!Double.isNaN(auc)) {
                    sumAUCRep += auc;
                }
                if (!Double.isNaN(kappa)) {
                    sumKappaRep += kappa;
                }
                if (!Double.isNaN(npofb20)) {
                    sumNPofB20Rep += npofb20;
                }
                validFolds++;

                // Genera i record ACUME per questo fold
                List<AcumeRecord> records = getAcumeRecords(clsCopy, test);
                AcumeUtils.writeAcumeCSV(
                        projectName + "_R" + r + "_F" + f,
                        records
                );

                // Stampa metriche del fold (opzionale)
                Printer.printlnBlue(
                        String.format("[R%d F%d] Precision=%.3f, Recall=%.3f, F1=%.3f, AUC=%.3f, Kappa=%.3f, NPofB20=%.3f",
                                r, f, precision, recall, f1, auc, kappa, npofb20)
                );
            }

            // Calcola le medie di questa repetition e aggiungile all'aggregato
            double avgPrecisionRep = sumPrecisionRep / validFolds;
            double avgRecallRep = sumRecallRep / validFolds;
            double avgF1Rep = sumF1Rep / validFolds;
            double avgAUCRep = sumAUCRep / validFolds;
            double avgKappaRep = sumKappaRep / validFolds;
            double avgNPofB20Rep = sumNPofB20Rep / validFolds;

            aggregated.addRunResult(
                    avgPrecisionRep,
                    avgRecallRep,
                    avgF1Rep,
                    avgAUCRep,
                    avgKappaRep,
                    avgNPofB20Rep
            );

            Printer.printlnGreen(
                    String.format("[R%d SUMMARY] Precision=%.3f, Recall=%.3f, F1=%.3f, AUC=%.3f, Kappa=%.3f, NPofB20=%.3f",
                            r, avgPrecisionRep, avgRecallRep, avgF1Rep, avgAUCRep, avgKappaRep, avgNPofB20Rep)
            );
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
       PREPROCESSING
       ========================= */
    public Instances applyFeatureSelection(Instances data) throws Exception {
        RemoveUseless ru = new RemoveUseless();
        ru.setInputFormat(data);
        Instances afterRu = Filter.useFilter(data, ru);

        AttributeSelection selector = new AttributeSelection();
        selector.setEvaluator(new InfoGainAttributeEval());

        Ranker ranker = new Ranker();
        ranker.setThreshold(0.01);
        selector.setSearch(ranker);

        selector.SelectAttributes(afterRu);
        return selector.reduceDimensionality(afterRu);
    }

    public Instances applySMOTE(Instances data) throws Exception {
        SMOTE smote = new SMOTE();
        smote.setPercentage(65);
        smote.setNearestNeighbors(5);
        smote.setInputFormat(data);
        return Filter.useFilter(data, smote);
    }

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

    /* =========================
       ACUME OUTPUT
       ========================= */
    public List<AcumeRecord> getAcumeRecords(Classifier cls, Instances data) throws Exception {
        List<AcumeRecord> records = new ArrayList<>();
        int buggyIdx = data.classAttribute().indexOfValue("yes");
        int locIdx = data.attribute("LOC").index();

        for (int i = 0; i < data.numInstances(); i++) {
            Instance inst = data.instance(i);
            double prob = cls.distributionForInstance(inst)[buggyIdx];
            int loc = (int) inst.value(locIdx);
            String actual = ((int) inst.classValue() == buggyIdx) ? "YES" : "NO";
            records.add(new AcumeRecord(i, loc, prob, actual));
        }
        return records;
    }
}