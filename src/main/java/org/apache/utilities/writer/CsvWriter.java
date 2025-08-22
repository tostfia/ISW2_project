package org.apache.utilities.writer;

import org.apache.model.AnalyzedClass;
import org.apache.model.AnalyzedMethod;
import org.apache.model.ClassMetrics;
import org.apache.model.MethodMetrics;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Locale;


/**
 * Scrive i risultati dell'analisi in un file CSV, con una riga per ogni metodo.
 * Questa versione è stata aggiornata per corrispondere esattamente alla struttura
 * della classe MethodMetrics e ai dati calcolati da MetricsController.
 */
public class CsvWriter implements AutoCloseable {
    private final BufferedWriter writer;
    private final Object writeLock = new Object();
    private volatile boolean isClosed = false;
    private final String targetName;


    // Il tuo orchestratore usa questo costruttore, quindi lo manteniamo così.
    public CsvWriter(String fileName,String targetName) throws IOException {
        this.writer = new BufferedWriter(new FileWriter(fileName));
        this.targetName = targetName;
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
                    "ProjectName","Release", "MethodName",
                    // Metriche di Complessità
                    "LOC", "ParameterCount", "CycloComplexity", "CognitiveComplexity", "NestingDepth",
                    // Metriche Storiche
                    "Revisions", "Authors",
                    "Fan-In", "Fan-Out",
                    // Metriche di Cambiamento Dettagliate
                    "TotalChurn", "MaxChurn", "AvgChurn", "NumberOfCodeSmells",
                    // Etichetta
                    "Bugginess"
            );
            writer.write(header);
            writer.newLine();
            writer.flush();
        }
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

    public void writeResultsForClass(List<AnalyzedClass> classes) throws IOException {
        synchronized (writeLock) {
            if (isClosed) throw new IOException("Writer è già chiuso.");

            for (AnalyzedClass ac : classes) {
                // Per ogni classe, iteriamo sui suoi metodi
                for (AnalyzedMethod am : ac.getMethods()) {
                    // Costruiamo e scriviamo una riga per ogni metodo
                    String row = buildHybridRowString(ac, am);
                    writer.write(row);
                    writer.newLine();
                }
            }
            writer.flush(); // Scrive i dati sul disco dopo aver processato la lista
        }
    }


    private String buildHybridRowString(AnalyzedClass analyzedClass, AnalyzedMethod analyzedMethod) {
        MethodMetrics methodMetrics = analyzedMethod.getMetrics();
        ClassMetrics cm = analyzedClass.getProcessMetrics();
        String methodNameFormatted = analyzedClass.getClassName() + "/" + analyzedMethod.getSignature();

        // Usiamo String.join per creare la riga CSV in modo pulito e sicuro
        return String.join(",",
                // Contesto
                csvEscape(targetName),
                String.valueOf(analyzedClass.getRelease().getReleaseName()),
                csvEscape(methodNameFormatted),


                String.valueOf(methodMetrics.getLoc()),
                String.valueOf(methodMetrics.getParameterCount()),
                String.valueOf(methodMetrics.getCycloComplexity()),
                String.valueOf(methodMetrics.getCognitiveComplexity()),
                String.valueOf(methodMetrics.getNestingDepth()),
                String.valueOf(cm.getNumberOfRevisions()),
                String.valueOf(cm.getNumAuthors()),
                String.valueOf(methodMetrics.getFanIn()),
                String.valueOf(methodMetrics.getFanOut()),
                String.valueOf(cm.getChurnMetrics().getVal()),
                String.valueOf(cm.getChurnMetrics().getMaxVal()),
                String.format(Locale.US, "%.2f", cm.getChurnMetrics().getAvgVal()),
                String.valueOf(methodMetrics.getNumberOfCodeSmells()) ,

                // Etichetta (usiamo quella della classe, che è stata calcolata da SZZ)
                analyzedMethod.isBuggy() ? "yes" : "no"
        );
    }
}