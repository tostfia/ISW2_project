package org.apache.controller;
import org.apache.logging.CollectLogger;
import org.apache.model.AggregatedClassifierResult;

import org.apache.model.ClassifierResult;
import org.apache.model.PredictionResult;

import tech.tablesaw.api.Table;

import weka.classifiers.Classifier;

import weka.core.Instance;
import weka.core.Instances;
import weka.core.SerializationHelper;
import weka.core.converters.CSVSaver;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;



public class WhatIfAnalyzer {
    private final AggregatedClassifierResult bClassifier; // Il miglior classificatore selezionato (BClassifierA)
    private final Table datasetA;
    private final Instances wekaDatasetA; // Il dataset 'A' in formato Weka
    private final String projectName;
    private final CorrelationController cc;
    private final Logger logger;
    private Classifier loadedWekaClassifier;// Il modello Weka caricato
    private List<ClassifierResult> classifierResults;

    // Costruttore: riceve il miglior classificatore, il dataset 'A' e il nome del progetto
    public WhatIfAnalyzer(AggregatedClassifierResult bClassifier, Table datasetA, Instances wekaDatasetA, String projectName) {
        this.bClassifier = bClassifier;
        this.datasetA = datasetA;
        this.wekaDatasetA = wekaDatasetA;
        this.projectName = projectName;
        this.cc = new CorrelationController(datasetA);
        this.logger = CollectLogger.getInstance().getLogger();
    }

    public void run() throws Exception {
        logger.info("Avvio dell'analisi 'What-If'...");

        //PASSO 1: Identificazione di AFeature (Punti 4 e 5 della Slide 2)
        String aFeature = identifyAFeature();
        String methodName= cc.findBuggyMethodWithMaxFeature(aFeature);

        logger.info("Feature Azionabile (AFeature) identificata: " + aFeature);
        logger.info("Metodo con il valore massimo di " + aFeature + " tra i metodi buggy: " + methodName + "Fare il refactor");

        // Carica il modello del classificatore BClassifierA
        // Il BClassifier dovrebbe contenere il percorso al file del modello Weka salvato (.model)
        String modelFilePath = bClassifier.getModelFilePath();
        if (modelFilePath == null || modelFilePath.isEmpty()) {
            logger.severe("Percorso del modello del classificatore non trovato. Impossibile procedere con le predizioni.");
            return;
        }
        loadClassifierModel(modelFilePath);
        if (loadedWekaClassifier == null) {
            logger.severe("Impossibile caricare il modello del classificatore. Analisi interrotta.");
            return;
        }

        // PASSO 2: Creazione dei dataset B+, C, B (Punto 10 della Slide 4)
        logger.info("Creazione dei dataset B+, C e B...");
        Instances bPlusDataset = createBPlusDataset(aFeature);
        Instances cDataset = createCDataset(aFeature);
        Instances bDataset = createBDataset(bPlusDataset, aFeature); // Manipola B+ per creare B

        // Se uno dei dataset cruciali è vuoto, potrebbe essere un problema per le predizioni
        if (bPlusDataset.isEmpty()) {
            logger.warning("Il dataset B+ (metodi con smells e bug) è vuoto. Le analisi future potrebbero essere limitate.");
        }
        if (cDataset.isEmpty()) {
            logger.warning("Il dataset C (metodi senza smells) è vuoto.");
        }
        if (bDataset.isEmpty()) {
            logger.warning("Il dataset B (B+ con smells azzerati) è vuoto.");
        }

       saveDatasetAsCSV(bPlusDataset, cDataset,bDataset);




        // PASSO 3: Predizioni su A, B, B+, C (Punto 12 della Slide 4)
        logger.info("Esecuzione delle predizioni...");
        Map<String, PredictionResult> results = new HashMap<>();

        /*results.put("A", predict(wekaDatasetA, "A"));
        results.put("B+", predict(bPlusDataset, "B+"));
        results.put("C", predict(cDataset, "C"));
        results.put("B", predict(bDataset, "B"));

        // PASSO 4: Analisi e Risposta (Punto 13 della Slide 4)
        analyzeResults(results, aFeature);

        // PASSO 5: Rispondere alle Domande Preliminari (Slide 3)
        // Questa parte richiede un confronto più dettagliato tra AFMethod e AFMethod2
        // Che sono rappresentati concettualmente dal confronto B+ vs B.
        answerPreliminaryQuestions(results, aFeature);*/

        logger.info("Analisi 'What-If' completata.");
    }

