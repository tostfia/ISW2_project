package org.apache.controller;

import org.apache.logging.Printer;
import org.apache.model.AggregatedClassifierResult;
import org.apache.model.AggregatedClassifierResultStore;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.core.Instance;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.supervised.instance.SMOTE;
import weka.filters.unsupervised.attribute.RemoveUseless;
import weka.attributeSelection.AttributeSelection;
import weka.attributeSelection.InfoGainAttributeEval;
import weka.attributeSelection.Ranker;

import java.util.*;

public class WekaController {

    private final String projectName;
    private static final int SEED = 42;

    public WekaController(String projectName) {
        this.projectName = projectName;
    }

    /**
     * RUN CROSS VALIDATION 10x10
     */
    public AggregatedClassifierResult runCrossValidation(Classifier cls, Instances data,
                                                         boolean applyFS, boolean applySmote,
                                                         boolean applyDownsampling, int maxInstances) throws Exception {

        int folds = 10;
        int repeats = 10;

        AggregatedClassifierResult aggregated = new AggregatedClassifierResult(projectName, cls.getClass().getSimpleName());

        for (int r = 0; r < repeats; r++) {

            // Copia e randomizza il dataset per questa ripetizione
            Instances dataset = new Instances(data);
            dataset.randomize(new Random(SEED + r));

            // Applica feature selection se richiesto
            if (applyFS) dataset = applyFeatureSelection(dataset);

            // Applica SMOTE se richiesto
            if (applySmote) dataset = applySMOTE(dataset);

            // Applica downsampling se richiesto
            if (applyDownsampling) dataset = downsample(dataset, maxInstances);

            // Calcola fold size
            int foldSize = dataset.numInstances() / folds;

            for (int f = 0; f < folds; f++) {
                int start = f * foldSize;
                int end = Math.min(start + foldSize, dataset.numInstances());

                Instances test = new Instances(dataset, start, end - start);
                Instances train = new Instances(dataset);
                for (int i = end - 1; i >= start; i--) train.delete(i);

                if (train.numInstances() == 0 || test.numInstances() == 0) continue;

                // Copia del classificatore
                Classifier clsCopy = weka.classifiers.AbstractClassifier.makeCopy(cls);
                clsCopy.buildClassifier(train);

                // Valutazione
                Evaluation eval = new Evaluation(train);
                eval.evaluateModel(clsCopy, test);

                double npofb20 = computeNPofB20(clsCopy, test);

                // Aggiunge risultati alla media
                aggregated.addRunResult(
                        eval.weightedPrecision(),
                        eval.weightedRecall(),
                        eval.weightedFMeasure(),
                        eval.weightedAreaUnderROC(),
                        eval.kappa(),
                        npofb20
                );
            }
        }

        // Salva il risultato CV
        addResult(aggregated);
        return aggregated;
    }


    /* =========================
       NPofB20 METRIC
       ========================= */
    public double computeNPofB20(Classifier cls, Instances data) throws Exception {
        int yesIndex = data.classAttribute().indexOfValue("yes");
        if (yesIndex == -1) return 0.0;

        List<double[]> scored = new ArrayList<>();
        for (int i = 0; i < data.numInstances(); i++) {
            Instance inst = data.instance(i);
            double[] dist = cls.distributionForInstance(inst);
            scored.add(new double[]{dist[yesIndex], inst.classValue()});
        }

        scored.sort((a, b) -> Double.compare(b[0], a[0]));
        int topN = (int) Math.ceil(data.numInstances() * 0.2);
        int foundBuggy = 0, totalBuggy = 0;

        for (int i = 0; i < scored.size(); i++) {
            if (scored.get(i)[1] == yesIndex) totalBuggy++;
            if (i < topN && scored.get(i)[1] == yesIndex) foundBuggy++;
        }

        return totalBuggy == 0 ? 0.0 : (double) foundBuggy / totalBuggy;
    }

    /* =========================
       FEATURE SELECTION / SMOTE
       ========================= */
    public Instances applyFeatureSelection(Instances data) throws Exception {
        RemoveUseless ru = new RemoveUseless();
        ru.setInputFormat(data);
        data = Filter.useFilter(data, ru);

        AttributeSelection selector = new AttributeSelection();
        InfoGainAttributeEval eval = new InfoGainAttributeEval();
        Ranker ranker = new Ranker();
        ranker.setThreshold(0.01);
        selector.setEvaluator(eval);
        selector.setSearch(ranker);
        selector.SelectAttributes(data);
        return selector.reduceDimensionality(data);
    }

    public Instances applySMOTE(Instances data) throws Exception {
        SMOTE smote = new SMOTE();
        smote.setPercentage(65.0);
        smote.setNearestNeighbors(5);
        smote.setInputFormat(data);
        return Filter.useFilter(data, smote);
    }


    public Instances downsample(Instances data, int maxInstances) throws Exception {
        if (data.numInstances() <= maxInstances) {
            return new Instances(data); // copia del dataset originale
        }

        // Randomizza il dataset con seed fisso
        Instances randomized = new Instances(data);
        randomized.randomize(new Random(SEED));

        // Prendi solo le prime maxInstances
        return new Instances(randomized, 0, maxInstances);
    }



    /* =========================
       SALVA RISULTATI
       ========================= */
    public void addResult(AggregatedClassifierResult result) {
        try {
            Collection<AggregatedClassifierResult> existing = AggregatedClassifierResultStore.load(projectName);
            existing.add(result);
            AggregatedClassifierResultStore.save(projectName, existing);
        } catch (Exception e) {
            Printer.printYellow("Failed to save CV result for project " + projectName + ": " + e.getMessage());
        }
    }

}
