package org.apache.model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;




@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class AggregatedClassifierResult {

    /* =========================
       METADATI
       ========================= */
    @Setter
    private  String project;
    @Setter
    private  String classifierName;


    private final static String INFO_GAIN = "InfoGain";

    private final static String CROSS_VALIDATION = "CrossValidation";

    /* =========================
       METRICHE AGGREGATE (per tutte le run)
       ========================= */
    private double avgPrecision;
    private double avgRecall;
    private double avgF1;
    private double avgAuc;
    private double avgKappa;
    private double avgAccuracy;
    private double avgNpofb20;

    /* =========================
       SNAPSHOT ultima run (opzionale)
       ========================= */
    private double precisionLast;
    private double recallLast;
    private double f1Last;
    private double aucLast;
    private double kappaLast;
    private double accuracyLast;
    private double npofb20Last;

    private int numberOfRuns;
    private String modelFilePath;

    // Costruttore privato base (nasconde l'inizializzazione implicita)
    private AggregatedClassifierResult(String project, String classifierName, int numberOfRuns) {
        this.project = project;
        this.classifierName = classifierName;
        this.numberOfRuns = numberOfRuns;
    }


    /* =========================
       COSTRUTTORI
       ========================= */
    public AggregatedClassifierResult(String project, String classifierName) {
        this.project = project;
        this.classifierName = classifierName;
        this.numberOfRuns = 0;
    }







    /* =========================
       AGGIUNTA DI UNA RUN
       ========================= */
    /**
     * Aggiunge i risultati di una run completa (già aggregati).
     * Se i valori passati sono già medie (come nel caso di CV 10x10),
     * questo metodo li salva direttamente.
     */
    public void addRunResult(
            double precision,
            double recall,
            double f1,
            double auc,
            double kappa,
            double accuracy,
            double npofb20) {

        this.precisionLast = precision;
        this.recallLast = recall;
        this.f1Last = f1;
        this.aucLast = auc;
        this.kappaLast = kappa;
        this.accuracyLast = accuracy;
        this.npofb20Last = npofb20;

        // Se è la prima run, inizializza le medie
        if (numberOfRuns == 0) {
            this.avgPrecision = precision;
            this.avgRecall = recall;
            this.avgF1 = f1;
            this.avgAuc = auc;
            this.avgKappa = kappa;
            this.avgAccuracy = accuracy;
            this.avgNpofb20 = npofb20;
        } else {
            // Altrimenti aggiorna medie incrementalmente
            this.avgPrecision = incrementalAvg(this.avgPrecision, precision);
            this.avgRecall = incrementalAvg(this.avgRecall, recall);
            this.avgF1 = incrementalAvg(this.avgF1, f1);
            this.avgAuc = incrementalAvg(this.avgAuc, auc);
            this.avgKappa = incrementalAvg(this.avgKappa, kappa);
            this.avgAccuracy = incrementalAvg(this.avgAccuracy, accuracy);
            this.avgNpofb20 = incrementalAvg(this.avgNpofb20, npofb20);
        }


        numberOfRuns++;
    }



    /* =========================
       UTILS
       ========================= */
    private double incrementalAvg(double currentAvg, double newValue) {
        // Gestisce anche i NaN
        if (Double.isNaN(newValue)) {
            return currentAvg;
        }
        if (Double.isNaN(currentAvg)) {
            return newValue;
        }
        return (currentAvg * numberOfRuns + newValue) / (numberOfRuns + 1);
    }



    @Override
    public String toString() {
        return String.format(
                "%s | AUC=%.3f | Precision=%.3f | Recall=%.3f | F1=%.3f | Kappa=%.3f | Accuracy=%.3f | NPofB20=%.3f | runs=%d ",
                classifierName, avgAuc, avgPrecision, avgRecall, avgF1, avgKappa,avgAccuracy, avgNpofb20, numberOfRuns
        );
    }




}