package org.apache.model;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import lombok.Getter;
import lombok.Setter;


import java.util.ArrayList;
import java.util.List;



@Getter
public class AnalyzedMethod {


    private final String signature;
    private final String simpleName;




    private final String body;
    @Setter
    private MethodMetrics metrics;
    @Setter
    private boolean isBuggy;
    private final List<Commit> touchingMethodCommitList;
    private final MethodDeclaration methodDeclaration;


    public AnalyzedMethod( MethodDeclaration methodDeclaration) {

        this.signature = methodDeclaration.getSignature().toString();
        this.simpleName = methodDeclaration.getNameAsString();
        this.body= methodDeclaration.getBody()
                .map(BlockStmt::toString)
                .orElse(""); // Se il metodo non ha corpo (es. in un'interfaccia), restituisce stringa vuota.
        this.methodDeclaration= methodDeclaration;
        this.metrics = new MethodMetrics();
        this.isBuggy = false;
        this.touchingMethodCommitList = new ArrayList<>();
        this.metrics= new MethodMetrics();
    }



}