package org.apache.controller;

import org.apache.logging.CollectLogger;
import tech.tablesaw.api.IntColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.api.StringColumn;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.HashSet;

import tech.tablesaw.columns.Column;
import tech.tablesaw.selection.Selection;
import weka.core.Attribute;
import weka.core.Instances;
import weka.core.DenseInstance;
import weka.core.converters.ArffSaver;

public class DatasetController {

    private final String csvFilePath;
    private static final Logger logger = CollectLogger.getInstance().getLogger();

    public DatasetController(String projectName) {
        this.csvFilePath = projectName + "_dataset.csv";
    }

    public Table prepareDatasetA(double cutPercentage) {
        File inputFile = new File(csvFilePath);
        if (!inputFile.exists()) {
            logger.severe("ERRORE: File dataset non trovato: " + csvFilePath);
            return null;
        }

        Table fullDataset;
        try {
            fullDataset = Table.read().csv(csvFilePath);
        } catch (Exception e) {
            logger.severe("ERRORE: Impossibile leggere il file CSV: " + csvFilePath + " - " + e.getMessage());
            return null;
        }

        if (fullDataset.isEmpty()) {
            logger.warning("ATTENZIONE: Il file dataset è vuoto: " + csvFilePath);
            return fullDataset;
        }

        if (!fullDataset.columnNames().contains("Release")) {
            logger.severe("ERRORE: La colonna 'Release' non è presente nel file CSV.");
            return null;
        }

        // Recupera le release ID uniche e le ordina
        StringColumn releaseColumn = fullDataset.stringColumn("Release");
        List<String> uniqueReleases = new ArrayList<>(new HashSet<>(releaseColumn.asList()));
        Collections.sort(uniqueReleases);

        int totalReleases = uniqueReleases.size();
        if (totalReleases == 0) {
            logger.warning("ATTENZIONE: Nessuna release valida trovata nel dataset.");
            return Table.create("EmptyDatasetA");
        }

        // Calcolo del taglio percentuale
        int releasesToKeepCount = (int) Math.round(totalReleases * cutPercentage);

        if (releasesToKeepCount == 0) {
            releasesToKeepCount = 1;
            logger.warning(String.format("Il calcolo della percentuale (%.2f%%) ha portato a 0 release da mantenere. Mantenuta almeno la prima release.", cutPercentage * 100));
        } else if (releasesToKeepCount > totalReleases) {
            releasesToKeepCount = totalReleases;
        }

        List<String> releasesForDatasetA = uniqueReleases.subList(0, releasesToKeepCount);

        // 2. Logga le release che verranno mantenute
        logger.info(String.format("Filtraggio del dataset: mantenute le prime %d release (corrispondenti a %.2f%% del totale) su %d. Release mantenute per Dataset A: %s",
                releasesToKeepCount, cutPercentage * 100, totalReleases, releasesForDatasetA));

        // 3. Filtra il fullDataset usando la lista delle releaseForDatasetA
        // Qui chiami il metodo helper che filtra per una lista di release
        return filterTableByReleases(fullDataset, releasesForDatasetA);
    }


    public int generateWalkForwardArffFiles(Table datasetA, String projectName, int walkForwardIterations) throws Exception {
        logger.info("Inizio generazione file ARFF per Walk-Forward per il progetto: " + projectName);

        final String arffExtension = ".arff";
        final String outputBaseDir = "output" + File.separator + "dataset" + File.separator + projectName;
        final String trainingBaseDir = outputBaseDir + File.separator + "TRAINING";
        final String testingBaseDir = outputBaseDir + File.separator + "TESTING";

        // Crea le directory se non esistono
        new File(trainingBaseDir).mkdirs();
        new File(testingBaseDir).mkdirs();

        List<String> allReleasesFromDatasetAInt = new ArrayList<>(new HashSet<>(datasetA.stringColumn("Release").asList()));
        Collections.sort(allReleasesFromDatasetAInt);

        // --- AGGIUNGI QUESTI LOG DI DEBUG ---
        logger.info("DEBUG: Release estratte da datasetA per Walk-Forward: " + allReleasesFromDatasetAInt);
        logger.info("DEBUG: Numero di release estratte da datasetA: " + allReleasesFromDatasetAInt.size());
        logger.info("DEBUG: walkForwardIterations (calcolato dal JiraController/input): " + walkForwardIterations);

        List<String> allReleasesFromDatasetA = new ArrayList<>();
        for (String releaseNum : allReleasesFromDatasetAInt) {
            allReleasesFromDatasetA.add(String.valueOf(releaseNum));
        }

        if (allReleasesFromDatasetA.size() < 2) {
            logger.severe("Dataset A ha meno di 2 release. Impossibile eseguire Walk-Forward.");
            return 0;
        }

        int actualIterations = Math.min(walkForwardIterations, allReleasesFromDatasetA.size() - 1);
        logger.info("DEBUG: Numero effettivo di iterazioni di Walk-Forward calcolate (actualIterations): " + actualIterations);

        if (actualIterations < walkForwardIterations) {
            logger.warning("Il numero di iterazioni di Walk-Forward richiesto (" + walkForwardIterations + ") è maggiore del numero massimo possibile con le release disponibili (" + actualIterations + "). Verranno eseguite " + actualIterations + " iterazioni.");
        }

        for (int i = 0; i < actualIterations; i++) {
            List<String> trainingReleases = allReleasesFromDatasetA.subList(0, i + 1);
            String testingRelease = allReleasesFromDatasetA.get(i + 1);

            logger.info(String.format("Iterazione %d: Training su release %s, Testing su release %s",
                    (i + 1), trainingReleases, testingRelease));

            Instances trainingInstances = convertTablesawToWekaInstances(datasetA, trainingReleases, projectName + "_Training_" + (i+1));
            Instances testingInstances = convertTablesawToWekaInstances(datasetA, List.of(testingRelease), projectName + "_Testing_" + (i+1));

            if (trainingInstances.numAttributes() > 0) {
                trainingInstances.setClassIndex(trainingInstances.numAttributes() - 1);
            }
            if (testingInstances.numAttributes() > 0) {
                testingInstances.setClassIndex(testingInstances.numAttributes() - 1);
            } else {
                logger.warning("Il test set per l'iterazione " + (i+1) + " non contiene attributi o è vuoto. Potrebbe essere un problema.");
            }

            String trainingFilePath = trainingBaseDir + File.separator + projectName + "_" + (i + 1) + arffExtension;
            String testingFilePath = testingBaseDir + File.separator + projectName + "_" + (i + 1) + arffExtension;

            saveArffFile(trainingInstances, trainingFilePath);
            saveArffFile(testingInstances, testingFilePath);
        }
        logger.info("Generazione file ARFF completata.");
        return actualIterations;
    }

