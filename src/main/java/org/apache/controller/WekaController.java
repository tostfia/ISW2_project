package org.apache.controller;

import org.apache.logging.Printer;
import org.apache.model.AcumeRecord;
import org.apache.model.AggregatedClassifierResult;
import org.apache.model.AggregatedClassifierResultStore;
import org.apache.model.EvaluationFoldResult;
import org.apache.utilities.writer.AcumeUtils;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;

import weka.core.Instance;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.supervised.instance.SMOTE;
import weka.filters.unsupervised.attribute.Remove;
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

        int releaseIdIndex = data.attribute("Release") != null ? data.attribute("Release").index() : -1;
        if (releaseIdIndex != -1) {
            Remove remove = new Remove();
            remove.setAttributeIndicesArray(new int[]{releaseIdIndex});
            remove.setInputFormat(data);
            data = Filter.useFilter(data, remove);
        }


        double totalPrecision = 0;
        double totalRecall = 0;
        double totalF1 = 0;
        double totalAUC = 0;
        double totalKappa = 0;
        int totalFolds = (folds - 1) * repeats;


        AggregatedClassifierResult aggregated = new AggregatedClassifierResult(projectName, cls.getClass().getSimpleName());

        for (int r = 0; r < repeats; r++) {
            Printer.printlnGreen("Starting runCrossValidation" + " repetition " + (r + 1) + "/" + repeats);

            // Copia e randomizza il trainFull per questa ripetizione
            Instances trainFull = new Instances(data);
            trainFull.randomize(new Random(SEED + r));

            // Applica feature selection se richiesto
            if (applyFS) trainFull = applyFeatureSelection(trainFull);

            // Applica SMOTE se richiesto
            if (projectName.equalsIgnoreCase("BOOKKEEPER")) {
                applySmote = true;
                trainFull = applySMOTE(trainFull);
            }


            // Applica downsampling se richiesto
            if (applyDownsampling) trainFull = downsample(trainFull, maxInstances);

            List<EvaluationFoldResult> foldResults = new ArrayList<>();
            // Calcola fold size
            int foldSize = trainFull.numInstances() / folds;
            int totalInstances = trainFull.numInstances();
            for (int f = 0; f < folds; f++) {

                int foldStart = f * foldSize;
                int foldEnd = Math.min(foldStart + foldSize, totalInstances);

                // Test set: il fold corrente
                Instances test = new Instances(trainFull, foldStart, foldEnd - foldStart);

                // Train set: tutte le altre istanze
                Instances train = new Instances(trainFull);
                for (int i = foldEnd - 1; i >= foldStart; i--) {
                    train.delete(i); // rimuove il fold di test dal training
                }

                // Copia del classificatore
                Classifier clsCopy = weka.classifiers.AbstractClassifier.makeCopy(cls);
                clsCopy.buildClassifier(train);
                List<AcumeRecord> foldRecords = getAcumeRecords(clsCopy, test);

                // Salva il file rinominandolo per repeat e fold
                String fileName = projectName + "_R" + r + "_F" + f;
                AcumeUtils.writeAcumeCSV(fileName, foldRecords);


                // Valutazione
                Evaluation foldEval = new Evaluation(train);
                foldEval.evaluateModel(clsCopy, test);

                double npofb20Fold = computeNPofB20(clsCopy, test);


                EvaluationFoldResult foldResult = new EvaluationFoldResult(
                        cls.toString(), applyFS, applySmote, SEED, r, f
                );
                foldResult.setAccuracy(foldEval.weightedPrecision());
                foldResult.setRecall(foldEval.weightedRecall());
                foldResult.setF1(foldEval.weightedFMeasure());
                foldResult.setAuc(foldEval.weightedAreaUnderROC());
                foldResult.setKappa(foldEval.kappa());
                foldResult.setNpofb20(npofb20Fold);

                foldResults.add(foldResult);


                totalPrecision += foldEval.weightedPrecision();
                totalRecall += foldEval.weightedRecall();
                totalF1 += foldEval.weightedFMeasure();
                totalAUC += foldEval.weightedAreaUnderROC();
                totalKappa += foldEval.kappa();
            }




        }

        // Calcola NPofB20 medio
        double npofb20 = computeNPofB20(cls, data);



                // Aggiunge risultati alla media
        aggregated.addRunResult(
            totalPrecision/totalFolds,
            totalRecall/totalFolds,
            totalF1/totalFolds,
            totalAUC/totalFolds,
            totalKappa/totalFolds,
            npofb20
        );

        // Salva il risultato CV
        addResult(aggregated);
        return aggregated;
    }


    /* =========================
       NPofB20 METRIC
       ========================= */
    public static double computeNPofB20(Classifier cls, Instances data) throws Exception {
        // Costruisce un nuovo classificatore su tutti i dati
        Classifier copy = weka.classifiers.AbstractClassifier.makeCopy(cls);
        copy.buildClassifier(data);

        // Trova l’indice della classe “Yes”
        int yesIndex = data.classAttribute().indexOfValue("yes");
        if (yesIndex == -1) {
            throw new IllegalArgumentException("La classe 'yes' non è presente tra i valori della variabile target.");
        }

        // Prepara una lista (score, isBuggy)
        List<double[]> scored = new ArrayList<>();
        for (int i = 0; i < data.numInstances(); i++) {
            double[] dist = copy.distributionForInstance(data.instance(i));
            double score = dist[yesIndex];  // probabilità che sia buggy
            double actual = data.instance(i).classValue(); // 1 = Yes, 0 = No (valore numerico)
            scored.add(new double[]{score, actual});
        }

        // Ordina per probabilità discendente
        scored.sort((a, b) -> Double.compare(b[0], a[0]));

        int topN = (int) Math.ceil(data.numInstances() * 0.2); // top 20%
        int foundBuggy = 0;
        int totalBuggy = 0;

        for (int i = 0; i < data.numInstances(); i++) {
            if (scored.get(i)[1] == yesIndex) totalBuggy++;
            if (i < topN && scored.get(i)[1] == yesIndex) foundBuggy++;
        }

        if (totalBuggy == 0) return 0.0;

        return (double) foundBuggy / totalBuggy;
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


    public Instances downsample(Instances data, int maxInstances)  {
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


    public List<AcumeRecord> getAcumeRecords(Classifier cls, Instances data) throws Exception {
        List<AcumeRecord> records = new ArrayList<>();

        // Trova gli indici delle colonne necessarie
        int classIndex = data.classIndex();
        int buggyClassIndex = data.classAttribute().indexOfValue("yes");
        // Se nel tuo dataset il bug è indicato diversamente (es. "true" o "1"), cambia "yes"

        int locIndex = data.attribute("LOC").index();

        for (int i = 0; i < data.numInstances(); i++) {
            Instance inst = data.instance(i);

            // Estrai la probabilità che l'istanza sia BUGGY (classe "yes")
            double[] distribution = cls.distributionForInstance(inst);
            double probability = distribution[buggyClassIndex];

            int loc = (int) inst.value(locIndex);
            String actual = (int) inst.classValue() == buggyClassIndex ? "YES" : "NO";

            records.add(new AcumeRecord(i, loc, probability, actual));
        }
        return records;
    }




}
