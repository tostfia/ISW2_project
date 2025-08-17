package org.apache.controller;



import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.stmt.*;
import org.apache.logging.CollectLogger;
import org.apache.model.*;
import org.apache.utilities.metrics.CognitiveComplexityVisitor;
import org.apache.utilities.metrics.NestingVisitor;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.revwalk.RevCommit;


import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Controller per orchestrare il calcolo delle metriche statiche e storiche.
 * Refactor: metodi isolati e Commit model aggiornato.
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
     * Orchestrazione del calcolo delle metriche.
     */
    public void processMetrics() {
        logger.info("Inizio calcolo metriche per lo snapshot...");

        // --- FASE 1: Metriche Statiche ---
        processSizeAndStaticComplexity();
        processParameterCount();

        // --- FASE 2: Raccolta Dati Storici ---
        populateMethodHistory();

        // --- FASE 3: Metriche Storiche ---
        processRevisions();
        processNumberOfAuthors();
        processChangeMetrics();
        processBuggyMethods();

        logger.info("Calcolo metriche completato.");
    }

    // ===============================
    // FASE 2 - Gestione Storia Metodi
    // ===============================

    private void populateMethodHistory() {
        logger.info("Inizio recupero cronologia per ogni metodo (con cache)...");
        Map<String, BlameResult> blameCache = new HashMap<>();

        for (AnalyzedClass analyzedClass : analyzedClasses) {
            String filePath = analyzedClass.getClassName();
            try {
                if (!blameCache.containsKey(filePath)) {
                    logger.info("Eseguo 'git blame' per il file: " + filePath);
                    RevCommit snapshotCommit = analyzedClass.getRelease()
                            .getCommitList().getLast().getRevCommit();
                    BlameResult result = gitController.performBlame(filePath, snapshotCommit);
                    blameCache.put(filePath, result);
                }

                BlameResult blameResult = blameCache.get(filePath);
                if (blameResult == null) {
                    logger.warning("Blame fallito per " + filePath);
                    continue;
                }

                for (AnalyzedMethod method : analyzedClass.getMethods()) {
                    List<Commit> methodCommits = extractCommitsFromBlame(blameResult, method);
                    method.setTouchingMethodCommit(methodCommits);
                }

            } catch (Exception e) {
                logger.severe("Errore durante git blame su " + filePath + ": " + e.getMessage());
                blameCache.put(filePath, null);
            }
        }
        logger.info("Recupero cronologia metodi completato.");
    }

    private List<Commit> extractCommitsFromBlame(BlameResult blameResult, AnalyzedMethod method) {
        MethodDeclaration md = method.getMethodDeclaration();
        if (md.getBegin().isPresent() && md.getEnd().isPresent()) {
            int startLine = md.getBegin().get().line;
            int endLine = md.getEnd().get().line;
            return gitController.extractCommitsFromBlameResult(blameResult, startLine, endLine);
        }
        return new ArrayList<>();
    }



    private void processSizeAndStaticComplexity() {
        analyzedClasses.parallelStream().forEach(analyzedClass -> {
            int totalCC = 0, totalCognitive = 0, maxNesting = 0;

            if (analyzedClass.getFileContent() != null) {
                analyzedClass.getProcessMetrics()
                        .setSize(analyzedClass.getFileContent().split("\\r?\\n").length);
            }

            for (AnalyzedMethod method : analyzedClass.getMethods()) {
                MethodDeclaration md = method.getMethodDeclaration();

                long loc = method.getBody().lines().count();
                method.getMetrics().setLOC(loc);

                int cc = calculateCyclomaticComplexity(md);
                method.getMetrics().setCycloComplexity(cc);
                totalCC += cc;

                int nesting = calculateNestingDepth(md);
                method.getMetrics().setNestingDepth(nesting);
                maxNesting = Math.max(maxNesting, nesting);

                int cognitive = calculateCognitiveComplexity(md);
                method.getMetrics().setCognitiveComplexity(cognitive);
                totalCognitive += cognitive;
            }

            analyzedClass.getProcessMetrics().setCycloComplexity(totalCC);
            analyzedClass.getProcessMetrics().setCognitiveComplexity(totalCognitive);
            analyzedClass.getProcessMetrics().setNestingDepth(maxNesting);
        });
    }

    private void processParameterCount() {
        analyzedClasses.parallelStream().forEach(analyzedClass ->
                analyzedClass.getMethods().forEach(method ->
                        method.getMetrics().setParameterCount(method.getMethodDeclaration().getParameters().size())));
    }

    // ===============================
    // FASE 3 - Metriche Storiche
    // ===============================

    private void processRevisions() {
        for (AnalyzedClass analyzedClass : analyzedClasses) {
            analyzedClass.getProcessMetrics().setNumberOfRevisions(analyzedClass.getTouchingClassCommitList().size());
            for (AnalyzedMethod method : analyzedClass.getMethods()) {
                method.getMetrics().setMethodHistory(method.getTouchingMethodCommit().size());
            }
        }
    }

    private void processNumberOfAuthors() {
        for (AnalyzedClass analyzedClass : analyzedClasses) {
            analyzedClass.getProcessMetrics().setNumAuthors(uniqueAuthors(analyzedClass.getTouchingClassCommitList()));
            for (AnalyzedMethod method : analyzedClass.getMethods()) {
                method.getMetrics().setNumAuthors(uniqueAuthors(method.getTouchingMethodCommit()));
            }
        }
    }

    private void processChangeMetrics() {
        for (AnalyzedClass analyzedClass : analyzedClasses) {
            for (AnalyzedMethod method : analyzedClass.getMethods()) {
                List<Commit> history = method.getTouchingMethodCommit();
                if (history == null || history.size() < 2) {
                    resetChangeMetrics(method);
                    continue;
                }

                List<GitController.MethodChangeStats> changes = gitController.calculateMethodChangeHistory(history, analyzedClass.getClassName(), method.getSignature());
                if (changes.isEmpty()) {
                    resetChangeMetrics(method);
                    continue;
                }

                LOCMetrics added = new LOCMetrics(), removed = new LOCMetrics(), churn = new LOCMetrics();

                for (GitController.MethodChangeStats change : changes) {
                    added.updateMetrics(change.linesAdded());
                    removed.updateMetrics(change.linesDeleted());
                    churn.updateMetrics(change.linesAdded() + change.linesDeleted());
                }

                int revisions = changes.size();
                if (revisions > 0) {
                    added.setAvgVal((double) added.getVal() / revisions);
                    removed.setAvgVal((double) removed.getVal() / revisions);
                    churn.setAvgVal((double) churn.getVal() / revisions);
                }

                MethodMetrics metrics = method.getMetrics();
                metrics.setStmtAddedMetrics(added.getVal(), added.getMaxVal(), added.getAvgVal());
                metrics.setStmtDeletedMetrics(removed.getVal(), removed.getMaxVal(), removed.getAvgVal());
                metrics.setChurnMetrics(churn.getVal(), churn.getMaxVal(), churn.getAvgVal());
            }
        }
    }

    private void processBuggyMethods() {
        Set<Commit> bugCommits = gitController.getBugIntroducingCommitsMap().values().stream().flatMap(List::stream).collect(Collectors.toSet());
        for (AnalyzedClass analyzedClass : analyzedClasses) {
            for (AnalyzedMethod method : analyzedClass.getMethods()) {
                List<Commit> history = method.getTouchingMethodCommit();
                boolean buggy = history != null && history.stream().anyMatch(bugCommits::contains);
                method.getMetrics().setBuggy(buggy);
            }
        }
    }

    // ===============================
    // Helper per Complessità
    // ===============================

    private int calculateCyclomaticComplexity(MethodDeclaration md) {
        int cc = 1;
        cc += md.findAll(IfStmt.class).size();
        cc += md.findAll(ForStmt.class).size();
        cc += md.findAll(ForEachStmt.class).size();
        cc += md.findAll(WhileStmt.class).size();
        cc += md.findAll(CatchClause.class).size();
        cc += md.findAll(SwitchStmt.class).stream().mapToInt(s -> Math.max(0, s.getEntries().size() - 1)).sum();
        cc += md.findAll(BinaryExpr.class, be -> be.getOperator() == BinaryExpr.Operator.AND || be.getOperator() == BinaryExpr.Operator.OR).size();
        return cc;
    }

    private int calculateNestingDepth(MethodDeclaration method) {
        if (method.getBody().isEmpty()) return 0;
        NestingVisitor visitor = new NestingVisitor();
        method.getBody().get().accept(visitor, null);
        return visitor.getMaxDepth();
    }

    private int calculateCognitiveComplexity(MethodDeclaration method) {
        if (method.getBody().isEmpty()) return 0;
        CognitiveComplexityVisitor visitor = new CognitiveComplexityVisitor();
        method.accept(visitor, null);
        return visitor.getComplexity();
    }

    private int uniqueAuthors(List<Commit> commits) {
        if (commits == null || commits.isEmpty()) return 0;
        return (int) commits.stream().map(Commit::getAuthor).distinct().count();
    }

    private void resetChangeMetrics(AnalyzedMethod method) {
        MethodMetrics metrics = method.getMetrics();
        metrics.setStmtAddedMetrics(0, 0, 0.0);
        metrics.setStmtDeletedMetrics(0, 0, 0.0);
        metrics.setChurnMetrics(0, 0, 0.0);
    }
}
