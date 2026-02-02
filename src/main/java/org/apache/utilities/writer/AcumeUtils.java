package org.apache.utilities.writer;


import org.apache.model.AcumeRecord;

import java.io.IOException;
import java.util.List;

public class AcumeUtils {
    public static void writeAcumeCSV(String projectName, List<AcumeRecord> records) throws IOException {
        // Crea la cartella se non esiste
        java.io.File dir = new java.io.File("acume_data/test/");
        if (!dir.exists()) dir.mkdirs();

        String fileName = "acume_data/test/" + projectName + "_results.csv";

        try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(fileName))) {
            // L'header deve essere esattamente questo per far leggere correttamente i file a main.py di ACUME
            writer.println("ID,Size,Predicted,Actual");

            for (AcumeRecord r : records) {
                writer.printf(java.util.Locale.US, "%d,%d,%.6f,%s%n",
                        r.getId(), r.getSize(), r.getPredictedProbability(), r.getActual());
            }
        }
    }
}
