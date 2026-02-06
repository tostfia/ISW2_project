package org.apache.utilities;

import weka.classifiers.Classifier;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.classifiers.meta.FilteredClassifier;
import weka.classifiers.trees.RandomForest;
import weka.core.SelectedTag;
import weka.filters.unsupervised.attribute.Normalize;

public class ClassifierFactory {

    private ClassifierFactory() {
        throw new AssertionError("Utility class - non istanziare");
    }


    public static Classifier build(String name, int seed) throws IllegalArgumentException {

        return switch (name) {
            case "NaiveBayes" -> new NaiveBayes();
            case "RandomForest" -> {
                RandomForest rf = new RandomForest();
                rf.setSeed(seed);
                rf.setNumIterations(100);
                yield rf;
            }
            case "IBk" -> {
                Normalize norm = new Normalize();
                IBk ibk = new IBk(5);
                ibk.setDistanceWeighting(
                        new SelectedTag(IBk.WEIGHT_INVERSE, IBk.TAGS_WEIGHTING));

                FilteredClassifier fc = new FilteredClassifier();
                fc.setFilter(norm);
                fc.setClassifier(ibk);
                yield fc;
            }
            default -> throw new IllegalArgumentException("Unsupported classifier");
        };
    }
}

