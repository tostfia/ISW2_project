package org.apache.controller;

import org.apache.logging.CollectLogger;
import org.apache.utilities.JsonReader;
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

    public void startAnalysis(String projectToAnalyze) {
        // Qui si avvia l'analisi del progetto specificato
        // Implementazione dell'analisi
        JSONObject targets = Objects.requireNonNull(JsonReader.load(projectToAnalyze));
        Iterator<String> keys = targets.keys();
        int threads= targets.length();
        Logger logger = CollectLogger.getInstance().getLogger();
        String startMessage = "Analisi avviata per il progetto: " + projectToAnalyze + " con " + threads + " thread.";
        logger.info(startMessage);

        CountDownLatch latch = new CountDownLatch(threads);
        int count=0;
        try (ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())) {
            while (keys.hasNext()) {
                String key = keys.next();
                String value = targets.getString(key);
                Process process = new Process(count, latch, key, value);
                count++;
            }
            latch.await();

        }catch (InterruptedException e){
            logger.severe(e.getMessage());
            Thread.currentThread().interrupt();
        }

    }

}