package org.apache.controller;

import org.apache.logging.CollectLogger;
import org.apache.utilities.JsonReader;
import org.apache.utilities.enums.AnalysisMode;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class AnalysisController {
    //Classe ha il controllo di tutto il processo di analisi
    public AnalysisController(){}

    public void startAnalysis(String configFilePath, AnalysisMode mode) {
        // Qui si avvia l'analisi del progetto specificato
        // Implementazione dell'analisi
        JSONObject targets = Objects.requireNonNull(JsonReader.load(configFilePath));
        Iterator<String> keys = targets.keys();
        int numTasks= targets.length();
        Logger logger = CollectLogger.getInstance().getLogger();
        logger.info("Numero di progetti da analizzare: " + numTasks);

        CountDownLatch latch = new CountDownLatch(numTasks);

        try (ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())) {
            int count = 0;
            while (keys.hasNext()) {
                String key = keys.next();
                String value = targets.getString(key);
                ProcessController processController = new ProcessController(count, latch, key, value,mode);
                executorService.submit(processController);
                count++;
            }
            logger.info("Tutti i task sono stati sottomessi. In attesa del completamento...");
            latch.await();
            logger.info("Tutti i task hanno terminato. Analisi completata.");

        }catch (InterruptedException e){
            logger.severe(e.getMessage());
            Thread.currentThread().interrupt();
        }

    }

}