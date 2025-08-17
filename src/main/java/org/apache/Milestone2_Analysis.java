package org.apache;

import org.apache.logging.CollectLogger;

import java.util.logging.Logger;

public class Milestone2_Analysis {
   // private final static Logger logger = CollectLogger.getInstance().getLogger();

    public static void main(String[] args) throws Exception {
        // Imposta qui il nome del progetto su cui lavorare
        //String projectName = "BOOKKEEPER";
        /*String projectName="STORM";
        String csvFilePath = projectName + "_dataset.csv";

        // ==================================================================
        // FASE 1: PREPARAZIONE DEL DATASET "A" (Punto 1 del PDF)
        // ==================================================================
        System.out.println("--- FASE 1: Preparazione del Dataset 'A' ---");

        // Carica il dataset completo generato dalla Milestone 1
        Table fullDataset = Table.read().csv(csvFilePath);

        // Trova il punto di taglio: il 50% delle release
        IntColumn releaseColumn = fullDataset.intColumn("ReleaseID");
        Table uniqueReleases = releaseColumn.unique().sortAscending();
        int totalReleases = uniqueReleases.rowCount();
        int releasesToKeepCount = totalReleases / 2;
        int lastReleaseToKeep = (int) uniqueReleases.get(releasesToKeepCount - 1, 0);

        // Filtra il dataset per tenere solo le righe delle release selezionate
        Table A = fullDataset.where(releaseColumn.isLessThanOrEqualTo(lastReleaseToKeep));
        logger.info("Dataset 'A' creato con le prime " + releasesToKeepCount + " release. Righe totali: " + A.rowCount());

        // Da qui in poi, useremo SOLO il dataset 'A'

        // ==================================================================
        // FASE 2: SCELTA DEL MIGLIOR CLASSIFICATORE (Punti 2-3 del PDF)
        // ==================================================================
        System.out.println("\n--- FASE 2: Scelta del miglior classificatore (BClassifier) ---");
        //Classifier bClassifier = chooseBestClassifier(A);

        // ==================================================================
        // FASE 3: ANALISI DI CORRELAZIONE E REFACTORING (Punti 4-9 del PDF)
        // ==================================================================
        System.out.println("\n--- FASE 3: Analisi di Correlazione e Refactoring ---");
        // ... (vedi implementazioni dettagliate sotto)

        // ==================================================================
        // FASE 4: SIMULAZIONE "WHAT-IF" (Punti 10-13 del PDF)
        // ==================================================================
        System.out.println("\n--- FASE 4: Simulazione What-If ---");
        // ... (vedi implementazioni dettagliate sotto)*/
    }

    // Qui sotto inseriremo i metodi helper per ogni fase
}
