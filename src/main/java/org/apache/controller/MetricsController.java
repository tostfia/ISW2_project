package org.apache.controller;



import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.stmt.*;
import org.apache.logging.CollectLogger;
import org.apache.model.*;
import org.apache.utilities.metrics.CognitiveComplexityVisitor;
import org.apache.utilities.metrics.NestingVisitor;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Orchestra il calcolo di tutte le metriche per uno snapshot di classi.
 * Questa versione è stata riscritta per garantire che tutte le metriche
 * siano calcolate con la granularità corretta (a livello di metodo)
 * e in un ordine logico che rispetti le dipendenze tra di esse.
 */
public class MetricsController {

    private final List<AnalyzedClass> analyzedClasses;
    private final GitController gitController;
    private final Logger logger = CollectLogger.getInstance().getLogger();

    public MetricsController(List<AnalyzedClass> snapshot, GitController gitController) {
        this.gitController = gitController;
        this.analyzedClasses = snapshot;
    }

    /**
     * Esegue il calcolo di tutte le metriche necessarie per il progetto.
     * L'ordine delle chiamate è fondamentale per il corretto funzionamento.
     */
    public void processMetrics() {
        logger.info("Inizio calcolo metriche per lo snapshot...");

        // --- FASE 1: Metriche Statiche sul Codice (a livello di metodo) ---
        // Queste non hanno dipendenze esterne, solo il codice sorgente.
        processSizeAndStaticComplexity();
        processParameterCount();



        // --- FASE 3: Metriche Storiche (a livello di metodo) ---
        // Queste dipendono dai dati raccolti nella Fase 2.
        processRevisions();
        processNumberOfAuthors();
        processChangeMetrics(); // Sostituisce il vecchio processLOC e calcola Churn, StmtAdded, etc.
        processBuggyMethods();

        logger.info("Calcolo metriche completato.");
    }

    /**
     * Calcola metriche di complessità statica come LOC, Complessità Ciclomatica,
     * Profondità di Annidamento e Complessità Cognitiva per ogni metodo.
     */
    private void processSizeAndStaticComplexity() {
        for (AnalyzedClass analyzedClass : analyzedClasses) {
            int totalClassCC = 0;
            int totalClassCognitive = 0;
            int maxClassNesting = 0;

            // Imposta la dimensione totale della classe
            if (analyzedClass.getFileContent() != null) {
                analyzedClass.getProcessMetrics().setSize(analyzedClass.getFileContent().split("\r\n|\r|\n").length);
            }

            for (AnalyzedMethod method : analyzedClass.getMethods()) {
                MethodDeclaration md = method.getMethodDeclaration();

                // LOC (Linee di Codice del corpo del metodo)
                long loc = method.getBody().lines().count();
                method.getMetrics().setLOC(loc);

                // Complessità Ciclomatica
                int cc = calculateCyclomaticComplexityForMethod(md);
                method.getMetrics().setCycloComplexity(cc);
                totalClassCC += cc;

                // Profondità di Annidamento
                int nesting = calculateNestingDepthForMethod(md);
                method.getMetrics().setNestingDepth(nesting);
                if (nesting > maxClassNesting) maxClassNesting = nesting;

                // Complessità Cognitiva
                int cognitive = calculateCognitiveComplexityForMethod(md);
                method.getMetrics().setCognitiveComplexity(cognitive);
                totalClassCognitive += cognitive;
            }

            // Imposta le metriche aggregate a livello di classe
            analyzedClass.getProcessMetrics().setCycloComplexity(totalClassCC);
            analyzedClass.getProcessMetrics().setCognitiveComplexity(totalClassCognitive);
            analyzedClass.getProcessMetrics().setNestingDepth(maxClassNesting);
        }
    }

    /**
     * Calcola il numero di parametri per ogni metodo (metrica "actionable").
     */
    private void processParameterCount() {
        for (AnalyzedClass analyzedClass : analyzedClasses) {
            for (AnalyzedMethod method : analyzedClass.getMethods()) {
                int paramCount = method.getMethodDeclaration().getParameters().size();
                method.getMetrics().setParameterCount(paramCount);
            }
        }
    }




    /**
     * Calcola il numero di revisioni sia per la classe che per ogni metodo.
     * Deve essere eseguito dopo `processMethodHistory`.
     */
    private void processRevisions() {
        for (AnalyzedClass analyzedClass : analyzedClasses) {
            analyzedClass.getProcessMetrics().setNumberOfRevisions(analyzedClass.getTouchingClassCommitList().size());
            for (AnalyzedMethod method : analyzedClass.getMethods()) {
                int methodRevisions = method.getTouchingMethodCommit().size();
                method.getMetrics().setMethodHistory(methodRevisions); // methodHistories
            }
        }
    }

    /**
     * Calcola il numero di autori unici per la classe e per ogni metodo.
     * Deve essere eseguito dopo `processMethodHistory`.
     */
    private void processNumberOfAuthors() {
        for (AnalyzedClass analyzedClass : analyzedClasses) {
            analyzedClass.getProcessMetrics().setNumAuthors(calculateUniqueAuthors(analyzedClass.getTouchingClassCommitList()));
            for (AnalyzedMethod method : analyzedClass.getMethods()) {
                method.getMetrics().setNumAuthors(calculateUniqueAuthors(method.getTouchingMethodCommit())); // authors
            }
        }
    }

