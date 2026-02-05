package org.apache.whatif;


import org.apache.logging.Printer;

public class RunPrediction{

    private static final String BASE_PATH = "whatif/";

    public static void main(String[] args) {

        try {
            Printer.printBlue("INIZIO: Predizione What-If");

            String project = "STORM"; // Cambia qui per ogni progetto
            String datasetAPath = "datasetA.csv";
            String outputSummaryCsv = BASE_PATH + project + "_summary.csv";

            PredictionWhatIf.runPrediction(
                    datasetAPath,
                    outputSummaryCsv,
                    project
            );

            Printer.printBlue("FINALIZIO: Predizione What-If");
        } catch (Exception e) {
            Printer.errorPrint("Errore nella predizione What-If: " + e.getMessage());
        }
    }
}
