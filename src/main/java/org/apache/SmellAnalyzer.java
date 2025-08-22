package org.apache;


import org.apache.controller.milestone1.AnalysisController;
import org.apache.logging.CollectLogger;

import java.util.logging.Logger;


public class SmellAnalyzer {
    public static void main(String[] args)  {
        Logger logger= CollectLogger.getInstance().getLogger();

        String configFilePath = args[0];
        //In config.json si trovano le repo Git da analizzare
        //"BOOKKEEPER": "https://github.com/tostfia/bookkeeper.git"
        //"STORM": "https://github.com/tostfia/storm.git"


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
        logger.info(()->"Elapsed Time:"+ elapsedSeconds+"secondi");


    }
}