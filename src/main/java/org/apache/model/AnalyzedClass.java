package org.apache.model;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import lombok.Getter;
import lombok.Setter;
import org.apache.controller.MetricsController;

import java.util.ArrayList;
import java.util.List;

@Getter
public class AnalyzedClass {

    // Campi Identificativi
    private final String filePath;      // Percorso del file
    private final String fileContent;
    // Contenuto del file in questa versione
    @Setter
    private Release release;

    // Struttura della Classe
    private  String className;
    private  String packageName;
    private final List<AnalyzedMethod> methods;
    private final List<Commit> touchingClassCommitList;
    private final List<Integer> addedLOCList;
    private final List<Integer> removedLOCList;

    @Setter
    private boolean isBuggy;
    @Setter
    private int totalLOC;

    // Metriche di Processo (calcolate dal GitController)
    @Setter
    private final DataMetrics processMetrics;

    public AnalyzedClass(String filePath, String fileContent, Release release) {
        this.filePath = filePath;
        this.fileContent = fileContent;
        this.release = release;
        this.methods = new ArrayList<>();
        this.processMetrics = new DataMetrics(); // Per Churn, Authors, etc.
        this.isBuggy = false; // Inizialmente non è buggy
        this.touchingClassCommitList = new ArrayList<>();
        this.addedLOCList = new ArrayList<>(); // Lista per le linee aggiunte
        this.removedLOCList = new ArrayList<>(); // Lista per le linee rimosse

        // Esegui il parsing iniziale per identificare la struttura
        parseStructure();
    }

    /**
     * Usa JavaParser per estrarre il nome del package, della classe
     * e per creare gli oggetti AnalyzedMethod (ancora senza metriche).
     */
    private void parseStructure() {
        CompilationUnit cu;
        try {
            cu = StaticJavaParser.parse(this.fileContent);
        } catch (Exception e) {
            // Logga l'errore di parsing ma non bloccare
            return;
        }

        // Estrai il nome del package
        this.packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getName().asString())
                .orElse("default");

        // Estrai il nome della classe principale
        this.className = cu.getPrimaryTypeName().orElse(extractClassNameFromPath());

        // Per ogni metodo, crea un oggetto AnalyzedMethod
        cu.findAll(MethodDeclaration.class).forEach(md -> {
            String signature = MetricsController.getStringBody(md);
            String simpleName = md.getNameAsString();
            this.methods.add(new AnalyzedMethod(signature, simpleName, this));
        });
    }

    private String extractClassNameFromPath() {
        String[] parts = this.filePath.split("/");
        String fileName = parts[parts.length - 1];
        return fileName.replace(".java", "");
    }


    public void addTouchingClassCommit(Commit commit) {
        this.touchingClassCommitList.add(commit);
    }

    public void addLoc(int addedLines) {
        addedLOCList.add(addedLines);
    }

    public void removeLoc(int deletedLines) {
        removedLOCList.add(deletedLines);
    }
}