package org.apache.utilities;

import lombok.Getter;
import org.apache.model.AnalyzedClass;
import org.apache.model.AnalyzedMethod;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;


public class CsvWriter {
    private final BufferedWriter writer;
    private final Object writeLock = new Object();

    @Getter
    private volatile boolean isClosed = false;

    public CsvWriter(String fileName) throws IOException {
        this.writer = new BufferedWriter(new FileWriter(fileName));
    }

    public void writeHeader() throws IOException {
        synchronized (writeLock) {
            if (isClosed) throw new IllegalStateException("CsvWriter già chiuso");

            writer.write("Release,ClassName,MethodName,LOC,Size,CycloComplexity,CognitiveComplexity,NestingDepth,NumCodeSmells" +
                    "NumAuthors,MethodHistory,Churn,MaxChurn,AvgChurn,IsBuggy");
            writer.newLine();
            writer.flush();
        }
    }


    public void writeClassData(AnalyzedClass analyzedClass) throws IOException {
        synchronized (writeLock) {
            if (isClosed) throw new IllegalStateException("CsvWriter già chiuso");

            List<AnalyzedMethod> methods = analyzedClass.getMethods();
            if (methods == null || methods.isEmpty()) {
                // Se non ci sono metodi, scrivi comunque una riga per la classe
                writeClassRow(analyzedClass, null);
                return;
            }

            // Scrivi una riga per ogni metodo
            for (AnalyzedMethod method : methods) {
                writeClassRow(analyzedClass, method);
            }

            writer.flush(); // Assicura che i dati siano scritti
        }
    }

    private void writeClassRow(AnalyzedClass analyzedClass, AnalyzedMethod method) throws IOException {
        StringBuilder row = new StringBuilder();

        // Release info
        row.append(csvEscape(analyzedClass.getRelease() != null ?
                analyzedClass.getRelease().getReleaseID() : "Unknown")).append(",");

        // Class info
        row.append(csvEscape(analyzedClass.getClassName())).append(",");

        // Method info
        if (method != null) {
            row.append(csvEscape(method.getSimpleName())).append(",");

            // Static metrics
            row.append(method.getMetrics().getLOC()).append(",");
            row.append(method.getMetrics().getSize()).append(",");
            row.append(method.getMetrics().getCycloComplexity()).append(",");
            row.append(method.getMetrics().getCognitiveComplexity()).append(",");
            row.append(method.getMetrics().getNestingDepth()).append(",");
            row.append(method.getMetrics().getNumCodeSmells()).append(",");

        } else {
            // Metodo nullo - scrivi valori di default
            row.append("N/A,N/A,0,0,0,");
        }

        // Process metrics (a livello di classe)
        if (analyzedClass.getProcessMetrics() != null) {
            row.append(analyzedClass.getProcessMetrics().getNumAuthors()).append(",");
            row.append(analyzedClass.getProcessMetrics().getMethodHistory()).append(",");
            row.append(analyzedClass.getProcessMetrics().getChurn()).append(",");
            row.append(analyzedClass.getProcessMetrics().getMaxChurn()).append(",");
            row.append(analyzedClass.getProcessMetrics().getAvgChurn()).append(",");
        } else {
            row.append("0,0,0,0,0,");
        }

        // Bug label
        row.append(analyzedClass.isBuggy() ? "1" : "0");

        writer.write(row.toString());
        writer.newLine();
    }



    /**
     * Escape dei valori CSV per gestire virgole e virgolette
     */
    private String csvEscape(String value) {
        if (value == null) return "";

        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Chiude il writer
     */
    public void close() throws IOException {
        synchronized (writeLock) {
            if (!isClosed) {
                writer.close();
                isClosed = true;
            }
        }
    }

}