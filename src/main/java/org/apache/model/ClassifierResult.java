package org.apache.model;



import lombok.Getter;
import lombok.Setter;
import weka.classifiers.Evaluation;

public class ClassifierResult {


    @Getter
    private final int walkForwardIteration;
    @Getter
    private final String classifierName;
    @Getter
    private final boolean featureSelection;
    private final boolean hasSampling;
    @Getter
    private final DataClassifier customClassifier;
    @Getter
    private final boolean costSensitive;
    @Getter
    @Setter
    private double trainingPercent;
    @Getter
    @Setter
    private double precision;
    @Getter
    @Setter
    private double recall;
    @Getter
    private final double areaUnderROC;
    @Getter
    private final double kappa;
    @Getter
    private final double truePositives;
    @Getter
    private final double falsePositives;
    @Getter
    private final double trueNegatives;
    @Getter
    private final double falseNegatives;

    public ClassifierResult(int walkForwardIteration, DataClassifier dataClassifier, Evaluation evaluation) {
        this.walkForwardIteration = walkForwardIteration;
        this.customClassifier = dataClassifier;
        this.classifierName = dataClassifier.getClassifierName();
        this.featureSelection = (!dataClassifier.getFeatureSelectionFilterName().equals("NoSelection"));
        this.hasSampling = (!dataClassifier.getSamplingFilterName().equals("NoSampling"));
        this.costSensitive = dataClassifier.isCostSensitive();

        trainingPercent = 0.0;
        truePositives = evaluation.numTruePositives(0);
        falsePositives = evaluation.numFalsePositives(0);
        trueNegatives = evaluation.numTrueNegatives(0);
        falseNegatives = evaluation.numFalseNegatives(0);
        if(truePositives == 0.0 && falsePositives == 0.0){
            precision = Double.NaN;
        } else{
            precision = evaluation.precision(0);
        }
        if(truePositives == 0.0 && falseNegatives == 0.0){
            recall = Double.NaN;
        } else{
            recall = evaluation.recall(0);
        }
        areaUnderROC = evaluation.areaUnderROC(0);
        kappa = evaluation.kappa();
    }

    public boolean hasSampling() {
        return hasSampling;
    }

}
