package org.apache.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import org.apache.logging.Printer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;

public class AggregatedClassifierResultStore {

    private static final String STORAGE_DIR = "aggregated_results";
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Salva una collezione di AggregatedClassifierResult per un progetto
     */
    public static void save(String project, Collection<AggregatedClassifierResult> results) throws IOException {
        File dir = new File(STORAGE_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create directory: " + STORAGE_DIR);
        }

        File file = new File(dir, project + ".json");

        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, results);
            Printer.printlnGreen("Results saved to: " + file.getAbsolutePath() + " (" + results.size() + " classifiers)");
        } catch (IOException e) {
            Printer.errorPrint("Failed to save results for " + project + ": " + e.getMessage());
            throw e;
        }
    }

    /**
     * Carica una collezione di AggregatedClassifierResult per un progetto
     */
    public static Collection<AggregatedClassifierResult> load(String project) {
        File file = new File(STORAGE_DIR, project + ".json");

        if (!file.exists()) {
            Printer.printYellow("No results file found for project: " + project + ", creating new collection");
            return new ArrayList<>();
        }

        try {
            CollectionType listType = mapper.getTypeFactory()
                    .constructCollectionType(ArrayList.class, AggregatedClassifierResult.class);

            Collection<AggregatedClassifierResult> results = mapper.readValue(file, listType);

            if (results == null) {
                Printer.printYellow("Results file is empty for project: " + project);
                return new ArrayList<>();
            }

            Printer.printlnBlue("Loaded " + results.size() + " classifier results from: " + file.getAbsolutePath());
            return results;

        } catch (JsonParseException | JsonMappingException e) {
            // JSON corrotto o incompatibile
            Printer.printYellow("Corrupted or incompatible JSON file for project: " + project);
            Printer.printYellow("Error: " + e.getMessage());

            // Backup del file corrotto
            File backup = new File(file.getAbsolutePath() + ".backup");
            try {
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                Printer.printYellow("Corrupted file backed up to: " + backup.getAbsolutePath());
            } catch (IOException ex) {
                Printer.printYellow("Could not create backup: " + ex.getMessage());
            }

            return new ArrayList<>();

        } catch (IOException e) {
            Printer.errorPrint("Error loading aggregated classifier results from " + project + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }




    /**
     * Aggiunge un singolo risultato ai risultati esistenti.
     * Rimuove eventuali risultati precedenti dello stesso classificatore.
     */
    public static void append(String project, AggregatedClassifierResult newResult) throws IOException {
        Collection<AggregatedClassifierResult> existing = load(project);

        // Rimuovi eventuali risultati precedenti dello stesso classificatore
        existing.removeIf(r -> r.getClassifierName().equals(newResult.getClassifierName()));

        // Aggiungi il nuovo risultato
        existing.add(newResult);

        // Salva tutto
        save(project, existing);
    }

    /**
     * Ottiene un singolo risultato per classificatore.
     */
    public static AggregatedClassifierResult get(String project, String classifierName) {
        Collection<AggregatedClassifierResult> results = load(project);
        return results.stream()
                .filter(r -> r.getClassifierName().equals(classifierName))
                .findFirst()
                .orElse(null);
    }


}