package org.apache.model;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.CollectLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

@Getter
public class AnalyzedClass {


    private final String fileContent;
    // Contenuto del file in questa versione
    @Setter
    private Release release;

    private final String packageName;
    // Nome del package della classe
    private final String FileName;

    // Struttura della Classe
    private final String className;
    @Setter
    private List<AnalyzedMethod> methods;
    @Setter
    private List<Commit> touchingClassCommitList;


    @Setter
    private boolean isBuggy;
    @Setter
    private int totalLOC;
    private final Logger logger = CollectLogger.getInstance().getLogger();

    @Setter
    private ClassMetrics processMetrics;
    private final List<Integer> addedLOCList;
    private final List<Integer> removedLOCList;

    public AnalyzedClass(String className, String fileContent, Release release, String packageName, String fileName) {
        // --- INIZIO BLOCCO DI CONTROLLO E INIZIALIZZAZIONE ---
        this.className = Objects.requireNonNull(className, "Il nome della classe non può essere nullo");
        this.packageName = packageName;
        FileName = fileName;
        if (className.trim().isEmpty()) {
            throw new IllegalArgumentException("Il nome della classe non può essere una stringa vuota.");
        }

        this.processMetrics= new ClassMetrics();
        this.fileContent = fileContent;
        this.release = release;
        this.touchingClassCommitList = new ArrayList<>();
        this.methods = new ArrayList<>();
        this.isBuggy = false;
        this.addedLOCList = new ArrayList<>();
        this.removedLOCList = new ArrayList<>();


        // --- INIZIO BLOCCO DI PARSING SICURO (già presente, ma assicurati sia così) ---
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(fileContent).getResult().orElse(null);

            if (cu != null) {
                cu.findAll(MethodDeclaration.class).forEach(method -> this.methods.add(new AnalyzedMethod(method)));


            } else {
                String errorMsg = String.format("ATTENZIONE: Parsing fallito ma senza eccezioni per la classe %s nella release %s. La lista dei metodi sarà vuota.", this.className, release.getReleaseID());
                logger.warning(errorMsg);
            }
        } catch (ParseProblemException e) {
            String errorMsg = String.format("ATTENZIONE: Errore di sintassi durante il parsing di %s nella release %s. La lista dei metodi sarà vuota. Errore: %s", this.className, release.getReleaseID(), e.getMessage());
            logger.warning(errorMsg);
        } catch (Exception e) {
            String errorMsg = String.format("ATTENZIONE: Errore generico durante il parsing di %s nella release %s. La lista dei metodi sarà vuota.", this.className, release.getReleaseID());
            logger.severe(errorMsg);
        }

    }




}