    //Punto 4 & 5 (Slide 2): Calcola la correlazione e identifica la AFeature.
        private String identifyAFeature() {

            CorrelationController.FeatureCorrelation best = cc.getBestFeature();
            String aFeature = best.featureName();
            logger.info("Feature azionabile (AFeature): " + aFeature + ", correlazione: " + best.correlation());
            return aFeature;

        }



        // Carica il modello del classificatore Weka dal file
        private void loadClassifierModel(String modelPath) {
            try {
                loadedWekaClassifier = (Classifier) SerializationHelper.read(modelPath);
                logger.info("Modello del classificatore caricato con successo da: " + modelPath);
            } catch (Exception e) {
                logger.severe("Errore durante il caricamento del modello del classificatore da " + modelPath + ": " + e.getMessage());
                loadedWekaClassifier = null; // Assicurati di impostare a null in caso di errore
            }
        }




    /**
     * Crea il dataset B+ = istanze con aFeature > 0 e bugginess = yes
     */
    private Instances createBPlusDataset(String aFeature) {
        Instances bPlus = new Instances(wekaDatasetA, 0); // copia struttura vuota
        int aFeatureIndex = wekaDatasetA.attribute(aFeature).index();
        int bugIndex = wekaDatasetA.classIndex(); // assumiamo che "bugginess" sia la classe nominale yes/no

        for (int i = 0; i < wekaDatasetA.numInstances(); i++) {
            Instance inst = wekaDatasetA.instance(i);
            double aVal = inst.value(aFeatureIndex);
            String bugValue = inst.stringValue(bugIndex);

            if (aVal > 0 && bugValue.equals("yes")) {
                bPlus.add((Instance) inst.copy());
            }
        }

        return bPlus;
    }

    /**
     * Crea il dataset C = istanze con aFeature = 0
     */
    private Instances createCDataset(String aFeature) {
        Instances cDataset = new Instances(wekaDatasetA, 0);
        int aFeatureIndex = wekaDatasetA.attribute(aFeature).index();

        for (int i = 0; i < wekaDatasetA.numInstances(); i++) {
            Instance inst = wekaDatasetA.instance(i);
            if (inst.value(aFeatureIndex) == 0) {
                cDataset.add((Instance) inst.copy());
            }
        }

        return cDataset;
    }

    /**
     * Crea il dataset B = copia di B+ ma con aFeature settata a 0
     */
    private Instances createBDataset(Instances bPlus, String aFeature) {
        Instances bDataset = new Instances(bPlus);
        int aFeatureIndex = bDataset.attribute(aFeature).index();

        for (int i = 0; i < bDataset.numInstances(); i++) {
            Instance inst = bDataset.instance(i);
            inst.setValue(aFeatureIndex, 0); // azzera la feature smell
        }

        return bDataset;
    }




