package org.apache.controller;
import org.apache.logging.CollectLogger;
import tech.tablesaw.api.IntColumn;
import tech.tablesaw.api.Table;

import java.io.File;
import java.util.logging.Logger;

public class DatasetController {

    private final String csvFilePath;
    private static final Logger logger = CollectLogger.getInstance().getLogger();

    public DatasetController(String projectName) {
        this.csvFilePath = projectName + "_dataset.csv";
    }

    public Table prepareDatasetA() {
        File inputFile = new File(csvFilePath);
        if (!inputFile.exists()) {
            logger.severe("ERRORE: File dataset non trovato: " + csvFilePath);
            return null;
        }

        Table fullDataset = Table.read().csv(csvFilePath);

        if (fullDataset.isEmpty()) {
            logger.warning("ATTENZIONE: Il file dataset è vuoto: " + csvFilePath);
            return fullDataset;
        }

        if (!fullDataset.columnNames().contains("ReleaseID")) {
            logger.severe("ERRORE: La colonna 'ReleaseID' non è presente nel file CSV.");
            return null;
        }

        // --- BLOCCO DI CODICE CORRETTO ---
        IntColumn releaseColumn = fullDataset.intColumn("ReleaseID");
        IntColumn uniqueReleasesColumn = releaseColumn.unique();
        uniqueReleasesColumn.sortAscending(); // Ordina la colonna

        int totalReleases = uniqueReleasesColumn.size(); // Usa .size() per le colonne
        if (totalReleases == 0) {
            logger.warning("ATTENZIONE: Nessuna release valida trovata nel dataset.");
            return Table.create("Empty");
        }

        int releasesToKeepCount = (totalReleases + 1) / 2;
        Integer lastReleaseToKeep = uniqueReleasesColumn.get(releasesToKeepCount - 1); // Usa .get() sulla colonna
        // ---------------------------------

        logger.info("Filtraggio del dataset per tenere le prime " + releasesToKeepCount + " release (fino alla release ID: " + lastReleaseToKeep + ")");
        return fullDataset.where(releaseColumn.isLessThanOrEqualTo(lastReleaseToKeep));
    }
}