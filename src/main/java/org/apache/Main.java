package org.apache;


import org.apache.controller.AnalysisController;
import org.apache.logging.CollectLogger;

import java.util.logging.Logger;


public class Main {
    public static void main(String[] args)  {
        Logger logger= CollectLogger.getInstance().getLogger();

        String configFilePath = args[0];

        //File json di configurazione
        logger.info("--------------------------------------------ANALISI AVVIATA--------------------------------------------");
        long startTime = System.currentTimeMillis();

        try {
            AnalysisController controller = new AnalysisController();
            controller.startAnalysis(configFilePath);
        }catch (Exception e) {
            logger.severe(e.getMessage());
        }
        long endTime = System.currentTimeMillis();
        double elapsedSeconds = (endTime - startTime) / 1000.0;
        logger.config("Elapsed Time: " + elapsedSeconds + " seconds");


    }
}