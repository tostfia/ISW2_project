package org.apache.whatif;

import org.apache.logging.Printer;
import weka.core.Instances;
import weka.core.converters.ConverterUtils;


public class RunDatasetBuilder {


        public static void main(String[] args) {
            try {
                Printer.printlnBlue("INIZIO: Costruzione sotto-dataset per analisi What-If");

                // Carica il dataset A (dal path corretto in base al progetto)

                Instances datasetA = new ConverterUtils.DataSource("datasetA.csv").getDataSet();


                // Imposta l'attributo target se necessario
                if (datasetA.classIndex() == -1) {
                    datasetA.setClassIndex(datasetA.numAttributes() - 1); // assumiamo ultima colonna = bugginess
                }

                // Costruisci B+, C, B
                DatasetBuilder builder = new DatasetBuilder("whatif/");
                Instances bPlus = builder.buildBPlus(datasetA);
                builder.buildC(datasetA);
                builder.buildB(bPlus);

                Printer.printlnBlue("Costruzione completata: sotto-dataset What-If creati con successo.");
            } catch (Exception e) {
                Printer.errorPrint(e.getMessage());
            }
        }


}
