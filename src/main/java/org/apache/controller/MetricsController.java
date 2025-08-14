package org.apache.controller;

import com.github.javaparser.ast.body.MethodDeclaration;
import lombok.Setter;
import org.apache.model.AnalyzedClass;
import org.apache.model.AnalyzedMethod;

import java.util.List;
import java.util.Map;

@Setter
public class MetricsController {
    private Map<String, List<AnalyzedClass>> analysisResults;
    private final GitController gitController;

    public MetricsController(GitController git){
        this.gitController = git;
    }

    public static String getStringBody(MethodDeclaration methodDeclaration) {
        return methodDeclaration.getBody().map(Object::toString).orElse("{}");
    }






    public void calculateMetrics(AnalyzedClass classSnapshot) {
        calculateLocMetrics(classSnapshot);
        calculateCycloComplexityMetrics(classSnapshot);
        calculateCodeSmellsMetrics(classSnapshot);
        calculateCognitiveComplexityMetrics(classSnapshot);
        calculateNestingDepthMetrics(classSnapshot);
    }

    public void calculateStatics(AnalyzedClass classSnapshot) {
        calculateChurnMetrics(classSnapshot);
        calculateMaxChurnMetrics(classSnapshot);
        calculateAvgChurnMetrics(classSnapshot);
        calculateMethodHistoryMetrics(classSnapshot);
        calculateNumAuthorsMetrics(classSnapshot);
    }

    public int calculateLocMetrics(AnalyzedClass classSnapshot) {
        int totalLOC = 0;

        for (AnalyzedMethod method : classSnapshot.getMethods()) {
            String body = getStringBody(method.getMethodDeclaration());
            int methodLOC = body.split("\n").length;

            // Aggiorna le metriche del metodo
            method.getMetrics().setLOC(methodLOC);

            totalLOC += methodLOC;
        }

        // Aggiorna le metriche della classe
        classSnapshot.getProcessMetrics().setLOC(totalLOC);

        return totalLOC;
    }
    public int calculateCycloComplexityMetrics(AnalyzedClass classSnapshot) {
        int totalCC=0;
        for (AnalyzedMethod method : classSnapshot.getMethods()) {
            MethodDeclaration methodDeclaration = method.getMethodDeclaration();
            int cc=1;
            String body = getStringBody(methodDeclaration);
            cc=+ countOccurrences(body, "if") +
                countOccurrences(body, "for") +
                countOccurrences(body, "while") +
                countOccurrences(body, "case") +
                countOccurrences(body, "catch")+
                countOccurrences(body, "&&") +
                countOccurrences(body, "||");
            // Aggiorna le metriche del metodo
            method.getMetrics().setCycloComplexity(cc);
            totalCC += cc;

        }
        // Aggiorna le metriche della classe
        classSnapshot.getProcessMetrics().setCycloComplexity(totalCC);
        return totalCC;
    }
    private int countOccurrences(String body, String occurrence){
        int count = 0;
        int index = 0;
        while (index != -1) {
            index=body.indexOf(occurrence, index);
            if( index != -1) {
                count++;
                index += occurrence.length(); // Sposta l'indice oltre l'occorrenza trovata
            }

        }
        return count;
    }
    public int calculateCodeSmellsMetrics(AnalyzedClass classSnapshot) {
        // Implementazione per calcolare i code smells
        return 0; // Placeholder
    }
    public int calculateCognitiveComplexityMetrics(AnalyzedClass classSnapshot) {
        // Implementazione per calcolare la complessità cognitiva
        return 0; // Placeholder
    }
    public int calculateNestingDepthMetrics(AnalyzedClass classSnapshot) {
        int maxClassNesting = 0;

        for (AnalyzedMethod method : classSnapshot.getMethods()) {
            MethodDeclaration md = method.getMethodDeclaration();
            String body = getStringBody(md);

            // Calcola la profondità di annidamento
            int maxNesting = calculateMaxNestingDepth(body);

            // Aggiorna le metriche del metodo
            method.getMetrics().setNestingDepth(maxNesting);

            // Aggiorna il valore massimo per la classe
            maxClassNesting = Math.max(maxClassNesting, maxNesting);
        }

        // Aggiorna le metriche della classe
        classSnapshot.getMetrics().setNestingDepth(maxClassNesting);

        return maxClassNesting;
    }

    private int calculateMaxNestingDepth(String code) {
        int maxDepth = 0;
        int currentDepth = 0;

        for (char c : code.toCharArray()) {
            if (c == '{') {
                currentDepth++;
                maxDepth = Math.max(maxDepth, currentDepth);
            } else if (c == '}') {
                currentDepth--;
            }
        }

        return maxDepth;
    }


    public int calculateChurnMetrics(AnalyzedClass classSnapshot) {
        int totalChurn = 0;

        for (AnalyzedMethod method : classSnapshot.getMethods()) {
            String methodSignature = getStringBody(method.getMethodDeclaration());

            // Ottieni la cronologia del metodo dal GitController
            int methodChurn = //TODO gitController.getMethodChurn(methodSignature, classSnapshot.getRelease());

            // Aggiorna le metriche del metodo
            method.getMetrics().setChurn(methodChurn);

            totalChurn += methodChurn;
        }

        // Aggiorna le metriche della classe
        classSnapshot.getProcessMetrics().setChurn(totalChurn);

        return totalChurn;
    }
    public int calculateMaxChurnMetrics(AnalyzedClass classSnapshot) {
        // Implementazione per calcolare il churn massimo
        return 0; // Placeholder
    }
    public int calculateAvgChurnMetrics(AnalyzedClass classSnapshot) {
        // Implementazione per calcolare il churn medio
        return 0; // Placeholder
    }
    public int calculateMethodHistoryMetrics(AnalyzedClass classSnapshot) {
        // Implementazione per calcolare la storia dei metodi
        return 0; // Placeholder
    }
    public int calculateNumAuthorsMetrics(AnalyzedClass classSnapshot) {
        // Implementazione per calcolare il numero di autori
        return 0; // Placeholder
    }

    public void generateDataset(AnalyzedClass classSnapshot) {
        // Implementazione per generare il dataset
        // Potrebbe essere necessario iterare sui metodi e raccogliere le metriche
        // e le informazioni necessarie per il dataset.
    }

}