    private void saveArffFile(Instances instances, String filePath) throws IOException {
        ArffSaver saver = new ArffSaver();
        saver.setInstances(instances);
        saver.setFile(new File(filePath));
        saver.writeBatch();
        logger.info("Salvato file: " + filePath);
    }

    public Instances convertTablesawToWekaInstances(Table sourceTable, List<String> targetReleases, String datasetName) throws Exception {
        Table filteredTable = filterTableByReleases(sourceTable, targetReleases);

        if (filteredTable.isEmpty()) {
            logger.warning("Filtered Tablesaw table is empty for releases: " + targetReleases + ". Creating empty Weka Instances.");
            // Passa la sourceTable completa per creare gli attributi, anche se la filteredTable è vuota
            return createEmptyWekaInstances(datasetName, sourceTable);
        }

        ArrayList<Attribute> attributes = createWekaAttributes(filteredTable);
        Instances wekaInstances = new Instances(datasetName, attributes, 0);

        for (int i = 0; i < filteredTable.rowCount(); i++) {
            DenseInstance instance = new DenseInstance(attributes.size());

            for (Attribute attr : attributes) {
                String attrName = attr.name();

                try {
                    // Assicurati che la colonna esista nella tabella filtrata
                    if (!filteredTable.columnNames().contains(attrName)) {
                        logger.warning("Colonna Tablesaw '" + attrName + "' non trovata nella tabella filtrata. Skippato l'impostazione del valore per questa istanza.");
                        continue; // Passa al prossimo attributo
                    }

                    if (attr.isNumeric()) {
                        double value = getNumericValue(filteredTable, attrName, i);
                        instance.setValue(attr, value);
                    } else if (attr.isNominal()) {
                        String value = getStringValue(filteredTable, attrName, i);
                        if (value != null && !value.trim().isEmpty()) {
                            if (attr.indexOfValue(value) != -1) { // Verifica che il valore sia tra i nominali definiti
                                instance.setValue(attr, value);
                            } else {
                                logger.warning(String.format("Valore nominale '%s' non riconosciuto per attributo '%s'. Alla riga %d. Assegnato Missing Value.", value, attrName, i));
                                instance.setMissing(attr); // Imposta come valore mancante di Weka
                            }
                        } else {
                            logger.warning(String.format("Valore nullo/vuoto per attributo nominale '%s' alla riga %d. Assegnato Missing Value.", attrName, i));
                            instance.setMissing(attr); // Imposta come valore mancante di Weka
                        }
                    } else if (attr.isString()) { // Gestione esplicita di attributi di tipo String (testo libero)
                        String value = getStringValue(filteredTable, attrName, i);
                        instance.setValue(attr, value);
                    }
                } catch (Exception e) {
                    logger.warning(String.format("Errore imprevisto nell'impostare il valore per l'attributo '%s' (tipo Weka: %d) alla riga %d: %s. Valore sorgente: '%s'. Assegnando Missing Value/Default.",
                            attrName, attr.type(), i, e.getMessage(), filteredTable.column(attrName).getString(i)));
                    instance.setMissing(attr); // In caso di errore inaspettato, imposta missing
                }
            }
            wekaInstances.add(instance);
        }

        return wekaInstances;
    }


