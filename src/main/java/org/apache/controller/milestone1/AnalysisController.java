package org.apache.controller.milestone1;

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


    public void startAnalysis(String configFilePath) {
        // Qui si avvia l'analisi del progetto specificato
        // Implementazione dell'analisi
        JSONObject targets = Objects.requireNonNull(JsonReader.load(configFilePath));
        Iterator<String> keys = targets.keys();
        int numTasks= targets.length();
        Logger logger = CollectLogger.getInstance().getLogger();


        CountDownLatch latch = new CountDownLatch(numTasks);

        try (ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())) {
            int count = 0;
            while (keys.hasNext()) {
                String key = keys.next();
                String value = targets.getString(key);
                ProcessController processController = new ProcessController(count, latch, key, value);
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