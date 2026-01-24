package org.apache.model;


import lombok.Getter;

@Getter
public class EvaluationFoldResult {
    private String classifierName;
    private boolean applyFS;
    private boolean applySMOTE;
    private int seed;
    private int repeat;
    private int fold;
    private double accuracy;
    private double precision;
    private double recall;
    private double f1;
    private double auc;
    private double kappa;
    private double npofb20;


    public EvaluationFoldResult(String classifierName, boolean applyFS, boolean applySMOTE,
                                int seed, int repeat, int fold) {
        this.classifierName = classifierName;
        this.applyFS = applyFS;
        this.applySMOTE = applySMOTE;
        this.seed = seed;
        this.repeat = repeat;
        this.fold = fold;
    }

    public void setAccuracy(double accuracy) {
        this.accuracy = accuracy;
    }

    public void setPrecision(double precision) {
        this.precision = precision;
    }

    public void setRecall(double recall) {
        this.recall = recall;
    }

    public void setF1(double f1) {
        this.f1 = f1;
    }

    public void setAuc(double auc) {
        this.auc = auc;
    }

    public void setKappa(double kappa) {
        this.kappa = kappa;
    }

    public void setNpofb20(double npofb20) {
        this.npofb20 = npofb20;
    }
}

