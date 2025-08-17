package org.apache;


import org.apache.controller.AnalysisController;
import org.apache.logging.CollectLogger;

import java.util.logging.Logger;

public class Main {
    public static void main(String[] args)  {
        Logger logger= CollectLogger.getInstance().getLogger();
         // Converte la stringa in enum
        String configFilePath = args[0];
        //"STORM": "https://github.com/tostfia/storm.git"
        //"BOOKKEEPER": "https://github.com/tostfia/bookkeeper.git"
        //File json di configurazione
        logger.info("--------------------------------------------ANALISI AVVIATA--------------------------------------------");
        long startTime = System.currentTimeMillis();

        try {
            AnalysisController controller = new AnalysisController();
            controller.startAnalysis(configFilePath);
        }catch (Exception e){
            logger.severe(e.getMessage());
        }
        long endTime = System.currentTimeMillis();
        logger.info("--------------------------------------------ANALISI TERMINATA in"+ (endTime-startTime)/1000.0+"secondi--------------------------------------------");


    }
}