package org.apache.controller;

import org.apache.logging.CollectLogger;
import tech.tablesaw.api.Table;

import java.util.Comparator;
import java.util.logging.Logger;

public class CorrelationController {
        private final Table datasetA;
        private static final Logger logger = CollectLogger.getInstance().getLogger();

        public CorrelationController(Table datasetA) {
            this.datasetA = datasetA;
        }

        public String findActionableFeature() {
            // Esempio semplice: Pearson correlation
            String bestFeature = null;
            double maxCorr = 0.0;

           /* for (String feature : datasetA.getFeatureNames()) {
                if (feature.equals("bugginess")) continue;
                if (!isActionable(feature)) continue;

                double corr = CorrelationUtils.computePearson(datasetA, feature, "bugginess");
                if (corr > maxCorr) {
                    maxCorr = corr;
                    bestFeature = feature;
                }
            }*/

            Logger.getLogger("CorrelationController")
                    .info("Best actionable feature: " + bestFeature + " (corr=" + maxCorr + ")");
            return bestFeature;
        }

       /* public Method findMethodToRefactor(String actionableFeature) {
            // Cerca nell’ultima release
            List<Method> lastReleaseMethods = DatasetUtils.getLastReleaseMethods(datasetA);

            return lastReleaseMethods.stream()
                    .filter(Method::isBuggy)
                    .max(Comparator.comparingInt(m -> m.getFeatureValue(actionableFeature)))
                    .orElse(null);
        }*/

        private boolean isActionable(String feature) {
            // Heuristica: smells, complessità, lunghezza…
            return feature.contains("Smells") || feature.contains("Complexity");
        }
}

