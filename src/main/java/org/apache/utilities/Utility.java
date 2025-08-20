package org.apache.utilities;

import org.apache.logging.CollectLogger;
import org.apache.model.ClassifierResult;
import org.apache.utilities.enumeration.FileExtension;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

public class Utility {
    private static final Logger logger = CollectLogger.getInstance().getLogger();
    private static final String CSV_HEADER_RESULTS = "DATASET," +
            "#TRAINING_RELEASES," +
            "%TRAINING_INSTANCES," +
            "CLASSIFIER," +
            "FEATURE_SELECTION," +
            "BALANCING," +
            "COST_SENSITIVE," +
            "PRECISION," +
            "RECALL," +
            "AREA_UNDER_ROC," +
            "KAPPA," +
            "TRUE_POSITIVES," +
            "FALSE_POSITIVES," +
            "TRUE_NEGATIVES," +
            "FALSE_NEGATIVES\n";



    public static void saveToCsv(String projectName, List<ClassifierResult> classifierResults) {
        final String resultsPath = "output" + File.separator + "results" + File.separator + projectName + File.separator;
        final String filename = projectName.toLowerCase(Locale.getDefault()) + "_report";
        try {
            File file = getFile(filename, resultsPath);
            try (FileWriter fileWriter = new FileWriter(file)) {
                fileWriter.append(Utility.CSV_HEADER_RESULTS);
                appendResults(projectName, fileWriter, classifierResults);
            }
        }catch(IOException e){
            logger.severe("Error saving results to CSV: " + e.getMessage());
        }
    }
    private static  File getFile(String filename, String path) throws IOException {
        File file = new File(path);
        if (!file.exists()) {
            boolean created = file.mkdirs();
            if (!created) {
                throw new IOException("Failed to create directories");
            }
        }
        file = new File(path + File.separator + filename + "." +
                FileExtension.CSV.name().toLowerCase(Locale.getDefault()));
        return file;
    }

    private static void appendResults(String projectName, FileWriter fileWriter,
                                          List<ClassifierResult> results) throws IOException {
        for (ClassifierResult classifierResult : results) {
            fileWriter.append(projectName).append(",")
                    .append(String.valueOf(classifierResult.getWalkForwardIteration())).append(",")
                    .append(String.valueOf(classifierResult.getTrainingPercent())).append(",")
                    .append(classifierResult.getClassifierName()).append(",");
            if (classifierResult.isFeatureSelection()) {
                fileWriter.append(classifierResult.getCustomClassifier().getFeatureSelectionFilterName()).append(",");
            } else {
                fileWriter.append("None").append(",");
            }
            if (classifierResult.hasSampling()) {
                fileWriter.append(classifierResult.getCustomClassifier().getSamplingFilterName()).append(",");
            } else {
                fileWriter.append("None").append(",");
            }
            if (classifierResult.isCostSensitive()) {
                fileWriter.append("SensitiveLearning").append(",");
            } else {
                fileWriter.append("None").append(",");
            }
            fileWriter.append(String.valueOf(classifierResult.getPrecision())).append(",")
                    .append(String.valueOf(classifierResult.getRecall())).append(",")
                    .append(String.valueOf(classifierResult.getAreaUnderROC())).append(",")
                    .append(String.valueOf(classifierResult.getKappa())).append(",")
                    .append(String.valueOf(classifierResult.getTruePositives())).append(",")
                    .append(String.valueOf(classifierResult.getFalsePositives())).append(",")
                    .append(String.valueOf(classifierResult.getTrueNegatives())).append(",")
                    .append(String.valueOf(classifierResult.getFalseNegatives())).append("\n");
        }

    }

}