    private Table filterTableByReleases(Table sourceTable, List<String> targetReleases) {
        StringColumn releaseCol = sourceTable.stringColumn("Release");

        if (targetReleases.size() == 1) {
            return sourceTable.where(releaseCol.isEqualTo(targetReleases.getFirst()));
        } else {
            Selection selection = null;
            for (String release : targetReleases) {
                Selection currentSelection = releaseCol.isEqualTo(release);
                if (selection == null) {
                    selection = currentSelection;
                } else {
                    selection = selection.or(currentSelection);
                }
            }
            // Se selection è null (lista targetReleases vuota), restituisci una tabella vuota
            return selection != null ? sourceTable.where(selection) : Table.create("EmptyFilteredTable");
        }
    }

    private ArrayList<Attribute> createWekaAttributes(Table table) {
        ArrayList<Attribute> attributes = new ArrayList<>();

        for (Column<?> col : table.columns()) {
            String colName = col.name();

            // Ignora colonne non necessarie per la classificazione
            if (colName.equalsIgnoreCase("Release") || colName.equalsIgnoreCase("MethodName") || colName.equalsIgnoreCase("ProjectName")) {
                continue;
            }

            if (colName.equalsIgnoreCase("bugginess")) {
                ArrayList<String> classValues = new ArrayList<>();
                classValues.add("yes");
                classValues.add("no");
                attributes.add(new Attribute(colName, classValues));
            } else if (col instanceof IntColumn || col instanceof DoubleColumn) {
                attributes.add(new Attribute(colName)); // Numerico
            } else if (col instanceof StringColumn) {
                Set<String> uniqueValues = new HashSet<>();
                for (int i = 0; i < col.size(); i++) {
                    String value = col.getString(i);
                    if (value != null && !value.trim().isEmpty()) {
                        uniqueValues.add(value.trim());
                    }
                }

                // Euristiche: se pochi valori unici, è nominale; altrimenti, è stringa
                if (!uniqueValues.isEmpty() && uniqueValues.size() < 50) { // Limite ragionevole per nominali
                    ArrayList<String> nominalValues = new ArrayList<>(uniqueValues);
                    Collections.sort(nominalValues); // Ordina i valori nominali
                    attributes.add(new Attribute(colName, nominalValues));
                } else {
                    // Troppi valori unici o nessun valore, trattato come stringa (testo libero)
                    logger.warning(String.format("Colonna '%s' ha troppi valori unici (%d) o è vuota. Trattata come attributo String.", colName, uniqueValues.size()));
                    attributes.add(new Attribute(colName, (ArrayList<String>) null)); // Tipo String Weka
                }
            } else {
                logger.warning("Tipo di colonna Tablesaw non riconosciuto per Weka Attribute: " + colName + " (" + col.type() + "). Skippato.");
            }
        }

        // Sposta l'attributo classe alla fine
        moveClassAttributeToEnd(attributes);

        return attributes;
    }

    private void moveClassAttributeToEnd(ArrayList<Attribute> attributes) {
        int classIndex = -1;
        for (int j = 0; j < attributes.size(); j++) {
            if (attributes.get(j).name().equalsIgnoreCase("bugginess")) {
                classIndex = j;
                break;
            }
        }

        if (classIndex != -1 && classIndex != attributes.size() - 1) {
            Attribute classAttribute = attributes.remove(classIndex);
            attributes.add(classAttribute);
        } else if (classIndex == -1) {
            logger.severe("Errore: Attributo classe 'bugginess' non trovato tra le colonne del dataset! Assicurati che esista e sia nominato 'bugginess'.");
            throw new IllegalArgumentException("Attributo classe 'bugginess' non trovato.");
        }
    }

    private double getNumericValue(Table table, String columnName, int rowIndex) {
        Column<?> column = table.column(columnName);
        if (column instanceof DoubleColumn) {
            return ((DoubleColumn) column).getDouble(rowIndex);
        } else if (column instanceof IntColumn) {
            return ((IntColumn) column).getInt(rowIndex);
        } else {
            // Se la colonna non è numerica direttamente, prova a parsare da stringa
            String stringValue = column.getString(rowIndex);
            try {
                return Double.parseDouble(stringValue);
            } catch (NumberFormatException e) {
                logger.warning("Impossibile convertire '" + stringValue + "' in numero per la colonna " + columnName + " alla riga " + rowIndex + ". Restituisco 0.0.");
                return 0.0;
            }
        }
    }

    private String getStringValue(Table table, String columnName, int rowIndex) {
        Column<?> column = table.column(columnName);
        return column.getString(rowIndex);
    }

    private Instances createEmptyWekaInstances(String datasetName, Table referenceTable) throws Exception {
        ArrayList<Attribute> attributes = createWekaAttributes(referenceTable);
        return new Instances(datasetName, attributes, 0);
    }
}