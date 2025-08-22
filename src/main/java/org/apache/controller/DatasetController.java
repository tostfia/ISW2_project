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
    private static final String RELEASE= "Release";

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
            logger.severe(String.format("ERRORE: File dataset non trovato: " + csvFilePath));
            return null;
        }

        if (fullDataset.isEmpty()) {
            logger.warning(String.format("ATTENZIONE: Il dataset caricato da %s è vuoto. Restituisco un dataset vuoto senza filtri.", csvFilePath));
            return fullDataset;
        }

        if (!fullDataset.columnNames().contains(RELEASE)) {
            logger.severe("ERRORE: La colonna 'Release' non è presente nel file CSV.");
            return null;
        }

        // Recupera le release ID uniche e le ordina
        StringColumn releaseColumn = fullDataset.stringColumn(RELEASE);
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


    public int generateWalkForwardArffFiles(Table datasetA, String projectName, int walkForwardIterations) throws IOException {
        logger.info("Inizio generazione file ARFF per Walk-Forward per il progetto: " + projectName);

        final String arffExtension = ".arff";
        final String outputBaseDir = "output" + File.separator + "dataset" + File.separator + projectName;
        final String trainingBaseDir = outputBaseDir + File.separator + "TRAINING";
        final String testingBaseDir = outputBaseDir + File.separator + "TESTING";

        // Crea le directory se non esistono
        new File(trainingBaseDir);
        new File(testingBaseDir);

        List<String> allReleasesFromDatasetAInt = new ArrayList<>(new HashSet<>(datasetA.stringColumn(RELEASE).asList()));
        Collections.sort(allReleasesFromDatasetAInt);


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

            try {
                saveArffFile(trainingInstances, trainingFilePath);
                saveArffFile(testingInstances, testingFilePath);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

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

    public Instances convertTablesawToWekaInstances(Table sourceTable, List<String> targetReleases, String datasetName) {
        Table filteredTable = filterTableByReleases(sourceTable, targetReleases);

        if (filteredTable.isEmpty()) {
            logger.warning("Filtered Tablesaw table is empty for releases: " + targetReleases + ". Creating empty Weka Instances.");
            return createEmptyWekaInstances(datasetName, sourceTable);
        }

        ArrayList<Attribute> attributes = createWekaAttributes(filteredTable);
        Instances wekaInstances = new Instances(datasetName, attributes, 0);

        for (int i = 0; i < filteredTable.rowCount(); i++) {
            DenseInstance instance = new DenseInstance(attributes.size());
            for (Attribute attr : attributes) {
                setInstanceValue(instance, attr, filteredTable, i);
            }
            wekaInstances.add(instance);
        }
        return wekaInstances;
    }

    private void setInstanceValue(DenseInstance instance, Attribute attr, Table table, int rowIndex) {
        String attrName = attr.name();
        if (!table.columnNames().contains(attrName)) {
            logger.warning("Colonna Tablesaw '" + attrName + "' non trovata nella tabella filtrata. Skippato l'impostazione del valore per questa istanza.");
            return;
        }
        try {
            if (attr.isNumeric()) {
                instance.setValue(attr, getNumericValue(table, attrName, rowIndex));
            } else if (attr.isNominal()) {
                String value = getStringValue(table, attrName, rowIndex);
                if (value != null && !value.trim().isEmpty() && attr.indexOfValue(value) != -1) {
                    instance.setValue(attr, value);
                } else {
                    logger.warning(String.format("Valore nominale non valido per attributo '%s' alla riga %d. Assegnato Missing Value.", attrName, rowIndex));
                    instance.setMissing(attr);
                }
            } else if (attr.isString()) {
                instance.setValue(attr, getStringValue(table, attrName, rowIndex));
            }
        } catch (Exception e) {
            logger.warning(String.format("Errore nell'impostare il valore per '%s' alla riga %d: %s. Assegnando Missing Value.", attrName, rowIndex, e.getMessage()));
            instance.setMissing(attr);
        }
    }


    private Table filterTableByReleases(Table sourceTable, List<String> targetReleases) {
        StringColumn releaseCol = sourceTable.stringColumn(RELEASE);

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

            if (colName.equalsIgnoreCase(RELEASE) || colName.equalsIgnoreCase("MethodName") || colName.equalsIgnoreCase("ProjectName")) {
                continue;
            }

            if (colName.equalsIgnoreCase("bugginess")) {
                attributes.add(createClassAttribute(colName));
            } else if (col instanceof IntColumn || col instanceof DoubleColumn) {
                attributes.add(new Attribute(colName));
            } else if (col instanceof StringColumn) {
                attributes.add(createStringOrNominalAttribute(colName, (StringColumn) col));
            } else {
                logger.warning("Tipo di colonna Tablesaw non riconosciuto per Weka Attribute: " + colName + " (" + col.type() + "). Skippato.");
            }
        }

        moveClassAttributeToEnd(attributes);

        return attributes;
    }

    private Attribute createClassAttribute(String colName) {
        ArrayList<String> classValues = new ArrayList<>();
        classValues.add("yes");
        classValues.add("no");
        return new Attribute(colName, classValues);
    }

    private Attribute createStringOrNominalAttribute(String colName, StringColumn col) {
        Set<String> uniqueValues = new HashSet<>();
        for (int i = 0; i < col.size(); i++) {
            String value = col.getString(i);
            if (value != null && !value.trim().isEmpty()) {
                uniqueValues.add(value.trim());
            }
        }
        if (!uniqueValues.isEmpty() && uniqueValues.size() < 50) {
            ArrayList<String> nominalValues = new ArrayList<>(uniqueValues);
            Collections.sort(nominalValues);
            return new Attribute(colName, nominalValues);
        } else {
            logger.warning(String.format("Colonna '%s' ha troppi valori unici (%d) o è vuota. Trattata come attributo String.", colName, uniqueValues.size()));
            return new Attribute(colName, (ArrayList<String>) null);
        }
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
        if (column instanceof DoubleColumn doubleColumn) {
            return doubleColumn.getDouble(rowIndex);
        } else if (column instanceof IntColumn intColumn) {
            return intColumn.getInt(rowIndex);
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

    private Instances createEmptyWekaInstances(String datasetName, Table referenceTable)  {
        ArrayList<Attribute> attributes = createWekaAttributes(referenceTable);
        return new Instances(datasetName, attributes, 0);
    }
}