package org.apache.controller;

import org.apache.logging.Printer;
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


public class WekaController {
    private final String projectName;
    private final int iterations;
    private final List<ClassifierResult> classifierResults;


    public WekaController(String projectName,int iterations){
        this.projectName=projectName;
        this.iterations=iterations;
        this.classifierResults = new ArrayList<>();
    }

    public void classify(){
        final String arffExtension = FileExtension.ARFF.getId(); // Assumiamo restituisca "arff" (senza punto)

        // Questo è il percorso base fino alla cartella del progetto (es. output/dataset/BOOKKEEPER)
        final String projectBaseDir = "output" + File.separator + "dataset" + File.separator + projectName;

        // I percorsi alle directory TRAINING e TESTING
        final String trainingDir = projectBaseDir + File.separator + DataSet.TRAINING;
        final String testingDir = projectBaseDir + File.separator + DataSet.TESTING;


        CountDownLatch countDownLatch = new CountDownLatch(this.iterations);
        for( int walkForwarIteration= 1; walkForwarIteration <= this.iterations; walkForwarIteration++ ){
            final int iteration = walkForwarIteration;
            Runnable task=()->{
                try {
                    // COSTRUZIONE DEL PERCORSO FILE CORRETTA
                    String currentTrainingPath = trainingDir + File.separator + this.projectName + "_" + iteration + "." + arffExtension;
                    String currentTestingPath = testingDir + File.separator + this.projectName + "_" + iteration + "." + arffExtension;

                    Printer.print("DEBUG (WekaController): Tentativo di caricare Training Set da: " + currentTrainingPath+ "\n");
                    Printer.print("DEBUG (WekaController): Tentativo di caricare Testing Set da: " + currentTestingPath+ "\n");
                    File trainingFile = new File(currentTrainingPath);
                    File testingFile = new File(currentTestingPath);
                    if (!trainingFile.exists()) {
                        Printer.errorPrint("ERRORE: File di training non trovato: " + currentTrainingPath);
                        return; // Salta questa iterazione
                    }
                    if (!testingFile.exists()) {
                        Printer.errorPrint("ERRORE: File di testing non trovato: " + currentTestingPath);
                        return; // Salta questa iterazione
                    }

                    ConverterUtils.DataSource trainingSet = new ConverterUtils.DataSource(trainingFile.getAbsolutePath());
                    ConverterUtils.DataSource testingSet = new ConverterUtils.DataSource(testingFile.getAbsolutePath());

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
                    Printer.errorPrint("Error during classification: " + e.getMessage());
                } finally {
                    countDownLatch.countDown();
                }
            };
            new Thread(task).start();
        }
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            Printer.errorPrint("Classification interrupted: " + e.getMessage());
        }
    }

    public void saveResults(){
        Utility.saveToCsv(this.projectName,this.classifierResults);
    }



}
