package org.apache.controller;
import org.apache.logging.CollectLogger;
import tech.tablesaw.api.IntColumn;
import tech.tablesaw.api.Table;

import java.io.File;
import java.util.List;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.Collections;

import tech.tablesaw.columns.Column;
import weka.core.Attribute;
import weka.core.Instances;
import weka.core.DenseInstance;
import weka.core.converters.ArffSaver;

public class DatasetController {

    private final String csvFilePath;
    private static final Logger logger = CollectLogger.getInstance().getLogger(); // Usa il tuo logger specifico

    public DatasetController(String projectName) {
        this.csvFilePath = projectName + "_dataset.csv";
    }

    // MODIFICATO: Ora accetta la percentuale di taglio
    public Table prepareDatasetA(double cutPercentage) {
        File inputFile = new File(csvFilePath);
        if (!inputFile.exists()) {
            logger.severe("ERRORE: File dataset non trovato: " + csvFilePath);
            return null;
        }

        Table fullDataset = Table.read().csv(csvFilePath);

        if (fullDataset.isEmpty()) {
            logger.warning("ATTENZIONE: Il file dataset è vuoto: " + csvFilePath);
            // Non restituire Table.create("Empty") se vuoi un Table vuoto coerente con il tuo metodo
            return fullDataset; // Restituisce un Table vuoto, ma non null
        }

        if (!fullDataset.columnNames().contains("ReleaseID")) {
            logger.severe("ERRORE: La colonna 'ReleaseID' non è presente nel file CSV.");
            return null;
        }

        // Recupera le release ID uniche e le ordina
        IntColumn releaseColumn = fullDataset.intColumn("ReleaseID");
        IntColumn uniqueReleasesColumn = releaseColumn.unique();
        uniqueReleasesColumn.sortAscending();

        int totalReleases = uniqueReleasesColumn.size();
        if (totalReleases == 0) {
            logger.warning("ATTENZIONE: Nessuna release valida trovata nel dataset.");
            return Table.create("EmptyDatasetA"); // Crea un Table vuoto
        }

        // --- CALCOLO MODIFICATO PER IL TAGLIO PERCENTUALE (34% INIZIALE) ---
        int releasesToKeepCount = (int) Math.round(totalReleases * cutPercentage);

        // Assicurati che ci sia almeno una release da tenere se il dataset non è vuoto
        if (releasesToKeepCount == 0 && totalReleases > 0) {
            releasesToKeepCount = 1;
            logger.warning(String.format("Il calcolo della percentuale (%.2f%%) ha portato a 0 release da mantenere. Mantenuta almeno la prima release.", cutPercentage * 100));
        } else if (releasesToKeepCount > totalReleases) {
            // Se per qualche motivo il calcolo supera il totale, usa il totale
            releasesToKeepCount = totalReleases;
        }

        // Se non ci sono release da tenere dopo l'aggiustamento (es. totalReleases era 0 all'inizio)

        Integer lastReleaseToKeep = uniqueReleasesColumn.get(releasesToKeepCount - 1);
        // -------------------------------------------------------------------

        logger.info(String.format("Filtraggio del dataset: mantenute le prime %d release (corrispondenti a %.2f%% del totale) su %d, fino alla release ID: %d",
                releasesToKeepCount, cutPercentage * 100, totalReleases, lastReleaseToKeep));

        // Applica il filtro al dataset completo per ottenere il Dataset A
        return fullDataset.where(releaseColumn.isLessThanOrEqualTo(lastReleaseToKeep));
    }

