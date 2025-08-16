package org.apache.utilities;

import org.apache.model.AnalyzedClass;
import org.apache.model.AnalyzedMethod;
import org.apache.model.ClassMetrics;
import org.apache.model.MethodMetrics;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;


public class CsvWriter implements AutoCloseable {
    private final BufferedWriter writer;
    private final Object writeLock = new Object();
    private volatile boolean isClosed = false;

    public CsvWriter(String fileName) throws IOException {
        this.writer = new BufferedWriter(new FileWriter(fileName));
    }

    /**
     * Scrive l'intestazione del file CSV. Deve corrispondere esattamente all'ordine
     * dei dati scritti nel metodo buildRowString.
     */
    public void writeHeader() throws IOException {
        synchronized (writeLock) {
            if (isClosed) throw new IllegalStateException("CsvWriter è già chiuso.");

            // Corrisponde esattamente alla logica di scrittura
            writer.write("Release,ClassName,MethodName,LOC,Size,CycloComplexity,CognitiveComplexity,NestingDepth,NumCodeSmells," +
                    "NumAuthors,MethodHistory,Churn,MaxChurn,AvgChurn,IsBuggy");
            writer.newLine();
            writer.flush();
        }
    }

    /**
     * Metodo principale per scrivere i dati di una AnalyzedClass.
     * Itera su tutti i metodi della classe e scrive una riga CSV per ciascuno.
     *
     * @param analyzedClass L'oggetto classe contenente i dati da scrivere.
     */
    public void writeResultsForClass(AnalyzedClass analyzedClass) throws IOException {
        synchronized (writeLock) {
            if (isClosed) throw new IllegalStateException("CsvWriter è già chiuso.");

            // Scrivi una riga per ogni metodo trovato nella classe
            for (AnalyzedMethod method : analyzedClass.getMethods()) {
                String row = buildRowString(analyzedClass, method);
                writer.write(row);
                writer.newLine();
            }
        }
    }

    /**
     * Costruisce la stringa CSV per una singola riga (un metodo) combinando
     * dati dalla classe e dal metodo stesso.
     *
     * @param analyzedClass La classe contenitore.
     * @param method Il metodo per cui generare la riga.
     * @return Una stringa formattata in CSV.
     */
    private String buildRowString(AnalyzedClass analyzedClass, AnalyzedMethod method) {
        StringBuilder sb = new StringBuilder();

        // Dati contestuali dalla Classe
        // 1. Release
        sb.append(csvEscape(analyzedClass.getRelease() != null ? analyzedClass.getRelease().getReleaseID() : "N/A")).append(",");
        // 2. ClassName
        sb.append(csvEscape(analyzedClass.getClassName())).append(",");

        // Dati specifici del Metodo
        // 3. MethodName
        sb.append(csvEscape(method.getSimpleName())).append(",");

        MethodMetrics methodMetrics = method.getMetrics();
        // 4. LOC
        sb.append(methodMetrics.getLOC()).append(",");
        // 5. Size (Nota: Assumiamo che Size sia una metrica a livello di metodo. Se è a livello di classe, prendila da lì)
        sb.append(methodMetrics.getSize()).append(",");
        // 6. CycloComplexity
        sb.append(methodMetrics.getCycloComplexity()).append(",");
        // 7. CognitiveComplexity
        sb.append(methodMetrics.getCognitiveComplexity()).append(",");
        // 8. NestingDepth
        sb.append(methodMetrics.getNestingDepth()).append(",");
        // 9. NumCodeSmells
        sb.append(methodMetrics.getNumCodeSmells()).append(",");
        // 10. NumAuthors (del metodo)
        sb.append(methodMetrics.getNumAuthors()).append(",");

        // Metriche di processo (solitamente calcolate a livello di classe, ma riportate per ogni metodo)
        // Se hai queste metriche anche a livello di metodo, prendile da method.getMetrics()
        ClassMetrics classProcessMetrics = analyzedClass.getProcessMetrics();
        // 11. MethodHistory
        sb.append(classProcessMetrics.getMethodHistory()).append(",");
        // 12. Churn
        sb.append(classProcessMetrics.getChurn()).append(",");
        // 13. MaxChurn
        sb.append(classProcessMetrics.getMaxChurn()).append(",");
        // 14. AvgChurn
        sb.append(classProcessMetrics.getAvgChurn()).append(",");

        // Etichetta del target (buggyness) - A livello di metodo
        // 15. IsBuggy
        sb.append(method.isBuggy() ? "1" : "0");

        return sb.toString();
    }

    /**
     * Esegue l'escape dei valori CSV per gestire correttamente virgole, virgolette e a capo.
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