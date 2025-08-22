package org.apache.model;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import lombok.Getter;
import lombok.Setter;


import java.util.ArrayList;
import java.util.List;



@Getter
public class AnalyzedMethod {

    @Setter
    private String signature;
    private String simpleName;
    @Setter
    private int startLine;
    @Setter
    private int endLine;




    private  String body;
    @Setter
    private MethodMetrics metrics;
    @Setter
    private boolean isBuggy;
    @Setter
    private List<Commit> touchingMethodCommit;
    private  MethodDeclaration methodDeclaration;


    public AnalyzedMethod( MethodDeclaration methodDeclaration) {

        this.signature = methodDeclaration.getSignature().toString();
        this.simpleName = methodDeclaration.getNameAsString();
        this.body= methodDeclaration.getBody()
                .map(BlockStmt::toString)
                .orElse(""); // Se il metodo non ha corpo (es. in un'interfaccia), restituisce stringa vuota.
        this.methodDeclaration= methodDeclaration;
        this.metrics = new MethodMetrics();
        this.isBuggy = false;
        this.touchingMethodCommit = new ArrayList<>();
        this.metrics= new MethodMetrics();

    }

    public AnalyzedMethod(String signature, int start, int end) {
        this.startLine = start;
        this.endLine = end;
        this.signature = signature;

    }




}