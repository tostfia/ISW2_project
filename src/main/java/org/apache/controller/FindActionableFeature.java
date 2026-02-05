package org.apache.controller;


import org.apache.logging.Printer;
import tech.tablesaw.api.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FindActionableFeature {


    private final Table datasetA;

    private final String projectName;
    private final CorrelationController cc;
    private static final String OUTPUT_DIR = "output";


    private record FeatureRankingRow(
            int rank,
            String feature,
            double value,
            double correlation
    ) {}


    public FindActionableFeature( Table datasetA, String projectName) {
        this.datasetA = datasetA;
        this.projectName = projectName;
        this.cc = new CorrelationController(datasetA);

    }

    public void run() throws Exception {

        Printer.printGreen("Avvio analisi What-If...\n");
        CorrelationController.FeatureCorrelation bestFeature = cc.getBestFeature();
        String aFeature = bestFeature.featureName().replaceAll("[‘’“”'\"`]", "").trim();
        CorrelationController.FeatureValue buggyMethod = findBuggyMethod(aFeature);

        saveFeatureRankingToCSV(
                buildFeatureRankingForMethod(buggyMethod.index(), aFeature)
        );

    }

    /** Trova il metodo buggy con valore massimo della feature */
    private CorrelationController.FeatureValue findBuggyMethod(String feature) {
        return cc.findBuggyMethodWithMaxFeature(feature);
    }

    private List<FeatureRankingRow> buildFeatureRankingForMethod(
            int methodIndex,
            String aFeature
    ) {

        // Tutte le correlazioni (calcolate una sola volta)
        List<CorrelationController.FeatureCorrelation> correlations =
                cc.computeAndSaveFullRanking(projectName);

        // Map veloce: feature → correlazione
        Map<String, CorrelationController.FeatureCorrelation> corrMap =
                correlations.stream()
                        .collect(Collectors.toMap(
                                CorrelationController.FeatureCorrelation::featureName,
                                c -> c
                        ));

        List<FeatureRankingRow> rows = new ArrayList<>();

        for (String colName : datasetA.columnNames()) {

            if (!datasetA.column(colName).type().equals(ColumnType.DOUBLE)
                    && !datasetA.column(colName).type().equals(ColumnType.INTEGER))
                continue;

            if (!cc.isActionable(colName)) continue;

            var corr = corrMap.get(colName);
            if (corr == null) continue;

            double value = datasetA.numberColumn(colName).getDouble(methodIndex);

            rows.add(new FeatureRankingRow(
                    0, // temporaneo
                    colName,
                    value,
                    corr.correlation()
            ));
        }

        // Ordina: AFeature prima, poi |correlation| desc
        rows.sort((a, b) -> {
            if (a.feature.equals(aFeature)) return -1;
            if (b.feature.equals(aFeature)) return 1;
            return Double.compare(
                    Math.abs(b.correlation),
                    Math.abs(a.correlation)
            );
        });

        // Assegna ranking
        for (int i = 0; i < rows.size(); i++) {
            rows.set(i, new FeatureRankingRow(
                    i + 1,
                    rows.get(i).feature,
                    rows.get(i).value,
                    rows.get(i).correlation
            ));
        }

        return rows;
    }


    private void saveFeatureRankingToCSV(
            List<FeatureRankingRow> ranking
    ) {

        try {
            IntColumn rankCol = IntColumn.create("Rank");
            StringColumn featureCol = StringColumn.create("Feature");
            DoubleColumn valueCol = DoubleColumn.create("FeatureValue");
            DoubleColumn rhoCol = DoubleColumn.create("SpearmanRho");


            for (FeatureRankingRow r : ranking) {
                rankCol.append(r.rank);
                featureCol.append(r.feature);
                valueCol.append(r.value);
                rhoCol.append(r.correlation);

            }

            Table table = Table.create("Feature_Ranking")
                    .addColumns(rankCol, featureCol, valueCol, rhoCol);

            String path = OUTPUT_DIR + File.separator + projectName + "_feature_ranking.csv";
            table.write().csv(path);

            Printer.printGreen("Ranking feature salvato in: " + path + "\n");

        } catch (Exception e) {
            Printer.errorPrint("Errore nel salvataggio ranking feature: " + e.getMessage());
        }
    }





}