    /**
     * Calcola tutte le metriche di cambiamento (Churn, StmtAdded, StmtDeleted)
     * a livello di metodo, inclusi sum, max e avg.
     * Deve essere eseguito dopo `processMethodHistory`.
     */
    private void processChangeMetrics() {
        for (AnalyzedClass analyzedClass : analyzedClasses) {
            for (AnalyzedMethod method : analyzedClass.getMethods()) {
                List<Commit> methodHistory = method.getTouchingMethodCommit();
                if (methodHistory == null || methodHistory.size() < 2) {
                    setEmptyChangeMetrics(method);
                    continue;
                }

                // Chiedi al GitController le statistiche di modifica per questo metodo
                List<GitController.MethodChangeStats> changes = gitController.calculateMethodChangeHistory(
                        methodHistory,
                        analyzedClass.getClassName(),
                        method.getSignature()
                );

                if (changes.isEmpty()) {
                    setEmptyChangeMetrics(method);
                    continue;
                }

                // Usa una classe helper per aggregare i risultati
                LOCMetrics addedMetrics = new LOCMetrics();
                LOCMetrics removedMetrics = new LOCMetrics();
                LOCMetrics churnMetrics = new LOCMetrics();

                for (GitController.MethodChangeStats change : changes) {
                    addedMetrics.updateMetrics(change.linesAdded());
                    removedMetrics.updateMetrics(change.linesDeleted());
                    // Corretta la formula del churn
                    churnMetrics.updateMetrics(change.linesAdded() + change.linesDeleted());
                }

                int nRevisions = changes.size();
                addedMetrics.setAvgVal((double) addedMetrics.getVal() / nRevisions);
                removedMetrics.setAvgVal((double) removedMetrics.getVal() / nRevisions);
                churnMetrics.setAvgVal((double) churnMetrics.getVal() / nRevisions);

                // Imposta le metriche nell'oggetto MethodMetrics
                MethodMetrics metrics = method.getMetrics();
                metrics.setStmtAddedMetrics(addedMetrics.getVal(), addedMetrics.getMaxVal(), addedMetrics.getAvgVal());
                metrics.setStmtDeletedMetrics(removedMetrics.getVal(), removedMetrics.getMaxVal(), removedMetrics.getAvgVal());
                metrics.setChurnMetrics(churnMetrics.getVal(), churnMetrics.getMaxVal(), churnMetrics.getAvgVal());
            }
        }
    }

    /**
     * Etichetta i metodi come "buggy" se sono stati toccati da un commit che ha introdotto un bug (SZZ).
     * Deve essere eseguito dopo `processMethodHistory`.
     */
    private void processBuggyMethods() {
        Set<Commit> bugIntroducingCommits = gitController.getBugIntroducingCommitsMap().values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toSet());

        for (AnalyzedClass analyzedClass : analyzedClasses) {
            for (AnalyzedMethod method : analyzedClass.getMethods()) {
                List<Commit> methodHistory = method.getTouchingMethodCommit();
                if (methodHistory == null || methodHistory.isEmpty()) {
                    method.getMetrics().setBuggy(false);
                    continue;
                }

                boolean isMethodBuggy = methodHistory.stream().anyMatch(bugIntroducingCommits::contains);
                method.getMetrics().setBuggy(isMethodBuggy);
            }
        }
    }

    // --- METODI HELPER ---

    private int calculateUniqueAuthors(List<Commit> commits) {
        if (commits == null || commits.isEmpty()) return 0;
        return new HashSet<>(commits.stream()
                .map(c -> c.getRevCommit().getAuthorIdent().getName())
                .toList()).size();
    }

    private int calculateCyclomaticComplexityForMethod(MethodDeclaration md) {
        int cc = 1;
        cc += md.findAll(IfStmt.class).size();
        cc += md.findAll(ForStmt.class).size();
        cc += md.findAll(ForEachStmt.class).size();
        cc += md.findAll(WhileStmt.class).size();
        cc += md.findAll(CatchClause.class).size();
        cc += md.findAll(SwitchStmt.class).stream().mapToInt(s -> !s.getEntries().isEmpty() ? s.getEntries().size() - 1 : 0).sum();
        cc += md.findAll(BinaryExpr.class, be -> be.getOperator() == BinaryExpr.Operator.AND || be.getOperator() == BinaryExpr.Operator.OR).size();
        return cc;
    }

    private int calculateNestingDepthForMethod(MethodDeclaration method) {
        if (method.getBody().isEmpty()) return 0;
        NestingVisitor visitor = new NestingVisitor();
        method.getBody().get().accept(visitor, null);
        return visitor.getMaxDepth();
    }

    private int calculateCognitiveComplexityForMethod(MethodDeclaration method) {
        if (method.getBody().isEmpty()) return 0;
        CognitiveComplexityVisitor visitor = new CognitiveComplexityVisitor();
        method.accept(visitor, null);
        return visitor.getComplexity();
    }

    private void setEmptyChangeMetrics(AnalyzedMethod method) {
        MethodMetrics metrics = method.getMetrics();
        metrics.setStmtAddedMetrics(0, 0, 0.0);
        metrics.setStmtDeletedMetrics(0, 0, 0.0);
        metrics.setChurnMetrics(0, 0, 0.0);
    }

    public void processClassLevelHistoricalMetrics() {
    }

    public void processMethodLevelStaticMetrics() {
    }
}


