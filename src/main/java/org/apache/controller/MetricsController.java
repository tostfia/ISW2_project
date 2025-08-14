package org.apache.controller;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.stmt.*;
import org.apache.model.AnalyzedClass;
import org.apache.model.AnalyzedMethod;
import org.apache.model.Commit;
import org.apache.model.Release;
import org.apache.utilities.metrics.CognitiveComplexityVisitor;
import org.apache.utilities.metrics.NestingVisitor;


import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Orchestra il calcolo di tutte le metriche per una lista di classi.
 * Versione ottimizzata per ridurre l'uso di memoria.
 */
public class MetricsController {
    List<Release> releases;
    List<AnalyzedClass> analyzedClasses;
    GitController gitController;
    String targetName;
    public MetricsController(List<Release> releases, List<AnalyzedClass> snapshot, GitController gitController, String targetName) {
        this.releases = releases;
        this.gitController = gitController;
        this.targetName = targetName;
        this.analyzedClasses = snapshot;
    }
    public void processMetrics() throws IOException {
        processSize();
        processNumberOfAuthors();
        processLOC();
        processCyclomaticComplexity();
        processNestingDepth();
        processCognitiveComplexity();
        //processChurn();
        //processMethodHistory();
        //processNumberOfCodeSmells();
        //processBuggyMethods();

    }
    private void processSize(){
        for (AnalyzedClass analyzedClass : analyzedClasses){
            String[] lines= analyzedClass.getFileContent().split("\r\n|\r|\n");
            analyzedClass.getProcessMetrics().setSize(lines.length);
        }
    }
    /**
     * Metodo principale per processare il numero di autori per classi e metodi.
     */
    private void processNumberOfAuthors() {
        for (AnalyzedClass analyzedClass : analyzedClasses) {
            // Calcola e imposta gli autori per la classe
            int classAuthorCount = calculateUniqueAuthors(analyzedClass.getTouchingClassCommitList());
            analyzedClass.getProcessMetrics().setNumAuthors(classAuthorCount);

            // Calcola e imposta gli autori per ogni metodo
            for (AnalyzedMethod analyzedMethod : analyzedClass.getMethods()) {
                // Assumiamo che esista getTouchingMethodCommitList() o un metodo simile
                int methodAuthorCount = calculateUniqueAuthors(analyzedMethod.getTouchingMethodCommitList());
                analyzedMethod.getMetrics().setNumAuthors(methodAuthorCount);
            }
        }
    }

    private int calculateUniqueAuthors(List<Commit> commits) {
        if (commits == null || commits.isEmpty()) {
            return 0;
        }

        // Usare un HashSet è più efficiente per garantire l'unicità
        Set<String> authors = new HashSet<>();
        for (Commit commit : commits) {
            authors.add(commit.getRevCommit().getAuthorIdent().getName());
        }
        return authors.size();
    }
    private  void processLOC() {
        for (AnalyzedClass analyzedClass: analyzedClasses){
            long loc = 0; // Inizializza loc a 0 per ogni classe
            for(AnalyzedMethod method: analyzedClass.getMethods()){
                long mloc = method.getBody().lines().count();
                method.getMetrics().setLOC(mloc);
                loc += mloc; // Aggiunge le righe del metodo al totale della classe
            }
            analyzedClass.getProcessMetrics().setLOC(loc);
        }
    }
    private void processCyclomaticComplexity() {
        int cc=1;
        for (AnalyzedClass analyzedClass : analyzedClasses) {
            for (AnalyzedMethod method : analyzedClass.getMethods()) {
                cc+=method.getMethodDeclaration().findAll(IfStmt.class).size();
                cc+=method.getMethodDeclaration().findAll(ForStmt.class).size();
                cc+=method.getMethodDeclaration().findAll(ForEachStmt.class).size();
                cc+=method.getMethodDeclaration().findAll(WhileStmt.class).size();
                cc+= method.getMethodDeclaration().findAll(CatchClause.class).size();
                cc+= method.getMethodDeclaration().findAll(SwitchEntry.class).size();
                cc+= (Math.toIntExact(method.getMethodDeclaration().findAll(BinaryExpr.class).stream()
                        .filter(expr -> expr.getOperator() == BinaryExpr.Operator.AND
                                || expr.getOperator() == BinaryExpr.Operator.OR)
                        .count()));
                method.getMetrics().setCycloComplexity(cc);
            }
            analyzedClass.getProcessMetrics().setCycloComplexity(cc);
        }

    }
    public void processNestingDepth() {
        for (AnalyzedClass analyzedClass : analyzedClasses) {
            int maxClassNesting = 0; // Per tenere traccia della profondità massima nella classe

            for (AnalyzedMethod analyzedMethod : analyzedClass.getMethods()) {
                // Ottieni la MethodDeclaration dall'AnalyzedMethod (assumendo che esista un getter)
                MethodDeclaration methodDeclaration = analyzedMethod.getMethodDeclaration();

                // Calcola la profondità per il singolo metodo usando il Visitor
                int methodNesting = calculateNestingDepthForMethod(methodDeclaration);

                // 1. Imposta la metrica per il METODO
                analyzedMethod.getMetrics().setNestingDepth(methodNesting);

                // Aggiorna la profondità massima per la classe
                if (methodNesting > maxClassNesting) {
                    maxClassNesting = methodNesting;
                }
            }

            // 2. Imposta la metrica per la CLASSE (usando il massimo trovato)
            analyzedClass.getProcessMetrics().setNestingDepth(maxClassNesting);
        }
    }
    private int calculateNestingDepthForMethod(MethodDeclaration method) {
        if (method == null || method.getBody().isEmpty()) {
            return 0; // Nessun corpo, nessuna profondità
        }

        NestingVisitor visitor = new NestingVisitor();
        // Il metodo accept avvierà la visita dell'AST a partire dal corpo del metodo
        method.getBody().get().accept(visitor, null);

        return visitor.getMaxDepth();
    }


    public void processCognitiveComplexity() {
        for (AnalyzedClass analyzedClass : analyzedClasses) {
            int classCognitiveComplexity = 0; // Complessità totale per la classe

            for (AnalyzedMethod analyzedMethod : analyzedClass.getMethods()) {
                MethodDeclaration methodDeclaration = analyzedMethod.getMethodDeclaration();

                // Calcola la complessità per il singolo metodo
                int methodComplexity = calculateCognitiveComplexityForMethod(methodDeclaration);

                // 1. Imposta la metrica per il METODO
                analyzedMethod.getMetrics().setCognitiveComplexity(methodComplexity);

                // Aggiungi alla somma della classe
                classCognitiveComplexity += methodComplexity;
            }

            // 2. Imposta la metrica per la CLASSE
            analyzedClass.getProcessMetrics().setCognitiveComplexity(classCognitiveComplexity);
        }
    }
    private int calculateCognitiveComplexityForMethod(MethodDeclaration method) {
        if (method == null || method.getBody().isEmpty()) {
            return 0; // Nessun corpo, nessuna complessità
        }

        CognitiveComplexityVisitor visitor = new CognitiveComplexityVisitor();
        method.accept(visitor, null); // Avvia la visita dall'inizio del metodo

        return visitor.getComplexity();
    }
    //private void processChurn() {
    //}


}
