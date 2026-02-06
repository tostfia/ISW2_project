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
    private double avgAccuracy;
    private double avgNpofb20;

    /* =========================
       SNAPSHOT ultima run (opzionale)
       ========================= */
    private double Precision;
    private double Recall;
    private double F1;
    private double Auc;
    private double Kappa;
    private double Accuracy;
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


    private AggregatedClassifierResult() {
        // intenzionalmente privato: usare il costruttore pubblico con parametri
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

        // Aggiorna snapshot ultima run
        this.Precision = precision;
        this.Recall = recall;
        this.F1 = f1;
        this.Auc = auc;
        this.Kappa = kappa;
        this.Accuracy = accuracy;
        this.Npofb20 = npofb20;

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