    /**
     * Punto 12 (Slide 4): Esegue le predizioni sui dataset e raccoglie i risultati.
     */
    /*private PredictionResult predict(Table dataToPredict, String datasetName) throws Exception {
        if (dataToPredict == null || dataToPredict.isEmpty()) {
            logger.warning("Dataset '" + datasetName + "' è vuoto. Impossibile effettuare predizioni.");
            return new PredictionResult(0, 0, 0); // Ritorna un risultato vuoto
        }

        int actualBuggy = 0;
        int predictedBuggy = 0;
        int correctlyPredictedBuggy = 0;

        // Assicurati che la colonna 'bug' esista e sia booleana
        if (!dataToPredict.containsColumn("bug") || !(dataToPredict.column("bug") instanceof BooleanColumn)) {
            logger.severe("La colonna 'bug' non esiste o non è booleana nel dataset " + datasetName);
            return new PredictionResult(0, 0, 0);
        }

        BooleanColumn bugColumn = dataToPredict.booleanColumn("bug");
        for (int i = 0; i < dataToPredict.rowCount(); i++) {
            boolean actualClassValue = bugColumn.get(i);

            if (actualClassValue) { // Se il metodo è effettivamente buggy
                actualBuggy++;
            }

            // Converti la riga di TableSaw in un'istanza Weka
            DenseInstance wekaInstance = createWekaInstance(dataToPredict.row(i), dataToPredict.name());

            double predictedClassValue = loadedWekaClassifier.classifyInstance(wekaInstance);

            if (predictedClassValue == 1.0) { // Se il classificatore predice che è buggy
                predictedBuggy++;
                if (actualClassValue) { // Se la predizione è corretta
                    correctlyPredictedBuggy++;
                }
            }
        }

        logger.info("Predizioni per " + datasetName + ": Totale istanze = " + dataToPredict.rowCount() +
                ", Actual Buggy = " + actualBuggy +
                ", Predicted Buggy = " + predictedBuggy +
                ", Correctly Predicted Buggy = " + correctlyPredictedBuggy);

        return new PredictionResult(actualBuggy, predictedBuggy, correctlyPredictedBuggy);
    }

    private DenseInstance createWekaInstance(Row row, String tableName) {
        // Converte una riga di TableSaw in un'istanza Weka
        // Assicurati che l'ordine degli attributi in Weka corrisponda alle colonne di TableSaw
        int numAttributes = datasetA.columnCount();
        DenseInstance wekaInstance = new DenseInstance(numAttributes);
        //wekaInstance.setDataset(datasetA);

        for (int i = 0; i < numAttributes; i++) {
            Column<?> column = datasetA.column(i);
            if (column instanceof NumberColumn) {
                wekaInstance.setValue(i, ((NumberColumn) column).getDouble(row.getRowNumber()));
            } else if (column instanceof NominalColumn) {
                wekaInstance.setValue(i, ((NominalColumn) column).getIndex(row.getString(column.name())));
            } else if (column instanceof BooleanColumn) {
                wekaInstance.setValue(i, ((BooleanColumn) column).getInt(row.getRowNumber())); //1 o 0
            } else {
                // Gestisci altri tipi di colonne se necessario
                System.err.println("Tipo di colonna non supportato: " + column.type());
            }
        }
        return wekaInstance;
    }

    /**
     * Punto 13 (Slide 4): Analizza la tabella dei risultati e risponde alla domanda chiave.
     */
    /**
     * Punto 13 (Slide 4): Analizza la tabella dei risultati e risponde alla domanda chiave.
     */
    /*private void analyzeResults(Map<String, PredictionResult> results, String aFeature) {
        logger.info("\n--- RISULTATI DELLE PREDIZIONI E ANALISI WHAT-IF ---");
        logger.info(String.format("%-10s %-15s %-15s %-25s", "Dataset", "Actual Buggy", "Predicted Buggy", "Correctly Predicted Buggy"));
        logger.info("-------------------------------------------------------------------------");

        results.forEach((name, res) ->
                logger.info(String.format("%-10s %-15d %-15d %-25d", name, res.getActualBuggy(), res.getPredictedBuggy(), res.getCorrectlyPredictedBuggy()))
        );

        // Risposta alla domanda: Quanti metodi difettosi avrebbero potuto essere prevenuti avendo zero smells?
        // Confrontiamo i metodi predetti come buggy in B+ (con smells) vs B (con smells azzerati).
        PredictionResult bPlusRes = results.get("B+");
        PredictionResult bRes = results.get("B");

        if (bPlusRes != null && bRes != null) {
            int preventableBuggyMethods = bPlusRes.getPredictedBuggy() - bRes.getPredictedBuggy();
            if (preventableBuggyMethods < 0) preventableBuggyMethods = 0; // Non può essere negativo

            logger.info("\n--- ANALISI FINALE: Impatto di ZERO SMELLS ---");
            logger.info("Numero di metodi con smells predetti come buggy (B+): " + bPlusRes.getPredictedBuggy());
            logger.info("Numero di metodi (ex B+ con smells azzerati) predetti come buggy (B): " + bRes.getPredictedBuggy());
            logger.info("RISPOSTA: Circa " + preventableBuggyMethods +
                    " metodi difettosi avrebbero potuto essere prevenuti avendo zero " + aFeature + " (basato sulle predizioni).");

            // In proporzione (Punto 13.11)
            double proportion = 0;
            if (bPlusRes.getPredictedBuggy() > 0) {
                proportion = (double) preventableBuggyMethods / bPlusRes.getPredictedBuggy() * 100;
            }
            logger.info(String.format("In proporzione: %.2f%% dei metodi buggy con smells avrebbero potuto essere prevenuti.", proportion));

            // Out of the preventable ones (Punto 13.12) - Questa è una metrica più complessa.
            // Potrebbe riferirsi a quanti dei *realmente* prevenibili sono stati identificati correttamente.
            // Per una risposta diretta al punto, potremmo dire:
            logger.info("Sui metodi che il classificatore ha predetto non più buggy dopo aver azzerato gli smells, il numero è " + preventableBuggyMethods + ".");
        } else {
            logger.warning("Risultati per B+ o B non disponibili, impossibile completare l'analisi finale.");
        }
    }
    /**
     * Risponde alle domande preliminari (Slide 3).
     * Queste domande sono più qualitative e basate sull'osservazione delle feature.
     * Nel contesto di questa simulazione, si basano sul confronto dei risultati di predizione.
     */
    /**
     * Risponde alle domande preliminari (Slide 3).
     * Queste domande sono più qualitative e basate sull'osservazione delle feature.
     * Nel contesto di questa simulazione, si basano sul confronto dei risultati di predizione.
     */
    /*private void answerPreliminaryQuestions(Map<String, PredictionResult> results, String aFeature) {
        logger.info("\n--- DOMANDE PRELIMINARI (SLIDE 3) ---");

        PredictionResult bPlusRes = results.get("B+");
        PredictionResult bRes = results.get("B");

        if (bPlusRes != null && bRes != null) {
            // "Did any feature positively correlate with bugginess increased in AFMethod2 (vs. AFMethod)?"
            // Questo si traduce in: il fatto di aver azzerato NSmells ha peggiorato le predizioni di bug?
            // Se le predizioni di bug in B (NSmells=0) sono *maggiori* o uguali a B+ (NSmells > 0), allora forse non abbiamo migliorato.
            if (bRes.getPredictedBuggy() >= bPlusRes.getPredictedBuggy()) {
                logger.info("• Il numero di metodi predetti come buggy in 'B' (NSmells=0) non è diminuito rispetto a 'B+' (NSmells>0).");
                logger.info("  -> Se la predizione di bug è aumentata o rimasta invariata, forse la manutenibilità non è migliorata, o altre feature correlate hanno un impatto.");
            } else {
                logger.info("• Il numero di metodi predetti come buggy in 'B' (NSmells=0) è diminuito rispetto a 'B+' (NSmells>0).");
                logger.info("  -> Questo suggerisce un miglioramento.");
            }

            // "Did any feature negatively correlate with bugginess increased in AFMethod2 (vs. AFMethod)?"
            // Questo si traduce in: il fatto di aver azzerato NSmells ha ridotto le predizioni di bug?
            // Se le predizioni di bug in B sono *inferiori* a B+, allora c'è una correlazione negativa (più smells, più bug).
            if (bRes.getPredictedBuggy() < bPlusRes.getPredictedBuggy()) {
                logger.info("• Il numero di metodi predetti come buggy in 'B' (NSmells=0) è significativamente inferiore rispetto a 'B+' (NSmells>0).");
                logger.info("  -> Ciò indica che azzerare '" + aFeature + "' è correlato negativamente alla bugginess, e suggerisce un miglioramento della manutenibilità.");
            } else {
                logger.info("• Il numero di metodi predetti come buggy in 'B' (NSmells=0) non è diminuito rispetto a 'B+' (NSmells>0).");
            }
        } else {
            logger.warning("Impossibile rispondere alle domande preliminari senza i risultati di B+ e B.");
        }
    }*/

