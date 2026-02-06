package org.apache.controller.milestone2;

import org.apache.model.AggregatedClassifierResult;
import org.apache.model.AggregatedClassifierResultStore;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public class ReportAnalyzer {

    private final String project;
    private final Collection<AggregatedClassifierResult> results;

    /* =========================
       COSTRUTTORE
       ========================= */
    public ReportAnalyzer(String project) {
        this.project = project;
        this.results = AggregatedClassifierResultStore.load(project);
        System.out.println("Loaded " + results.size() + " classifier results for project " + project);
        for (AggregatedClassifierResult r : results) {
            System.out.println(r.getClassifierName() + " | " + r.getProject());
        }
    }

    /* =========================
       ENTRY POINT USATO DAL MAIN
       ========================= */
    public AggregatedClassifierResult getBestClassifierByCompositeScore() {
        return results.stream()
                .max(Comparator.comparingDouble(this::compositeScore))
                .orElseThrow(() ->
                        new IllegalStateException("No classifier results available"));
    }

    /* =========================
       COMPOSITE SCORE
       ========================= */
    private double compositeScore(AggregatedClassifierResult r) {
        double recall = r.getAvgRecall(); // già 0-1
        double auc = r.getAvgAuc();       // già 0-1
        double npofb20 = normalizeNp(r.getAvgNpofb20()); // normalizza NPofB20
        double kappa = normalizeKappa(r.getAvgKappa());

        return 0.30 * auc +
                0.40 * recall +
                0.20 * npofb20 +
                0.10 * kappa;
    }

    private double normalizeKappa(double kappa) {
        return (kappa + 1.0) / 2.0;
    }
    // Normalizza NPofB20 su scala 0-1
    private double normalizeNp(double val) {
        double min = 0.0;    // ipotetico minimo possibile
        double max = 100.0;  // ipotetico massimo possibile (da calibrare sul dataset)
        return Math.min(1.0, Math.max(0.0, (val - min) / (max - min)));
    }


    /* =========================
       OPTIONAL: RANKING OUTPUT
       ========================= */
    public void printRanking() {

        List<AggregatedClassifierResult> ranked =
                results.stream()
                        .sorted(Comparator
                                .comparingDouble(this::compositeScore)
                                .reversed())
                        .toList();

        System.out.println("=== CLASSIFIER RANKING (" + project + ") ===");

        int pos = 1;
        for (AggregatedClassifierResult r : ranked) {
            System.out.printf(
                    "%d) %s | Score=%.3f | AUC=%.3f | Recall=%.3f | Precision=%.3f | Kappa=%.3f | NPofB20=%.3f%n",
                    pos++,
                    r.getClassifierName(),
                    compositeScore(r),
                    r.getAvgAuc(),
                    r.getAvgRecall(),
                    r.getAvgPrecision(),
                    r.getAvgKappa(),
                    r.getAvgNpofb20()
            );
        }
    }
}
