package org.apache;


import org.apache.controller.milestone1.AnalysisController;
import org.apache.logging.Printer;




public class SmellAnalyzer {
    public static void main(String[] args)  {


        String configFilePath = args[0];
        //In config.json si trovano le repo Git da analizzare
        //"BOOKKEEPER": "https://github.com/tostfia/bookkeeper.git"
        //"STORM": "https://github.com/tostfia/storm.git"


        Printer.printlnGreen("\n--------------------------------------------ANALISI AVVIATA--------------------------------------------\n");
        long startTime = System.currentTimeMillis();

        try {
            AnalysisController controller = new AnalysisController();
            controller.startAnalysis(configFilePath);
        }catch (Exception e) {
            Printer.errorPrint(e.getMessage());
        }
        long endTime = System.currentTimeMillis();
        double elapsedSeconds = (endTime - startTime) / 1000.0;
        Printer.printlnBlue("Elapsed Time:"+ elapsedSeconds+"secondi\n");


    }
}