package org.apache.utilities.writer;

import org.apache.model.AnalyzedClass;
import org.apache.model.AnalyzedMethod;
import org.apache.model.MethodMetrics;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;


/**
 * Scrive i risultati dell'analisi in un file CSV, con una riga per ogni metodo.
 * Questa versione è stata aggiornata per corrispondere esattamente alla struttura
 * della classe MethodMetrics e ai dati calcolati da MetricsController.
 */
public class CsvWriter implements AutoCloseable {
    private final BufferedWriter writer;
    private final Object writeLock = new Object();
    private volatile boolean isClosed = false;

    // Il tuo orchestratore usa questo costruttore, quindi lo manteniamo così.
    public CsvWriter(String fileName) throws IOException {
        this.writer = new BufferedWriter(new FileWriter(fileName));
    }

    /**
     * Scrive l'intestazione del file CSV. L'ordine è cruciale.
     */
    public void writeHeader() throws IOException {
        synchronized (writeLock) {
            if (isClosed) throw new IllegalStateException("CsvWriter è già chiuso.");

            // Header aggiornato per riflettere TUTTE le metriche calcolate in MethodMetrics.
            String header = String.join(",",
                    // Contesto
                    "ReleaseID", "ClassName", "MethodSignature",
                    // Metriche di Complessità
                    "LOC", "ParameterCount", "CycloComplexity", "CognitiveComplexity", "NestingDepth",
                    // Metriche Storiche
                    "Revisions", "Authors",
                    // Metriche di Cambiamento Dettagliate
                    "TotalStmtAdded", "MaxStmtAdded", "AvgStmtAdded",
                    "TotalStmtDeleted", "MaxStmtDeleted", "AvgStmtDeleted",
                    "TotalChurn", "MaxChurn", "AvgChurn",
                    // Etichetta
                    "Bugginess"
            );
            writer.write(header);
            writer.newLine();
            writer.flush();
        }
    }

    /**
     * Scrive i risultati per una singola classe analizzata, creando una riga per ogni metodo.
     * Questo metodo è usato dal tuo orchestratore.
     */
    public void writeResultsForClass(AnalyzedClass analyzedClass) throws IOException {
        synchronized (writeLock) {
            if (isClosed) throw new IllegalStateException("CsvWriter è già chiuso.");

            for (AnalyzedMethod method : analyzedClass.getMethods()) {
                String row = buildRowString(analyzedClass, method);
                writer.write(row);
                writer.newLine();
            }
        }
    }

    /**
     * Costruisce la stringa CSV per un singolo metodo.
     * ORA LEGGE I DATI CORRETTI DALLA CLASSE MethodMetrics.
     */
    private String buildRowString(AnalyzedClass analyzedClass, AnalyzedMethod method) {
        StringBuilder sb = new StringBuilder();
        MethodMetrics metrics = method.getMetrics(); // L'unica fonte di dati per le metriche del metodo

        // --- Contesto ---
        sb.append(csvEscape(analyzedClass.getRelease() != null ? String.valueOf(analyzedClass.getRelease().getReleaseID()) : "N/A")).append(",");
        sb.append(csvEscape(analyzedClass.getClassName())).append(",");
        sb.append(csvEscape(method.getSignature())).append(",");

        // --- Metriche di Complessità ---
        sb.append(metrics.getLOC()).append(",");
        sb.append(metrics.getParameterCount()).append(",");
        sb.append(metrics.getCycloComplexity()).append(",");
        sb.append(metrics.getCognitiveComplexity()).append(",");
        sb.append(metrics.getNestingDepth()).append(",");

        // --- Metriche Storiche ---
        sb.append(metrics.getNumberOfRevisions()).append(","); // Corrisponde a 'methodHistories'
        sb.append(metrics.getNumAuthors()).append(",");

        // --- Metriche di Cambiamento Dettagliate ---
        // Leggiamo i campi individuali da MethodMetrics
        sb.append(metrics.getTotalStmtAdded()).append(",");
        sb.append(metrics.getMaxStmtAdded()).append(",");
        sb.append(String.format("%.2f", metrics.getAvgStmtAdded())).append(",");

        sb.append(metrics.getTotalStmtDeleted()).append(",");
        sb.append(metrics.getMaxStmtDeleted()).append(",");
        sb.append(String.format("%.2f", metrics.getAvgStmtDeleted())).append(",");

        sb.append(metrics.getTotalChurn()).append(",");
        sb.append(metrics.getMaxChurn()).append(",");
        sb.append(String.format("%.2f", metrics.getAvgChurn())).append(",");

        // --- Etichetta ---
        sb.append(metrics.isBuggy() ? "yes" : "no"); // Formato richiesto dalle slide

        return sb.toString();
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    @Override
    public void close() throws IOException {
        synchronized (writeLock) {
            if (!isClosed) {
                try {
                    writer.flush();
                    writer.close();
                } finally {
                    isClosed = true;
                }
            }
        }
    }
}