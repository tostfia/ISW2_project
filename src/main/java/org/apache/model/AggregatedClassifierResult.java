package org.apache.model;

import lombok.Getter;
import lombok.Setter;
import weka.classifiers.Evaluation;

@Getter
@Setter
public class AggregatedClassifierResult {

    /* =========================
       METADATI
       ========================= */
    private final String project;
    private final String classifierName;

    private final String featureSelection = "InfoGain";

    private final String evaluationType = "CrossValidation";

    /* =========================
       METRICHE AGGREGATE (per tutte le run)
       ========================= */
    private double avgPrecision;
    private double avgRecall;
    private double avgF1;
    private double avgAuc;
    private double avgKappa;
    private double avgNpofb20;

    /* =========================
       SNAPSHOT ultima run (opzionale)
       ========================= */
    private double Precision;
    private double Recall;
    private double F1;
    private double Auc;
    private double Kappa;
    private double Npofb20;

    private int numberOfRuns;
    private String modelFilePath;

    /* =========================
       COSTRUTTORI
       ========================= */
    public AggregatedClassifierResult(String project, String classifierName) {
        this.project = project;
        this.classifierName = classifierName;
        this.numberOfRuns = 0;
    }

    public AggregatedClassifierResult() {
        this.project = null;
        this.classifierName = null;
    }

    /* =========================
       AGGIUNTA DI UNA RUN
       ========================= */
    public void addRunResult(
            double precision,
            double recall,
            double f1,
            double auc,
            double kappa,
            double npofb20) {

        // Aggiorna snapshot ultima run
        Precision = precision;
        Recall = recall;
        F1 = f1;
        Auc = auc;
        Kappa = kappa;
        Npofb20 = npofb20;

        // Aggiorna medie incrementalmente
        avgPrecision = incrementalAvg(avgPrecision, precision);
        avgRecall = incrementalAvg(avgRecall, recall);
        avgF1 = incrementalAvg(avgF1, f1);
        avgAuc = incrementalAvg(avgAuc, auc);
        avgKappa = incrementalAvg(avgKappa, kappa);
        avgNpofb20 = incrementalAvg(avgNpofb20, npofb20);

        numberOfRuns++;
    }

    /* =========================
       UTILS
       ========================= */
    private double incrementalAvg(double currentAvg, double newValue) {
        return (currentAvg * numberOfRuns + newValue) / (numberOfRuns + 1);
    }

    @Override
    public String toString() {
        return String.format(
                "%s | AUC=%.3f | Precision=%.3f | Recall=%.3f | F1=%.3f | Kappa=%.3f | NPofB20=%.3f | runs=%d",
                classifierName, avgAuc, avgPrecision, avgRecall, avgF1, avgKappa, avgNpofb20, numberOfRuns
        );
    }

    /* =========================
       FACTORY DA WEKA Evaluation
       ========================= */
    public static AggregatedClassifierResult fromEvaluation(
            String project,
            String classifierName,
            Evaluation eval,
            double npofb20) throws Exception {

        int buggyIndex = eval.getHeader().classAttribute().indexOfValue("yes");
        if (buggyIndex == -1) {
            throw new IllegalStateException("Classe 'yes' non trovata nella classe target");
        }

        AggregatedClassifierResult r = new AggregatedClassifierResult(project, classifierName);

        r.addRunResult(
                eval.precision(buggyIndex),
                eval.recall(buggyIndex),
                eval.fMeasure(buggyIndex),
                eval.areaUnderROC(buggyIndex),
                eval.kappa(),
                npofb20
        );

        return r;
    }
}
