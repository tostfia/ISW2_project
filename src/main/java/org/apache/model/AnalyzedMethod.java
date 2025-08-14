package org.apache.model;



import com.github.javaparser.ast.body.MethodDeclaration;
import lombok.Getter;
import lombok.Setter;

@Getter
public class AnalyzedMethod {
    private final String signature; // La firma completa del metodo
    private final String simpleName;
    private final AnalyzedClass parentClass;// Riferimento alla classe che lo contiene
    private final MethodDeclaration methodDeclaration; // Il metodo stesso, per accedere al corpo e ad altre informazioni
    @Setter
    private DataMetrics dataMetrics;
    @Setter
    private int totalLOC;

    @Setter
    private DataMetrics metrics;
    @Setter
    private boolean isBuggy;


    public AnalyzedMethod(String signature, String simpleName, AnalyzedClass parentClass) {
        this.signature = signature;
        this.simpleName = simpleName;
        this.parentClass = parentClass;
        this.metrics = new DataMetrics();// Inizializziamo subito un oggetto metriche vuoto
        this.isBuggy = false;
        this.methodDeclaration= null; // Inizialmente non abbiamo il metodo
    }

}