    private void saveDatasetAsCSV(Instances bPlusDataset, Instances cDataset, Instances bDataset) {

        CSVSaver saver = new CSVSaver(); // Crea l'oggetto saver una sola volta

        try {
            // Salva bPlusDataset
            saver.setInstances(bPlusDataset); // Imposta il dataset corretto
            saver.setFile(new java.io.File("output" + File.separator + projectName + "_BPlus.csv"));
            saver.writeBatch();
            logger.info("Dataset BPlus salvato in: output" + File.separator + projectName + "_BPlus.csv");

            // Salva bDataset
            saver.setInstances(bDataset); // Imposta il dataset corretto
            saver.setFile(new java.io.File("output" + File.separator + projectName + "_BDataset.csv"));
            saver.writeBatch();
            logger.info("Dataset B salvato in: output" + File.separator + projectName + "_BDataset.csv");


            // Salva cDataset
            saver.setInstances(cDataset); // Imposta il dataset corretto
            saver.setFile(new java.io.File("output" + File.separator + projectName + "_CDataset.csv"));
            saver.writeBatch();
            logger.info("Dataset C salvato in: output" + File.separator + projectName + "_CDataset.csv");

        } catch (Exception e) {
            logger.severe("Errore durante il salvataggio dei dataset B+, B, C in CSV: " + e.getMessage());

        }
    }
}


