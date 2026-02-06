package org.apache.controller.milestone2;

import org.apache.logging.Printer;
import tech.tablesaw.api.IntColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.api.StringColumn;

import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.HashSet;

import tech.tablesaw.columns.Column;
import tech.tablesaw.selection.Selection;
import weka.core.Attribute;
import weka.core.Instances;
import weka.core.DenseInstance;

public class DatasetController {

    private final String csvFilePath;
    public  static final  String RELEASE = "Release";


    public DatasetController(String projectName) {
        this.csvFilePath = projectName + "_dataset.csv";
    }

    public Table prepareDatasetA(double cutPercentage) {
        File inputFile = new File(csvFilePath);
        if (!inputFile.exists()) {
            Printer.errorPrint("ERRORE: File dataset non trovato: " + csvFilePath);
            return null;
        }

        Table fullDataset;
        try {
            fullDataset = Table.read().csv(csvFilePath);
        } catch (Exception e) {
            Printer.errorPrint("ERRORE: Impossibile leggere il file CSV: " + csvFilePath + " - " + e.getMessage());
            return null;
        }

        if (fullDataset.isEmpty()) {
            Printer.printYellow("ATTENZIONE: Il file dataset è vuoto: " + csvFilePath);
            return fullDataset;
        }

        if (!fullDataset.columnNames().contains(RELEASE)) {
            Printer.errorPrint("ERRORE: La colonna 'Release' non è presente nel file CSV.");
            return null;
        }

        // Recupera le release ID uniche e le ordina
        StringColumn releaseColumn = fullDataset.stringColumn(RELEASE);
        List<String> uniqueReleases = new ArrayList<>(new HashSet<>(releaseColumn.asList()));
        Collections.sort(uniqueReleases);

        int totalReleases = uniqueReleases.size();
        if (totalReleases == 0) {
            Printer.printYellow("ATTENZIONE: Nessuna release valida trovata nel dataset.");
            return Table.create("EmptyDatasetA");
        }

        // Calcolo del taglio percentuale
        int releasesToKeepCount = (int) Math.round(totalReleases * cutPercentage);

        if (releasesToKeepCount == 0) {
            releasesToKeepCount = 1;
            Printer.printYellow("Il calcolo della percentuale (%.2f%%) ha portato a 0 release da mantenere. Mantenuta almeno la prima release."+cutPercentage * 100);
        } else if (releasesToKeepCount > totalReleases) {
            releasesToKeepCount = totalReleases;
        }

        List<String> releasesForDatasetA = uniqueReleases.subList(0, releasesToKeepCount);

        // 2. Logga le release che verranno mantenute
        Printer.println(String.format("Filtraggio del dataset: mantenute le prime %d release (corrispondenti a %.2f%% del totale) su %d. Release mantenute per Dataset A: %s",
                releasesToKeepCount, cutPercentage * 100, totalReleases, releasesForDatasetA));

        // 3. Filtra il fullDataset usando la lista delle releaseForDatasetA
        // Qui chiami il metodo helper che filtra per una lista di release
        return filterTableByReleases(fullDataset, releasesForDatasetA);
    }


    public Instances convertTablesawToWekaInstances(Table sourceTable, List<String> targetReleases, String datasetName) {
        Table filteredTable = filterTableByReleases(sourceTable, targetReleases);

        if (filteredTable.isEmpty()) {
            Printer.printYellow("Filtered Tablesaw table is empty for releases: " + targetReleases + ". Creating empty Weka Instances.");
            // Passa la sourceTable completa per creare gli attributi, anche se la filteredTable è vuota
            return createEmptyWekaInstances(datasetName, sourceTable);
        }

        ArrayList<Attribute> attributes = createWekaAttributes(filteredTable);
        Instances wekaInstances = new Instances(datasetName, attributes, 0);

        for (int i = 0; i < filteredTable.rowCount(); i++) {
            DenseInstance instance = new DenseInstance(attributes.size());

            for (Attribute attr : attributes) {
                String attrName = attr.name();

                // Assicurati che la colonna esista nella tabella filtrata
                if (!filteredTable.columnNames().contains(attrName)) {
                    Printer.printYellow("Colonna Tablesaw '" + attrName + "' non trovata nella tabella filtrata. Skippato l'impostazione del valore per questa istanza.");
                    continue; // Passa al prossimo attributo
                }

                // Delegazione alla nuova routine che imposta il valore (gestisce anche gli errori)
                setAttributeValue(instance, attr, filteredTable, i);
            }
            wekaInstances.add(instance);
        }

        return wekaInstances;
    }


