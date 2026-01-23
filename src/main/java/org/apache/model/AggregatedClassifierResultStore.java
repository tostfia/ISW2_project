package org.apache.model;




import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import org.apache.logging.Printer;

import java.io.File;
import java.io.IOException;
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
        if (!dir.exists()) dir.mkdirs();

        File file = new File(dir, project + ".json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(file, results);
    }

    /**
     * Carica una collezione di AggregatedClassifierResult per un progetto
     */
    public static Collection<AggregatedClassifierResult> load(String project) {
        File file = new File(STORAGE_DIR, project + ".json");
        if (!file.exists()) return new ArrayList<>();

        try {
            CollectionType listType = mapper.getTypeFactory()
                    .constructCollectionType(ArrayList.class, AggregatedClassifierResult.class);
            return mapper.readValue(file, listType);
        } catch (IOException e) {
            Printer.printYellow("Error loading aggregated classifier results from " + project);
            return new ArrayList<>();
        }
    }
}

