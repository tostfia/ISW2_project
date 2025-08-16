package org.apache.utilities.metrics;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import lombok.Getter;

/**
 * Visitor specializzato per calcolare la profondità di annidamento.
 * Questa versione è corretta e non causa errori di compilazione o ClassCastException.
 */
@Getter
public class NestingVisitor extends VoidVisitorAdapter<Void> {
    private int currentDepth = 0;
    private int maxDepth = 0;



    // --- Logica corretta inserita in ogni metodo di visita ---

    @Override
    public void visit(IfStmt n, Void arg) {
        currentDepth++;
        maxDepth = Math.max(maxDepth, currentDepth);
        super.visit(n, arg); // Visita i figli
        currentDepth--;
    }

    @Override
    public void visit(ForStmt n, Void arg) {
        currentDepth++;
        maxDepth = Math.max(maxDepth, currentDepth);
        super.visit(n, arg);
        currentDepth--;
    }

    @Override
    public void visit(ForEachStmt n, Void arg) {
        currentDepth++;
        maxDepth = Math.max(maxDepth, currentDepth);
        super.visit(n, arg);
        currentDepth--;
    }

    @Override
    public void visit(WhileStmt n, Void arg) {
        currentDepth++;
        maxDepth = Math.max(maxDepth, currentDepth);
        super.visit(n, arg);
        currentDepth--;
    }

    @Override
    public void visit(DoStmt n, Void arg) {
        currentDepth++;
        maxDepth = Math.max(maxDepth, currentDepth);
        super.visit(n, arg);
        currentDepth--;
    }

    @Override
    public void visit(SwitchStmt n, Void arg) {
        currentDepth++;
        maxDepth = Math.max(maxDepth, currentDepth);
        super.visit(n, arg);
        currentDepth--;
    }

    @Override
    public void visit(TryStmt n, Void arg) {
        currentDepth++;
        maxDepth = Math.max(maxDepth, currentDepth);
        super.visit(n, arg);
        currentDepth--;
    }

    @Override
    public void visit(CatchClause n, Void arg) {
        currentDepth++;
        maxDepth = Math.max(maxDepth, currentDepth);
        super.visit(n, arg);
        currentDepth--;
    }

    @Override
    public void visit(LambdaExpr n, Void arg) {
        if (n.getBody() instanceof BlockStmt) {
            currentDepth++;
            maxDepth = Math.max(maxDepth, currentDepth);
            super.visit(n, arg);
            currentDepth--;
        } else {
            super.visit(n, arg);
        }
    }
}
