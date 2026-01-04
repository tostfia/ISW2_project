package org.apache.controller.milestone1;




import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
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


        // Prima di processare le singole classi/metodi, calcoliamo Fan-in/Fan-out
        // che richiede una visione d'insieme dello snapshot corrente.
        calculateMethodUsageMetrics(); // Nuovo metodo per Fan-in/Fan-out


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
    private void calculateMethodUsageMetrics() {
        Printer.print("Inizio calcolo Fan-in/Fan-out per lo snapshot.\n");

        // Mappa per memorizzare le chiamate effettuate da ogni metodo chiamante
        // Chiave: firma completa del metodo chiamante (es. "com.example.MyClass.myMethod(int,String)")
        // Valore: Set di nomi semplici dei metodi chiamati (es. "calledMethod")
        // Questo è per il calcolo Fan-out.
        Map<String, Set<String>> methodCallsOutSimpleName = new HashMap<>();

        // Mappa per un accesso rapido a tutti gli AnalyzedMethod per il calcolo del Fan-in
        // Chiave: "nomeMetodo_numeroParametri" (euristica)
        // Valore: Lista di AnalyzedMethod che corrispondono a quella firma euristica.
        // Necessaria una lista perché più metodi possono avere lo stesso nome e numero di parametri
        // in classi diverse o per overloading (anche se l'overloading per solo numero di parametri è raro).
        Map<String, List<AnalyzedMethod>> methodsByHeuristicSignature = new HashMap<>();


        // --- Fase 1: Popolare le chiamate in uscita (Fan-out) e preparare la mappa per il Fan-in ---
        for (AnalyzedClass analyzedClass : analyzedClasses) {
            String className = analyzedClass.getClassName();
            // Parsing sicuro della classe per evitare errori di sintassi
            try {
                 StaticJavaParser.parse(analyzedClass.getFileContent());
            } catch (Exception e) {
                Printer.printYellow("Errore di parsing JavaParser per classe " + className + ": " + e.getMessage());
                continue;
            }

            for (AnalyzedMethod analyzedMethod : analyzedClass.getMethods()) {
                MethodDeclaration methodDecl = analyzedMethod.getMethodDeclaration();
                String methodSignature = analyzedMethod.getSignature();
                String fullCallerSignature = className + "." + methodSignature;

                // Popola la mappa per il Fan-in
                // Prepara la chiave euristica: nome del metodo + numero di parametri
                String heuristicSignature = methodDecl.getNameAsString() + "_" + methodDecl.getParameters().size();
                methodsByHeuristicSignature.computeIfAbsent(heuristicSignature, k -> new ArrayList<>()).add(analyzedMethod);


                // Raccogli le chiamate per il Fan-out
                Set<String> calleesSimpleNames = new HashSet<>();
                methodDecl.findAll(MethodCallExpr.class).forEach(methodCall -> {
                    // Per Fan-out, contiamo i nomi dei metodi distinti.
                    calleesSimpleNames.add(methodCall.getNameAsString());
                });
                methodCallsOutSimpleName.put(fullCallerSignature, calleesSimpleNames);
            }
        }

        // --- Fase 2: Calcolare Fan-out per ogni metodo ---
        for (AnalyzedClass analyzedClass : analyzedClasses) {
            String className = analyzedClass.getClassName();
            for (AnalyzedMethod analyzedMethod : analyzedClass.getMethods()) {
                String methodSignature = analyzedMethod.getSignature();
                String fullMethodSignature = className + "." + methodSignature;

                Set<String> callees = methodCallsOutSimpleName.getOrDefault(fullMethodSignature, Collections.emptySet());
                analyzedMethod.getMetrics().setFanOut(callees.size());
            }
        }

        // --- Fase 3: Calcolare Fan-in per ogni metodo ---
        // Resetta Fan-in a 0 per tutti i metodi prima di ricalcolare,
        // per assicurare un conteggio corretto ad ogni esecuzione.
        for (AnalyzedClass ac : analyzedClasses) {
            for (AnalyzedMethod am : ac.getMethods()) {
                am.getMetrics().setFanIn(0);
            }
        }

        for (AnalyzedClass callerClass : analyzedClasses) {

            try {
                StaticJavaParser.parse(callerClass.getFileContent());
            } catch (Exception e) {
                Printer.errorPrint("Errore di parsing JavaParser per classe chiamante " + callerClass.getClassName() + ": " + e.getMessage());
                continue;
            }

            for (AnalyzedMethod callerMethod : callerClass.getMethods()) {
                MethodDeclaration callerMd = callerMethod.getMethodDeclaration();
                callerMd.getBody().ifPresent(body ->
                        body.findAll(MethodCallExpr.class).forEach(methodCall -> {
                            String calleeSimpleName = methodCall.getNameAsString();
                            int calleeArgCount = methodCall.getArguments().size();
                            String heuristicSignature = calleeSimpleName + "_" + calleeArgCount;
                            List<AnalyzedMethod> potentialCallees = methodsByHeuristicSignature.getOrDefault(heuristicSignature, Collections.emptyList());
                            potentialCallees.forEach(potentialCalleeMethod ->
                                    potentialCalleeMethod.getMetrics().setFanIn(potentialCalleeMethod.getMetrics().getFanIn() + 1)
                            );
                        })
                );
            }
        }
        Printer.print("Calcolo Fan-in/Fan-out completato.\n");
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