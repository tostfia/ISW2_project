package org.apache.utilities.writer;


import org.apache.model.AnalyzedClass;
import org.apache.model.AnalyzedMethod;
import org.apache.model.MethodMetrics;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;



/**
 * Writer specializzato per la Fase 1 (Analisi Storica).
 * Il suo unico scopo è salvare su un file CSV le metriche storiche
 * calcolate per ogni metodo, che sono costose da ottenere.
 * Questo file servirà da input per la Fase 2.
 */
public class HistoricalDataWriter implements AutoCloseable {

    private final BufferedWriter writer;
    private volatile boolean isClosed = false;
    private final Object writeLock = new Object();


    public HistoricalDataWriter(String fileName) throws IOException {
        this.writer = new BufferedWriter(new FileWriter(fileName));
    }

    /**
     * Scrive l'intestazione del file CSV intermedio.
     */
    public void writeHeader() throws IOException {
        synchronized (writeLock) {
            if (isClosed) throw new IOException("Writer è già chiuso.");
            // Header con le metriche storiche che abbiamo calcolato
            writer.write("ReleaseID,ClassName,MethodSignature,Revisions,Authors,TotalChurn,IsBuggy\n");
            writer.flush();
        }
    }

    /**
     * Scrive i risultati per una lista di classi (tipicamente uno snapshot di una release).
     * È thread-safe per essere usato in ambienti paralleli.
     */
    public void writeResults(List<AnalyzedClass> classList) throws IOException {
        synchronized (writeLock) {
            if (isClosed) throw new IOException("Writer è già chiuso.");

            for (AnalyzedClass ac : classList) {
                for (AnalyzedMethod method : ac.getMethods()) {
                    MethodMetrics mm = method.getMetrics();
                    String row = String.join(",",
                            String.valueOf(ac.getRelease().getReleaseID()),
                            csvEscape(ac.getClassName()),
                            csvEscape(method.getSignature()),
                            String.valueOf(mm.getNumberOfRevisions()), // Assicurati di usare il getter corretto
                            String.valueOf(mm.getNumAuthors()),
                            String.valueOf(mm.getTotalChurn()),
                            mm.isBuggy() ? "yes" : "no"
                    );
                    writer.write(row);
                    writer.newLine();
                }
            }
            writer.flush(); // Flush dopo ogni batch di release per sicurezza
        }
    }

    /**
     * Esegue l'escape dei valori che potrebbero contenere virgole o virgolette.
     */
    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Chiude il writer in modo sicuro.
     * Verrà chiamato automaticamente se usi il try-with-resources.
     */
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