    private void setAttributeValue(DenseInstance instance, Attribute attr, Table filteredTable, int rowIndex) {
        String attrName = attr.name();
        try {
            if (attr.isNumeric()) {
                double value = getNumericValue(filteredTable, attrName, rowIndex);
                instance.setValue(attr, value);
            } else if (attr.isNominal()) {
                String value = getStringValue(filteredTable, attrName, rowIndex);
                if (value != null && !value.trim().isEmpty()) {
                    if (attr.indexOfValue(value) != -1) { // Verifica che il valore sia tra i nominali definiti
                        instance.setValue(attr, value);
                    } else {
                        Printer.printYellow(String.format("Valore nominale '%s' non riconosciuto per attributo '%s'. Alla riga %d. Assegnato Missing Value.", value, attrName, rowIndex));
                        instance.setMissing(attr); // Imposta come valore mancante di Weka
                    }
                } else {
                    Printer.printYellow(String.format("Valore nullo/vuoto per attributo nominale '%s' alla riga %d. Assegnato Missing Value.", attrName, rowIndex));
                    instance.setMissing(attr); // Imposta come valore mancante di Weka
                }
            } else if (attr.isString()) { // Gestione esplicita di attributi di tipo String (testo libero)
                String value = getStringValue(filteredTable, attrName, rowIndex);
                instance.setValue(attr, value);
            }
        } catch (Exception e) {
            String sourceVal;
            try {
                sourceVal = filteredTable.column(attrName).getString(rowIndex);
            } catch (Exception ex) {
                sourceVal = "N/A";
            }
            Printer.printYellow(String.format("Errore imprevisto nell'impostare il valore per l'attributo '%s' (tipo Weka: %d) alla riga %d: %s. Valore sorgente: '%s'. Assegnando Missing Value/Default.",
                    attrName, attr.type(), rowIndex, e.getMessage(), sourceVal));
            instance.setMissing(attr); // In caso di errore inaspettato, imposta missing
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

            // Ignora colonne non necessarie per la classificazione
            if (colName.equalsIgnoreCase(RELEASE) || colName.equalsIgnoreCase("MethodName") || colName.equalsIgnoreCase("ProjectName")) {
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
                attributes.add(createAttributeForStringColumn(col));
            } else {
                Printer.printYellow("Tipo di colonna Tablesaw non riconosciuto per Weka Attribute: " + colName + " (" + col.type() + "). Skippato.");
            }
        }

        // Sposta l'attributo classe alla fine
        moveClassAttributeToEnd(attributes);

        return attributes;
    }

    private Attribute createAttributeForStringColumn(Column<?> col) {
        String colName = col.name();
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
            return new Attribute(colName, nominalValues);
        } else {
            // Troppi valori unici o nessun valore, trattato come stringa (testo libero)
            Printer.printYellow(String.format("Colonna '%s' ha troppi valori unici (%d) o è vuota. Trattata come attributo String.", colName, uniqueValues.size()));
            return new Attribute(colName, (ArrayList<String>) null); // Tipo String Weka
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
            Printer.errorPrint("Errore: Attributo classe 'bugginess' non trovato tra le colonne del dataset! Assicurati che esista e sia nominato 'bugginess'.");
            throw new IllegalArgumentException("Attributo classe 'bugginess' non trovato.");
        }
    }

    private double getNumericValue(Table table, String columnName, int rowIndex) {
        Column<?> column = table.column(columnName);

        return switch (column) {
            case DoubleColumn doubleColumn -> doubleColumn.getDouble(rowIndex);
            case IntColumn intColumn -> intColumn.getInt(rowIndex);
            default -> {
                String stringValue = column.getString(rowIndex);
                double value;
                try {
                    value = Double.parseDouble(stringValue);
                } catch (NumberFormatException e) {
                    Printer.printYellow("Impossibile convertire '" + stringValue + "' in numero per la colonna "
                            + columnName + " alla riga " + rowIndex + ". Restituisco 0.0.");
                    value = 0.0;
                }
                yield value;
            }
        };
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