    // Il metodo generateWalkForwardArffFiles deve ancora essere implementato completamente
    // Questo è lo scheletro che avevo fornito, ora devi completare la parte di conversione da Tablesaw.Table a Weka.Instances
    public void generateWalkForwardArffFiles(Table datasetA, String projectName, int walkForwardIterations) throws Exception {
        logger.info("Inizio generazione file ARFF per Walk-Forward per il progetto: " + projectName);

        final String arffExtension = ".arff";
        final String outputBaseDir = "output" + File.separator + "dataset" + File.separator + projectName + File.separator + "ARFF";
        final String trainingBaseDir = outputBaseDir + File.separator + "TRAINING";
        final String testingBaseDir = outputBaseDir + File.separator + "TESTING";

        new File(trainingBaseDir);
        new File(testingBaseDir);

        // Questa parte richiede di convertire da Tablesaw.IntColumn a List<String> o List<Integer> per l'ordinamento
        // Assicurati che le release siano ordinate cronologicamente
        List<Integer> allReleasesFromDatasetAInt = datasetA.intColumn("ReleaseID").unique().asList();
        Collections.sort(allReleasesFromDatasetAInt); // Ordina le release numericamente

        List<String> allReleasesFromDatasetA = new ArrayList<>();
        for (Integer releaseNum : allReleasesFromDatasetAInt) {
            allReleasesFromDatasetA.add(String.valueOf(releaseNum)); // Converti a stringa per consistenza
        }

        if (allReleasesFromDatasetA.size() < 2) {
            logger.severe("Dataset A ha meno di 2 release. Impossibile eseguire Walk-Forward.");
            return;
        }

        int actualIterations = Math.min(walkForwardIterations, allReleasesFromDatasetA.size() - 1);

        if (actualIterations < walkForwardIterations) {
            logger.warning("Il numero di iterazioni di Walk-Forward richiesto (" + walkForwardIterations + ") è maggiore del numero massimo possibile con le release disponibili (" + actualIterations + "). Verranno eseguite " + actualIterations + " iterazioni.");
        }

        for (int i = 0; i < actualIterations; i++) {
            List<String> trainingReleases = allReleasesFromDatasetA.subList(0, i + 1);
            String testingRelease = allReleasesFromDatasetA.get(i + 1);

            logger.info(String.format("Iterazione %d: Training su release %s, Testing su release %s",
                    (i + 1), trainingReleases, testingRelease));

            // ***** QUI DEVI CONVERTIRE TABLESLAW.TABLE A WEKA.INSTANCES *****
            // Questa è la parte più complessa e dipenderà dalla tua specifica struttura delle colonne.
            // Il codice precedente in DatasetController (la classe Table fittizia) ti dava un'idea.
            // Dovrai iterare sulle righe della Tablesaw.Table, estrarre i valori
            // e costruire gli oggetti Weka Attribute e Instance.

            Instances trainingInstances = convertTablesawToWekaInstances(datasetA, trainingReleases, projectName + "_Training_" + (i+1));
            Instances testingInstances = convertTablesawToWekaInstances(datasetA, List.of(testingRelease), projectName + "_Testing_" + (i+1));

            // Imposta l'attributo classe nell'ultimo indice
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

            ArffSaver saver = new ArffSaver();
            saver.setInstances(trainingInstances);
            saver.setFile(new File(trainingFilePath));
            saver.writeBatch();
            logger.info("Salvato training set: " + trainingFilePath);

            saver.setInstances(testingInstances);
            saver.setFile(new File(testingFilePath));
            saver.writeBatch();
            logger.info("Salvato testing set: " + testingFilePath);
        }
        logger.info("Generazione file ARFF completata.");
    }

