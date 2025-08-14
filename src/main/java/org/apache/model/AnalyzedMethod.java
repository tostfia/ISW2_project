package org.apache.model;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import lombok.Getter;
import lombok.Setter;


import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
public class AnalyzedMethod {


    private final String signature;
    private final String simpleName;
    private final AnalyzedClass parentClass;
    private final MethodDeclaration methodDeclaration;

    @Setter
    private DataMetrics metrics;
    @Setter
    private boolean isBuggy;
    private final List<Commit> touchingMethodCommitList;


    public AnalyzedMethod(String signature, String simpleName, AnalyzedClass parentClass, MethodDeclaration methodDeclaration) {

        this.signature = Objects.requireNonNull(signature, "La firma del metodo non può essere nulla.");
        this.simpleName = Objects.requireNonNull(simpleName, "Il nome semplice del metodo non può essere nullo.");
        this.parentClass = Objects.requireNonNull(parentClass, "La classe contenitore non può essere nulla.");
        this.methodDeclaration = Objects.requireNonNull(methodDeclaration, "MethodDeclaration non può essere nullo.");


        this.metrics = new DataMetrics();
        this.isBuggy = false;
        this.touchingMethodCommitList = new ArrayList<>();
    }


    public String getBody() {

        return this.methodDeclaration.getBody()
                .map(BlockStmt::toString)
                .orElse(""); // Se il metodo non ha corpo (es. in un'interfaccia), restituisce stringa vuota.
    }


    public void addTouchingCommit(Commit commit) {

        this.touchingMethodCommitList.add(commit);
    }


}