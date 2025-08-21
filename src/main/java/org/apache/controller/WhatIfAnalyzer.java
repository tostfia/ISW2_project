package org.apache.controller;
import org.apache.logging.CollectLogger;
import org.apache.model.AggregatedClassifierResult;
import org.apache.model.PredictionResult;
import tech.tablesaw.api.Table;
import weka.classifiers.Classifier;
import weka.core.Instances;
import weka.core.Attribute;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Remove;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;



public class WhatIfAnalyzer {
    private final AggregatedClassifierResult bClassifier; // Il miglior classificatore selezionato (BClassifierA)
    private final Table datasetA; // Il dataset completo 'A' (dovrebbe essere Instances di Weka)
    private final String projectName;
    private final CorrelationController correlationController; // Per il calcolo delle correlazioni
    private final Logger logger;
    private Classifier loadedWekaClassifier; // Il modello Weka caricato

    // Costruttore: riceve il miglior classificatore, il dataset 'A' e il nome del progetto
    public WhatIfAnalyzer(AggregatedClassifierResult bClassifier, Table datasetA, String projectName) {
        this.bClassifier = bClassifier;
        this.datasetA = datasetA;
        this.projectName = projectName;
        this.correlationController = new CorrelationController(datasetA); // Inizializza il CorrelationController
        this.logger = CollectLogger.getInstance().getLogger();
    }