    // NUOVO METODO DA IMPLEMENTARE: CONVERSIONE DA TABLESLAW A WEKA INSTANCES
    private Instances convertTablesawToWekaInstances(Table sourceTable, List<String> targetReleases, String datasetName) throws Exception {
        // Filtra la tabella Tablesaw in base alle release target
        Table filteredTablesawTable;
        if (targetReleases.size() == 1) {
            filteredTablesawTable = sourceTable.where(sourceTable.intColumn("ReleaseID").isEqualTo(Integer.parseInt(targetReleases.getFirst())));
        } else {
            // Per più release, usa una condizione OR o un set di appartenenza
            tech.tablesaw.selection.Selection selection = null;
            for (String release : targetReleases) {
                if (selection == null) {
                    selection = sourceTable.intColumn("ReleaseID").isEqualTo(Integer.parseInt(release));
                } else {
                    selection = selection.or(sourceTable.intColumn("ReleaseID").isEqualTo(Integer.parseInt(release)));
                }
            }
            if (selection != null) {
                filteredTablesawTable = sourceTable.where(selection);
            } else {
                filteredTablesawTable = Table.create("EmptyFilteredTable");
            }
        }

        // Se la tabella filtrata è vuota, crea un'istanza Weka vuota ma con gli attributi corretti
        if (filteredTablesawTable.isEmpty()) {
            logger.warning("Filtered Tablesaw table is empty for releases: " + targetReleases + ". Creating empty Weka Instances.");
            return createEmptyWekaInstances(datasetName, sourceTable);
        }

        ArrayList<Attribute> attributes = new ArrayList<>();
        // Itera sulle colonne di Tablesaw per creare gli attributi Weka
        for (Column<?> col : filteredTablesawTable.columns()) {
            String colName = col.name();
            // Ignora colonne non necessarie o che non sono feature (es. "MethodName" se non la usi come feature)
            // Assumi che "bugginess" sia l'attributo classe e che "ReleaseID" non sia una feature predittiva diretta
            if (colName.equals("ReleaseID") || colName.equals("MethodName")) {
                continue;
            }

            if (colName.equalsIgnoreCase("bugginess")) {
                // L'attributo classe deve essere nominale ("yes", "no")
                ArrayList<String> classValues = new ArrayList<>();
                classValues.add("yes");
                classValues.add("no");
                attributes.add(new Attribute(colName, classValues));
            } else if (col.type().isNumeric()) {
                attributes.add(new Attribute(colName)); // Numerico
            } else {
                // Per attributi stringa/categorici, devi definire i valori nominali
                // Questo è un esempio, potresti aver bisogno di una gestione più robusta
                ArrayList<String> nominalValues = new ArrayList<>(col.unique().asStringColumn().asList());
                attributes.add(new Attribute(colName, nominalValues));
            }
        }

        // Assicurati che l'attributo classe sia l'ultimo, come richiesto da Weka
        // Questo è fondamentale! Se non lo è, Weka si aspetterà qualcos'altro.
        // Cerca l'attributo "bugginess" e spostalo alla fine.
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
            logger.severe("Errore: Attributo classe 'bugginess' non trovato tra le colonne del dataset!");
            throw new IllegalArgumentException("Attributo classe 'bugginess' non trovato.");
        }


        Instances wekaInstances = new Instances(datasetName, attributes, 0);

        // Aggiungi le righe (istanze) alla tabella Weka
        for (int i = 0; i < filteredTablesawTable.rowCount(); i++) {
            DenseInstance instance = new DenseInstance(attributes.size());
            for (Attribute attr : attributes) {
                String attrName = attr.name();

                // Estrai il valore dalla riga di Tablesaw
                // Assicurati che i tipi corrispondano
                if (attr.isNumeric()) {
                    instance.setValue(attr, filteredTablesawTable.doubleColumn(attrName).get(i));
                } else if (attr.isNominal()) {
                    instance.setValue(attr, filteredTablesawTable.stringColumn(attrName).get(i));
                }
                // ... gestisci altri tipi se necessario (es. date)
            }
            wekaInstances.add(instance);
        }

        return wekaInstances;
    }

    // Helper method to create empty Weka Instances with correct attributes
    private Instances createEmptyWekaInstances(String datasetName, Table referenceTable) throws Exception {
        ArrayList<Attribute> attributes = new ArrayList<>();
        for (Column<?> col : referenceTable.columns()) {
            String colName = col.name();
            if (colName.equals("ReleaseID") || colName.equals("MethodName")) {
                continue;
            }
            if (colName.equalsIgnoreCase("bugginess")) {
                ArrayList<String> classValues = new ArrayList<>();
                classValues.add("yes");
                classValues.add("no");
                attributes.add(new Attribute(colName, classValues));
            } else if (col.type().isNumeric()) {
                attributes.add(new Attribute(colName));
            } else {
                ArrayList<String> nominalValues = new ArrayList<>(col.unique().asStringColumn().asList());
                attributes.add(new Attribute(colName, nominalValues));
            }
        }

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
            throw new IllegalArgumentException("Attributo classe 'bugginess' non trovato per la creazione di istanze Weka vuote.");
        }

        return new Instances(datasetName, attributes, 0);
    }
}