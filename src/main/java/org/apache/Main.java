package org.apache;


import org.apache.controller.AnalysisController;
import org.apache.logging.CollectLogger;

import java.util.logging.Logger;

public class Main {
    public static void main(String[] args) throws Exception {
        Logger logger= CollectLogger.getInstance().getLogger();
        if(args.length<1){
            logger.severe("Uso: java -jar analyzer.jar <file_config> [progetto |--all]");
            throw new Exception("Error: file di configurazione mancante");

        }
        String target = args[0]; //Utente specifica il progetto
        // Se il secondo parametro è --all, allora si analizzano tutti i progetti
        String projectToAnalyze = (args.length > 1 ? args[1]: "--all");
        logger.info("--------------------------------------------ANALISI AVVIATA--------------------------------------------");
        long startTime = System.currentTimeMillis();

        try {
            AnalysisController controller = new AnalysisController();
            controller.startAnalysis(projectToAnalyze);
        }catch (Exception e){
            logger.severe(e.getMessage());
        }
        long endTime = System.currentTimeMillis();
        logger.info("--------------------------------------------ANALISI TERMINATA in"+ (endTime-startTime)/1e-9+"secondi--------------------------------------------");





    }
}