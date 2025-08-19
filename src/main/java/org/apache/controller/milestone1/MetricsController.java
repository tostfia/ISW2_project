package org.apache.controller.milestone1;




import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.*;
import org.apache.logging.CollectLogger;
import org.apache.model.*;
import org.apache.utilities.metrics.CognitiveComplexityVisitor;
import org.apache.utilities.metrics.NestingVisitor;


import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.logging.Logger;


/**
 * Controller per orchestrare il calcolo delle metriche statiche e storiche.
 * Refactor: metodi isolati e Commit model aggiornato.
 */
public class MetricsController {

    private final List<AnalyzedClass> analyzedClasses;
    private final GitController gitController;
    private final Logger logger = CollectLogger.getInstance().getLogger();
    private final Release release;

    public MetricsController(List<AnalyzedClass> snapshot, GitController gitController,Release release) {
        this.gitController = gitController;
        this.analyzedClasses = snapshot;
        this.release= release;
    }

    /**
     * Orchestrazione del calcolo delle metriche.
     */
    public void processMetrics() {
        logger.info("Inizio calcolo metriche per lo snapshot...");
        LocalDate dateRelease = release.getReleaseDate();
        calculateAge(dateRelease);
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
        if(history.size()>1){
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
            mm.setLOC(am.getBody().lines().count());
            mm.setCycloComplexity(calculateCyclomaticComplexity(md));
            mm.setParameterCount(md.getParameters().size());
            mm.setCognitiveComplexity(calculateCognitiveComplexity(md));
            mm.setNestingDepth(calculateNestingDepth(md));
            mm.setBuggy(ac.isBuggy());

        }
    }
    /**
     * Calcola Fan-in e Fan-out per tutti i metodi nelle classi dello snapshot corrente.
     */
    private void calculateMethodUsageMetrics() {
        logger.fine("Inizio calcolo Fan-in/Fan-out per lo snapshot.");

        // Mappa per memorizzare le chiamate effettuate da ogni metodo
        // Chiave: firma completa del metodo chiamante (es. "com.example.MyClass.myMethod(int,String)")
        // Valore: Set di firme complete dei metodi chiamati (es. "com.example.AnotherClass.calledMethod()")
        Map<String, Set<String>> methodCallsOut = new HashMap<>();

        // Mappa per un accesso rapido a tutti i MethodMetrics per firma completa
        Map<String, MethodMetrics> allMethodMetricsBySignature = new HashMap<>();
        Map<String, AnalyzedMethod> allAnalyzedMethodsBySignature = new HashMap<>();


        // Fase 1: Popolare le chiamate in uscita (Fan-out) e raccogliere tutte le firme
        for (AnalyzedClass jc : analyzedClasses) {
            String className = jc.getClassName(); // Esempio: "path/to/MyClass.java"
            CompilationUnit cu;
            try {
                // Parsing dell'intero contenuto della classe
                cu = StaticJavaParser.parse(jc.getFileContent());
            } catch (Exception e) {
                logger.warning("Errore di parsing JavaParser per classe " + className + ": " + e.getMessage());
                continue;
            }

            for (AnalyzedMethod am : jc.getMethods()) {
                MethodDeclaration methodDecl = am.getMethodDeclaration();
                String methodSignature = am.getSignature(); // Ottieni la firma del metodo (es. "myMethod(int,String)")
                String fullCallerSignature = className + "." + methodSignature;

                allMethodMetricsBySignature.put(fullCallerSignature, am.getMetrics());
                allAnalyzedMethodsBySignature.put(fullCallerSignature, am);

                // Trova tutte le chiamate a metodi all'interno del corpo del metodo corrente
                Set<String> callees = new HashSet<>();
                methodDecl.findAll(MethodCallExpr.class).forEach(methodCall -> {
                    // Per Fan-out, ci interessa solo che faccia una chiamata.
                    // Per identificare il metodo chiamato in modo più preciso (per Fan-in),
                    // avremmo bisogno di un'analisi di risoluzione dei tipi (type solving),
                    // che è molto più complessa e richiede un contesto di progetto completo (classpath, librerie, ecc.)
                    // Qui useremo solo il nome del metodo chiamato.
                    callees.add(methodCall.getNameAsString());
                });
                methodCallsOut.put(fullCallerSignature, callees);
            }
        }

        // Fase 2: Calcolare Fan-out per ogni metodo
        // Ipotizziamo che Fan-out sia il numero di chiamate UNICHE effettuate dal metodo corrente.
        // Se vogliamo le chiamate a metodi *specifici* del progetto, servirebbe type solving.
        // Per semplicità, qui si contano i nomi dei metodi chiamati.
        for (AnalyzedClass jc : analyzedClasses) {
            String className = jc.getClassName();
            for (AnalyzedMethod am : jc.getMethods()) {
                String methodSignature = am.getSignature();
                String fullMethodSignature = className + "." + methodSignature;

                Set<String> callees = methodCallsOut.getOrDefault(fullMethodSignature, Collections.emptySet());
                am.getMetrics().setFanOut(callees.size());
            }
        }

        // Fase 3: Calcolare Fan-in per ogni metodo
        // Fan-in: quante volte un metodo è chiamato da ALTRI metodi nello snapshot
        // Questa è la parte più complessa senza type solving. Faremo un match per nome.
        // Potrebbe contare chiamate a metodi con lo stesso nome ma firme diverse.
        for (AnalyzedClass callerClass : analyzedClasses) {
            CompilationUnit callerCu;
            try {
                callerCu = StaticJavaParser.parse(callerClass.getFileContent());
            } catch (Exception e) {
                logger.warning("Errore di parsing JavaParser per classe chiamante " + callerClass.getClassName() + ": " + e.getMessage());
                continue;
            }

            for (AnalyzedMethod callerMethod : callerClass.getMethods()) {
                MethodDeclaration callerMd = callerMethod.getMethodDeclaration();
                // Assicurati che il corpo esista prima di cercare chiamate
                callerMd.getBody().ifPresent(body -> {
                    body.findAll(MethodCallExpr.class).forEach(methodCall -> {
                        String calleeSimpleName = methodCall.getNameAsString();
                        int calleeArgCount = methodCall.getArguments().size(); // Conta gli argomenti per una migliore precisione

                        // Cerca in tutte le classi dello snapshot un metodo che corrisponda
                        for (AnalyzedClass potentialCalleeClass : analyzedClasses) {
                            for (AnalyzedMethod potentialCalleeMethod : potentialCalleeClass.getMethods()) {
                                MethodDeclaration potentialCalleeMd = potentialCalleeMethod.getMethodDeclaration();
                                // Confronta per nome e numero di parametri (euristica per identificare il metodo)
                                if (potentialCalleeMd.getNameAsString().equals(calleeSimpleName) &&
                                        potentialCalleeMd.getParameters().size() == calleeArgCount) {
                                    potentialCalleeMethod.getMetrics().setFanIn(potentialCalleeMethod.getMetrics().getFanIn() + 1);
                                }
                            }
                        }
                    });
                });
            }
        }
        logger.fine("Calcolo Fan-in/Fan-out completato.");
    }



    private void calculateAge(LocalDate releaseDate) {
        for (AnalyzedClass jc : analyzedClasses) {
            for (AnalyzedMethod am : jc.getMethods()) {
                List<Commit> history = am.getTouchingMethodCommit();

                if (history == null || history.isEmpty()) {
                    am.getMetrics().setAge(0);
                    continue;
                }

                // ordina i commit per data crescente
                history.sort(Comparator.comparing(c -> c.getRevCommit().getCommitterIdent().getWhen()));

                // primo commit che introduce il metodo
                Date firstCommitDate = history.getFirst().getRevCommit().getCommitterIdent().getWhen();

                // converto Date → LocalDate
                LocalDate firstCommitLocalDate = firstCommitDate.toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();

                // calcolo la differenza in giorni
                long diffDays = ChronoUnit.DAYS.between(firstCommitLocalDate, releaseDate);

                am.getMetrics().setAge((int) diffDays);
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


}