    public void run() throws Exception {
        logger.info("Avvio dell'analisi 'What-If'..., non ancora iniziata");

        // PASSO 1: Identificazione di AFeature (Punti 4 e 5 della Slide 2)
        //String aFeature = identifyAFeature();
        //logger.info("Feature Azionabile (AFeature) identificata: " + aFeature);

        // Carica il modello del classificatore BClassifierA
        // Il BClassifier dovrebbe contenere il percorso al file del modello Weka salvato (.model)
        /*String modelFilePath = bClassifier.getModelFilePath();
        if (modelFilePath == null || modelFilePath.isEmpty()) {
            logger.severe("Percorso del modello del classificatore non trovato. Impossibile procedere con le predizioni.");
            return;
        }
        loadClassifierModel(modelFilePath);
        if (loadedWekaClassifier == null) {
            logger.severe("Impossibile caricare il modello del classificatore. Analisi interrotta.");
            return;
        }*/

        // PASSO 2: Creazione dei dataset B+, C, B (Punto 10 della Slide 4)
       /* logger.info("Creazione dei dataset B+, C e B...");
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


        // PASSO 3: Predizioni su A, B, B+, C (Punto 12 della Slide 4)
        logger.info("Esecuzione delle predizioni...");
        Map<String, PredictionResult> results = new HashMap<>();

        results.put("A", predict(datasetA, "A"));
        results.put("B+", predict(bPlusDataset, "B+"));
        results.put("C", predict(cDataset, "C"));
        results.put("B", predict(bDataset, "B"));

        // PASSO 4: Analisi e Risposta (Punto 13 della Slide 4)
        analyzeResults(results, aFeature);

        // PASSO 5: Rispondere alle Domande Preliminari (Slide 3)
        // Questa parte richiede un confronto più dettagliato tra AFMethod e AFMethod2
        // Che sono rappresentati concettualmente dal confronto B+ vs B.
        answerPreliminaryQuestions(results, aFeature);

        logger.info("Analisi 'What-If' completata.");
    }

    /**
     * Punto 4 & 5 (Slide 2): Calcola la correlazione e identifica la AFeature.
     */
   /* private String identifyAFeature() {
        // Qui userai il tuo CorrelationController
        // Esempio: List<FeatureCorrelation> correlations = correlationController.computeCorrelations();
        // Quindi, cerca la feature "azionabile" (e.g., NSmells) con la correlazione più alta.
        // Per il contesto di questo progetto, è fortemente implicito che AFeature sia NSmells.
        // Assicurati che 'NSmells' sia presente come attributo nel dataset 'A'.
       /* Attribute nSmellsAttribute = datasetA.attribute("NSmells");
        if (nSmellsAttribute == null) {
            logger.severe("Attributo 'NSmells' non trovato nel dataset. Impossibile procedere con l'analisi 'What-If'.");
            throw new IllegalArgumentException("Attributo 'NSmells' mancante.");
        }
        return "NSmells"; // Implementazione concreta userà correlationController per determinare dinamicamente.
    }

    /**
     * Carica il modello del classificatore Weka salvato.
     */
   /* private void loadClassifierModel(String modelPath) {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(modelPath))) {
            loadedWekaClassifier = (Classifier) ois.readObject();
            logger.info("Modello del classificatore caricato con successo da: " + modelPath);
        } catch (Exception e) {
            logger.severe("Errore durante il caricamento del modello del classificatore da " + modelPath + ": " + e.getMessage());
            loadedWekaClassifier = null;
        }
    }

    /**
     * Punto 10.1 (Slide 4): Crea il dataset B+ (metodi con NSmells > 0 e considerati buggy).
     * Nota: Per "buggy" ci riferiamo al target attribute (es. 'bug' = true).
     */
  /*  private Instances createBPlusDataset(String aFeature) throws Exception {
        Instances bPlus = new Instances(datasetA, 0); // Crea un nuovo dataset con la stessa struttura

        int aFeatureIndex = datasetA.attribute(aFeature).index();
        int bugIndex = datasetA.classAttribute().index();

        for (int i = 0; i < datasetA.numInstances(); i++) {
            weka.core.Instance instance = datasetA.instance(i);
            // In Weka, il valore di un attributo numerico viene recuperato con instance.value(index)
            // e il valore del class attribute (bugginess) è instance.classValue()
            // Assumiamo che il class attribute sia binario (0=non buggy, 1=buggy)
            if (instance.value(aFeatureIndex) > 0 && instance.classValue() == 1.0) { // Se NSmells > 0 e 'bug' è vero
                bPlus.add(instance);
            }
        }
        logger.info("Creato B+: " + bPlus.numInstances() + " istanze.");
        return bPlus;
    }

    /**
     * Punto 10.2 (Slide 4): Crea il dataset C (metodi con NSmells = 0).
     */
   /* private Instances createCDataset(String aFeature) throws Exception {
        Instances cDataset = new Instances(datasetA, 0);

        int aFeatureIndex = datasetA.attribute(aFeature).index();

        for (int i = 0; i < datasetA.numInstances(); i++) {
            weka.core.Instance instance = datasetA.instance(i);
            if (instance.value(aFeatureIndex) == 0) { // Se NSmells = 0
                cDataset.add(instance);
            }
        }
        logger.info("Creato C: " + cDataset.numInstances() + " istanze.");
        return cDataset;
    }

    /**
     * Punto 10.3 (Slide 4): Crea il dataset B (B+ manipolato con NSmells settato a 0).
     * Questo simula la rimozione degli smells.
     */
  /*  private Instances createBDataset(Instances bPlusDataset, String aFeature) throws Exception {
        if (bPlusDataset == null || bPlusDataset.isEmpty()) {
            return new Instances(datasetA, 0); // Ritorna un dataset vuoto con la stessa struttura
        }

        Instances bDataset = new Instances(bPlusDataset); // Clona B+
        int aFeatureIndex = bDataset.attribute(aFeature).index();

        for (int i = 0; i < bDataset.numInstances(); i++) {
            weka.core.Instance instance = bDataset.instance(i);
            instance.setValue(aFeatureIndex, 0); // Setta NSmells a 0
        }
        logger.info("Creato B (da B+ con NSmells=0): " + bDataset.numInstances() + " istanze.");
        return bDataset;
    }


    /**
     * Punto 12 (Slide 4): Esegue le predizioni sui dataset e raccoglie i risultati.
     */
    /*private PredictionResult predict(Instances dataToPredict, String datasetName) throws Exception {
        if (dataToPredict == null || dataToPredict.isEmpty()) {
            logger.warning("Dataset '" + datasetName + "' è vuoto. Impossibile effettuare predizioni.");
            return new PredictionResult(0, 0, 0); // Ritorna un risultato vuoto
        }

        int actualBuggy = 0;
        int predictedBuggy = 0;
        int correctlyPredictedBuggy = 0;

        int classIndex = dataToPredict.classAttribute().index();

        // Necessario impostare l'indice dell'attributo classe per le predizioni
        dataToPredict.setClassIndex(classIndex);

        for (int i = 0; i < dataToPredict.numInstances(); i++) {
            weka.core.Instance instance = dataToPredict.instance(i);

            // Ottieni il valore effettivo dell'attributo classe
            double actualClassValue = instance.classValue();
            if (actualClassValue == 1.0) { // Se il metodo è effettivamente buggy (assumendo 1.0 = buggy)
                actualBuggy++;
            }

            // Effettua la predizione
            double predictedClassValue = loadedWekaClassifier.classifyInstance(instance);

            if (predictedClassValue == 1.0) { // Se il classificatore predice che è buggy
                predictedBuggy++;
                if (actualClassValue == 1.0) { // Se la predizione è corretta (vero positivo)
                    correctlyPredictedBuggy++;
                }
            }
        }
        logger.info("Predizioni per " + datasetName + ": Totale istanze = " + dataToPredict.numInstances() +
                ", Actual Buggy = " + actualBuggy +
                ", Predicted Buggy = " + predictedBuggy +
                ", Correctly Predicted Buggy = " + correctlyPredictedBuggy);
        return new PredictionResult(actualBuggy, predictedBuggy, correctlyPredictedBuggy);
    }

    /**
     * Punto 13 (Slide 4): Analizza la tabella dei risultati e risponde alla domanda chiave.
     */
   /* private void analyzeResults(Map<String, PredictionResult> results, String aFeature) {
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
   /* private void answerPreliminaryQuestions(Map<String, PredictionResult> results, String aFeature) {
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

    }
}


