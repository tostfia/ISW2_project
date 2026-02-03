package org.apache.controller.milestone1;





import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.stmt.*;
import org.apache.logging.Printer;
import org.apache.model.*;
import org.apache.utilities.metrics.CognitiveComplexityVisitor;
import org.apache.utilities.metrics.NestingVisitor;


import java.util.*;


/**
 * Controller per orchestrare il calcolo delle metriche statiche e storiche.
 * Refactor: metodi isolati e Commit model aggiornato.
 */
public class MetricsController {

    private final List<AnalyzedClass> analyzedClasses;
    private final GitController gitController;



    public MetricsController(List<AnalyzedClass> snapshot, GitController gitController) {
        this.gitController = gitController;
        this.analyzedClasses = snapshot;

    }

    /**
     * Orchestrazione del calcolo delle metriche.
     */
    public void processMetrics() {
        Printer.printBlue("Inizio calcolo metriche per lo snapshot...\n");





        for (AnalyzedClass analyzedClass : analyzedClasses) {
            processClassLevelMetrics(analyzedClass);
            processMethodLevelMetrics(analyzedClass);
        }
    }

    private void processClassLevelMetrics(AnalyzedClass ac) {
        ClassMetrics cm= ac.getProcessMetrics();
        List<Commit> history = ac.getTouchingClassCommitList();
        //Calcolo size, revisoni autori per le classi
        cm.setSize(ac.getFileContent()!=null? ac.getFileContent().split("\\r?\\n").length : 0);
        cm.setNumberOfRevisions(history.size());
        cm.setNumAuthors((int) history.stream().map(Commit::getAuthor).distinct().count());
        if(gitController!=null && history.size()>1){
            List<GitController.ClassChangeStats> changes = gitController.calculateClassChangeHistory(history, ac.getClassName());
            int totalChurn=changes.stream().mapToInt(c-> c.linesAdded() + c.linesDeleted()).sum();
            double avgChurn=changes.stream().mapToInt(GitController.ClassChangeStats::linesAdded).average().orElse(0.0);
            int maxChurn=changes.stream().mapToInt(GitController.ClassChangeStats::linesAdded).max().orElse(0);
            cm.setChurnMetrics(totalChurn,maxChurn,avgChurn);
        } else {
            cm.setChurnMetrics(0, 0, 0.0); // Se non ci sono revisioni, impostiamo a zero
        }

    }
    private void processMethodLevelMetrics(AnalyzedClass ac) {
        for (AnalyzedMethod am : ac.getMethods()) {
            MethodDeclaration md = am.getMethodDeclaration();
            MethodMetrics mm = am.getMetrics();
            mm.setLoc(am.getBody().lines().count());
            mm.setCycloComplexity(calculateCyclomaticComplexity(md));
            mm.setParameterCount(md.getParameters().size());
            mm.setCognitiveComplexity(calculateCognitiveComplexity(md));
            mm.setNestingDepth(calculateNestingDepth(md));


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


}