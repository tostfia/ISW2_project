package org.apache;


import org.apache.controller.milestone1.MetricsController;
import org.apache.logging.Printer;
import org.apache.model.AnalyzedClass;
import org.apache.model.Release;
import org.apache.utilities.writer.CsvWriter;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Refactor {
    public static void main(String[] args) throws Exception {

        String classeStorm ="refactor/JdbcClient.java";
        String classeBookkeeper ="refactor/BookieServer.java";


        //1.Carico il file
        String stormCode = Files.readString(Paths.get(classeStorm));
        String bookkeeperCode = Files.readString(Paths.get(classeBookkeeper));

        //2. Inserisco la release
        Release releaseStorm = new Release("0.10.2", "Apache Storm", "2026-01-04");
        Release releaseBookkeeper = new Release("4.2.1", "Apache Bookkeeper", "2024-05-14");

        //3. Creo gli AnalyzedClass
        List<AnalyzedClass> snapshot= new ArrayList<>();
        snapshot.add(new AnalyzedClass("JdbcClient", stormCode, releaseStorm,"org.apache.storm.jdbc.common", "JdbcClient.java"));
        snapshot.add(new AnalyzedClass("BookieServer", bookkeeperCode, releaseBookkeeper,"org.apache.bookkeeper.bookie", "BookieServer.java"));

        MetricsController mc = new MetricsController(snapshot, null);
        mc.processMetrics();
        String outputCsv = "refactor/metrics_output.csv";
        try (CsvWriter csvWriter = new CsvWriter(outputCsv, "RefactorProject")){
            csvWriter.writeResultsForClass(snapshot);
        }catch( IOException e){
            Printer.errorPrint(e.getMessage());
        }
        //Classifier classifier = ClassifierFactory.build("RandomForest",42);
        //classifier.buildClassifier();

    }


}
