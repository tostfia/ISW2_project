package org.apache;


import org.apache.controller.AnalysisController;
import org.apache.logging.CollectLogger;
import org.apache.utilities.enums.AnalysisMode;

import java.util.logging.Logger;

public class Main {
    public static void main(String[] args)  {
        Logger logger= CollectLogger.getInstance().getLogger();
        if (args.length < 2) {
            logger.severe("Uso: java -jar <jarfile> <modalità> <file_config>");
            logger.severe("Modalità disponibili: 'ANALYZE_HISTORY' o 'BUILD_FINAL_DATASET'");
            return;
        }

        AnalysisMode mode = AnalysisMode.valueOf(args[0].toUpperCase()); // Converte la stringa in enum
        String configFilePath = args[1];
        //File json di configurazione
        logger.info("--------------------------------------------ANALISI AVVIATA--------------------------------------------");
        long startTime = System.currentTimeMillis();

        try {
            AnalysisController controller = new AnalysisController();
            controller.startAnalysis(configFilePath,mode);
        }catch (Exception e){
            logger.severe(e.getMessage());
        }
        long endTime = System.currentTimeMillis();
        logger.info("--------------------------------------------ANALISI TERMINATA in"+ (endTime-startTime)/1000.0+"secondi--------------------------------------------");


    }
}