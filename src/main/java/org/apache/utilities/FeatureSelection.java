package org.apache.utilities;
import weka.attributeSelection.ASEvaluation;
import weka.attributeSelection.ASSearch;
import weka.attributeSelection.AttributeSelection;
import weka.core.Instances;

public class FeatureSelection {

    public static Instances selectFeatures(Instances data, ASEvaluation evaluator, ASSearch search) throws Exception {
        AttributeSelection attrSel = new AttributeSelection();
        attrSel.setEvaluator(evaluator);
        attrSel.setSearch(search);
        attrSel.SelectAttributes(data);
        return attrSel.reduceDimensionality(data);
    }
}

