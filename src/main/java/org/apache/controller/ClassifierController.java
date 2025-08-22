package org.apache.controller;


import org.apache.model.DataClassifier;


import weka.attributeSelection.BestFirst;
import weka.classifiers.Classifier;
import weka.classifiers.CostMatrix;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.classifiers.meta.CostSensitiveClassifier;
import weka.classifiers.meta.FilteredClassifier;
import weka.classifiers.trees.RandomForest;
import weka.core.AttributeStats;
import weka.core.SelectedTag;
import weka.filters.Filter;
import weka.filters.supervised.attribute.AttributeSelection;
import weka.filters.supervised.instance.Resample;
import weka.filters.supervised.instance.SMOTE;
import weka.filters.supervised.instance.SpreadSubsample;

import java.util.ArrayList;
import java.util.List;


public class ClassifierController {

    public static final double WEIGHT_FALSE_POSITIVE = 1.0;
    public static final double WEIGHT_FALSE_NEGATIVE = 10.0;
    public static final String NO_SAMPLING = "NoSampling";
    public static final String NO_SELECTION = "NoSelection";
    public static final String NO_BEST_FIRST = null;





    public static List<DataClassifier> returnAllClassifiersCombinations(AttributeStats isBuggyAttributeStats) {
        List<Classifier> classifiers = List.of(new RandomForest(), new NaiveBayes(), new IBk());
        List<AttributeSelection> featureSelections = getFeatureSelections();
        int majority = isBuggyAttributeStats.nominalCounts[1];
        int minority = isBuggyAttributeStats.nominalCounts[0];
        List<Filter> samplings = getSamplingFilters(majority, minority);

        List<DataClassifier> result = new ArrayList<>();
        basicClassifiers(classifiers, result);
        onlyFeatureSelectionClassifiers(classifiers, featureSelections, result);
        onlySamplingClassifiers(classifiers, samplings, result);
        onlyCostSensitiveClassifiers(classifiers, result);
        featureSelectionAndSamplingClassifiers(classifiers, featureSelections, samplings, result);
        featureSelectionsAndCostSensitiveClassifiers(classifiers, featureSelections, result);
        return result;
    }

    private static void basicClassifiers(List<Classifier> classifiers, List<DataClassifier> result) {
        for(Classifier classifier : classifiers) {
            result.add(new DataClassifier(classifier, classifier.getClass().getSimpleName(),
                    NO_SELECTION, NO_BEST_FIRST, NO_SAMPLING, false));
        }
    }

    private static void onlyFeatureSelectionClassifiers(List<Classifier> classifiers, List<AttributeSelection> filters, List<DataClassifier> result) {
        for (AttributeSelection filter : filters) {
            for (Classifier classifier : classifiers) {
                FilteredClassifier filteredClassifier = new FilteredClassifier();
                filteredClassifier.setFilter(filter);
                filteredClassifier.setClassifier(classifier);
                result.add(new DataClassifier(filteredClassifier, classifier.getClass().getSimpleName(),
                        filter.getSearch().getClass().getSimpleName(), ((BestFirst) filter.getSearch()).getDirection().getSelectedTag().getReadable(), NO_SAMPLING, false));
            }
        }
    }
    private static void onlySamplingClassifiers(List<Classifier> classifiers, List<Filter> filters, List<DataClassifier> result) {
        for (Filter f : filters) {
            for (Classifier c : classifiers) {
                FilteredClassifier fc = new FilteredClassifier();
                fc.setFilter(f);
                fc.setClassifier(c);
                result.add(new DataClassifier(fc, c.getClass().getSimpleName(), NO_SELECTION, NO_BEST_FIRST, f.getClass().getSimpleName(), false));
            }
        }
    }

    /**
     * Add classifiers using cost-sensitive configuration only.
     */
    private static void onlyCostSensitiveClassifiers(List<Classifier> classifiers, List<DataClassifier> result) {
        for (Classifier c : classifiers) {
            for (CostSensitiveClassifier cost : getCostSensitiveFilters()) {
                cost.setClassifier(c);
                result.add(new DataClassifier(cost, c.getClass().getSimpleName(),NO_SELECTION, NO_BEST_FIRST, NO_SAMPLING, true));
            }
        }
    }

