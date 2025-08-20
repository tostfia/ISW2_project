package org.apache.controller;

import org.apache.logging.CollectLogger;
import org.apache.model.ClassifierResult;
import org.apache.model.DataClassifier;
import org.apache.utilities.Utility;
import org.apache.utilities.enumeration.DataSet;
import org.apache.utilities.enumeration.FileExtension;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.core.Instances;
import weka.core.converters.ConverterUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

public class WekaController {
    private final String projectName;
    private final int iterations;
    private final List<ClassifierResult> classifierResults;
    private final static Logger logger = CollectLogger.getInstance().getLogger();

    public WekaController(String projectName,int iterations){
        this.projectName=projectName;
        this.iterations=iterations;
        this.classifierResults = new ArrayList<>();
    }

    public void classify(){
        final String arff= FileExtension.ARFF.toString();
        final String head= "output"+ File.separator+ "dataset"+ File.separator + projectName + File.separator + FileExtension.ARFF.toString() + File.separator;
        final String training_path= head + DataSet.TRAINING.toString() + File.separator+ this.projectName ;
        final String testing_path= head + DataSet.TESTING.toString() + File.separator+ this.projectName ;


        CountDownLatch countDownLatch = new CountDownLatch(this.iterations);
        for( int walkForwarIteration= 1; walkForwarIteration <= this.iterations; walkForwarIteration++ ){
            final int iteration = walkForwarIteration;
            Runnable task=()->{
                try {
                    ConverterUtils.DataSource trainingSet = new ConverterUtils.DataSource(training_path+"_"+iteration+"."+arff);
                    ConverterUtils.DataSource testingSet = new ConverterUtils.DataSource(testing_path+"_"+iteration+"."+arff);
                    Instances trainInstances = trainingSet.getDataSet();
                    Instances testInstances = testingSet.getDataSet();

                    int numAttributes = trainInstances.numAttributes();
                    trainInstances.setClassIndex(numAttributes-1);
                    testInstances.setClassIndex(numAttributes-1);

                    List<DataClassifier> classifiers = ClassifierController.returnAllClassifiersCombinations(trainInstances.attributeStats(numAttributes-1));
                    for (DataClassifier classifier : classifiers ) {
                        Evaluation evaluation= new Evaluation(testInstances);
                        Classifier wekaClassifier = classifier.getClassifier();
                        wekaClassifier.buildClassifier(trainInstances);
                        evaluation.evaluateModel(wekaClassifier, testInstances);
                        ClassifierResult result = new ClassifierResult(iteration, classifier,evaluation);
                        result.setTrainingPercent(100.0*(double) trainInstances.numInstances()/(trainInstances.numInstances()+testInstances.numInstances()));

                        synchronized (classifierResults) {
                            classifierResults.add(result);
                        }
                    }
                } catch (Exception e) {
                    logger.severe("Error during classification: " + e.getMessage());
                } finally {
                    countDownLatch.countDown();
                }
            };
            new Thread(task).start();
        }
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            logger.severe("Classification interrupted: " + e.getMessage());
        }
    }

    public void saveResults(){
        Utility.saveToCsv(this.projectName,this.classifierResults);
    }


}
