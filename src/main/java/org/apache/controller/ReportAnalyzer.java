package org.apache.controller;

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
        double auc = r.getAvgAuc();
        double recall = r.getAvgRecall();
        double precision = r.getAvgPrecision();
        double kappa = normalizeKappa(r.getAvgKappa());

        return 0.40 * auc +
                0.25 * recall +
                0.15 * precision +
                0.20 * kappa;
    }

    private double normalizeKappa(double kappa) {
        return (kappa + 1.0) / 2.0;
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