    /**
     * Add classifiers using both feature selection and sampling filters.
     */
    private static void featureSelectionAndSamplingClassifiers(List<Classifier> classifiers, List<AttributeSelection> featureSelections, List<Filter> samplings, List<DataClassifier> result) {
        for (AttributeSelection feature : featureSelections) {
            for (Filter sampling : samplings) {
                for (Classifier c : classifiers) {
                    FilteredClassifier inner = new FilteredClassifier();
                    inner.setFilter(sampling);
                    inner.setClassifier(c);

                    FilteredClassifier outer = new FilteredClassifier();
                    outer.setFilter(feature);
                    outer.setClassifier(inner);

                    result.add(new DataClassifier(outer, c.getClass().getSimpleName(), feature.getSearch().getClass().getSimpleName(), ((BestFirst)feature.getSearch()).getDirection().getSelectedTag().getReadable(), sampling.getClass().getSimpleName(), false));
                }
            }
        }
    }

    /**
     * Add classifiers using both feature selection and cost-sensitive configuration.
     */
    private static void featureSelectionsAndCostSensitiveClassifiers(List<Classifier> classifiers, List<AttributeSelection> featureSelections, List<DataClassifier> result) {
        for (Classifier c : classifiers) {
            for (CostSensitiveClassifier cost : getCostSensitiveFilters()) {
                for (AttributeSelection feature : featureSelections) {
                    FilteredClassifier filtered = new FilteredClassifier();
                    filtered.setFilter(feature);
                    cost.setClassifier(c);
                    filtered.setClassifier(cost);
                    result.add(new DataClassifier(filtered, c.getClass().getSimpleName(), feature.getSearch().getClass().getSimpleName(), ((BestFirst)feature.getSearch()).getDirection().getSelectedTag().getReadable(), NO_SAMPLING, true));
                }
            }
        }
    }

    /**
     * Compute oversampling and SMOTE percentages and create sampling filters.
     */
    private static List<Filter> getSamplingFilters(int majority, int minority) {
        double oversamplePercent = ((100.0 * majority) / (majority + minority)) * 2;
        double smotePercent = (minority == 0 || minority > majority) ? 0 : ((100.0 * (majority - minority)) / minority);
        return createSamplingFilters(oversamplePercent, smotePercent);
    }

    /**
     * Create and configure list of Resample, Spread sub sample, and SMOTE filters.
     */
    private static  List<Filter> createSamplingFilters(double oversamplePercent, double smotePercent) {
        List<Filter> filters = new ArrayList<>();

        Resample resample = new Resample();
        resample.setBiasToUniformClass(1.0);
        resample.setSampleSizePercent(oversamplePercent);
        filters.add(resample);

        SpreadSubsample spread = new SpreadSubsample();
        spread.setDistributionSpread(1.0);
        filters.add(spread);

        SMOTE smote = new SMOTE();
        smote.setClassValue("1");
        smote.setPercentage(smotePercent);
        filters.add(smote);

        return filters;
    }

    /**
     * Create attribute selection filter using BestFirst strategy.
     */

    private static  List<AttributeSelection> getFeatureSelections() {
        AttributeSelection selection = new AttributeSelection();
        BestFirst best = new BestFirst();
        best.setDirection(new SelectedTag(2, best.getDirection().getTags()));
        selection.setSearch(best);
        return List.of(selection);
    }

    /**
     * Create list containing one CostSensitiveClassifier using default cost matrix.
     */

    private static List<CostSensitiveClassifier> getCostSensitiveFilters() {
        CostSensitiveClassifier csc = new CostSensitiveClassifier();
        csc.setMinimizeExpectedCost(false);
        csc.setCostMatrix(createCostMatrix());
        return List.of(csc);
    }

    /**
     * Define and return the cost matrix used for cost-sensitive classification.
     */
    private static CostMatrix createCostMatrix() {
        CostMatrix matrix = new CostMatrix(2);
        matrix.setCell(0, 0, 0.0);
        matrix.setCell(1, 0, WEIGHT_FALSE_POSITIVE);
        matrix.setCell(0, 1, WEIGHT_FALSE_NEGATIVE);
        matrix.setCell(1, 1, 0.0);
        return matrix;
    }


}







