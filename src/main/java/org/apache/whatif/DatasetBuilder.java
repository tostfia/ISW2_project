package org.apache.whatif;


import org.apache.logging.Printer;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.CSVSaver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.function.DoublePredicate;

/**
 * Classe per la costruzione dei sotto-dataset necessari all’analisi What-If.
 * Usa sempre la feature "Number of Smells" per costruire i dataset B⁺, B e C.
 */
public class DatasetBuilder {

    private final String outputDir;

    public DatasetBuilder(String outputDir) {
        this.outputDir = outputDir.endsWith("/") ? outputDir : outputDir + "/";
    }

    private static final String RAW_FEATURE_NAME = "LOC";//Cambia qui per ogni progetto

    private static final String PROJECT_PREFIX = "BOOKKEEPER"; //Cambia qui per ogni progetto

    // Costruisce il dataset B+ (metodi con smells) - RITORNA Instances
    public Instances buildBPlus(Instances datasetA) {
        Printer.printGreen("Costruzione dataset B⁺: " + RAW_FEATURE_NAME + " > 0");
        return filterAndLog(datasetA, v -> v > 0, PROJECT_PREFIX + "_Bplus.csv");
    }

    // Costruisce il dataset C (metodi clean, senza smells) - CAMBIATO IN Instances
    public Instances buildC(Instances datasetA) {
        Printer.printGreen("Costruzione dataset C: " + RAW_FEATURE_NAME + " == 0");
        return filterAndLog(datasetA, v -> v == 0, PROJECT_PREFIX + "_C.csv");
    }

    // Costruisce il dataset B (what-if): copia di B⁺ con NumberOfSmells forzato a 0 - CAMBIATO IN Instances
    public Instances buildB(Instances datasetBPlus) {
        Printer.printGreen("Costruzione dataset B (what-if): B⁺ con " + RAW_FEATURE_NAME + " = 0");

        Instances cloned = new Instances(datasetBPlus);
        int index = getCleanAttributeIndex(cloned);

        for (Instance instance : cloned) {
            instance.setValue(index, 0); // Forza la feature a 0
        }

        exportToCsv(cloned, PROJECT_PREFIX + "_B.csv");
        return cloned; // Ritorna l'istanza modificata
    }

    // Filtra le istanze in base a una predicate sulla feature e le esporta
    private Instances filterAndLog(Instances data, DoublePredicate predicate, String exportFile) {
        int featureIndex = getCleanAttributeIndex(data);

        Instances filtered = new Instances(data, 0); // Copia lo schema esatto di A
        for (Instance instance : data) {
            if (predicate.test(instance.value(featureIndex))) {
                filtered.add(instance);
            }
        }

        exportToCsv(filtered, exportFile);
        return filtered;
    }

    // Esporta un dataset in formato CSV, ripulendo i nomi delle feature
    private void exportToCsv(Instances data, String fileName) {
        try {
            Files.createDirectories(Paths.get(outputDir));

            for (int i = 0; i < data.numAttributes(); i++) {
                Attribute attr = data.attribute(i);
                String clean = attr.name().replaceAll("[‘’“”'\"`]", "").trim();
                if (!attr.name().equals(clean)) {
                    data.renameAttribute(i, clean);
                }
            }

            CSVSaver saver = new CSVSaver();
            saver.setInstances(data);
            saver.setFile(new File(outputDir + fileName));
            saver.setFieldSeparator(",");
            saver.writeBatch();

        } catch (IOException e) {
            Printer.errorPrint(STR."Errore durante l'export in CSV: \{e.getMessage()}");
        }
    }

    // Trova l’indice della feature normalizzando i caratteri speciali
    private int getCleanAttributeIndex(Instances data) {
        String target = RAW_FEATURE_NAME.toLowerCase().replace(" ", "");

        for (int i = 0; i < data.numAttributes(); i++) {
            String current = data.attribute(i).name()
                    .toLowerCase()
                    .replaceAll("[‘’“”'\"`]", "") // Rimuove apici
                    .replace(" ", "");            // Rimuove spazi

            if (current.equals(target)) {
                return i;
            }
        }

        // Se non lo trova, stampa tutti per debug e lancia errore
        for(int i=0; i<data.numAttributes(); i++) {
            Printer.errorPrint("Disponibile: " + data.attribute(i).name());
        }
        throw new IllegalArgumentException("Attributo non trovato: " + RAW_FEATURE_NAME);
    }
}
