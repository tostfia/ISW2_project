package org.apache.utilities.metrics;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import lombok.Getter;

public  class CognitiveComplexityVisitor extends VoidVisitorAdapter<Void> {

    @Getter
    private int complexity = 0;
    private int nesting = 0;
    private String methodName = "";

    // Incrementa la complessità e il livello di annidamento
    private void increaseComplexityAndNesting(Node node) {
        complexity += 1 + nesting;
        nesting++;
        visitChildren(node); // Visita i figli con il nuovo livello di nesting
        nesting--; // Decrementa quando esci dal nodo
    }

    // Visita i figli di un nodo
    private void visitChildren(Node node) {
        node.getChildNodes().forEach(child -> child.accept(this, null));
    }

    @Override
    public void visit(MethodDeclaration n, Void arg) {
        this.methodName = n.getNameAsString();
        super.visit(n, arg);
    }

    // Strutture di Nesting: +1 + nesting
    @Override
    public void visit(IfStmt n, Void arg) {
        increaseComplexityAndNesting(n.getThenStmt());

        // Gestisce la catena "else if" e l'"else" finale
        if (n.hasElseBlock()) {
            Node elseStmt = n.getElseStmt().get();
            if (elseStmt instanceof IfStmt) {
                // Questo è un "else if", viene gestito come un 'if' normale dal visitor
                elseStmt.accept(this, null);
            } else {
                // Questo è un "else" finale
                complexity++; // +1 per l'else
                nesting++;
                elseStmt.accept(this, null);
                nesting--;
            }
        }
    }

    @Override
    public void visit(ForStmt n, Void arg) { increaseComplexityAndNesting(n); }

    @Override
    public void visit(ForEachStmt n, Void arg) { increaseComplexityAndNesting(n); }


    @Override
    public void visit(WhileStmt n, Void arg) { increaseComplexityAndNesting(n); }

    @Override
    public void visit(DoStmt n, Void arg) { increaseComplexityAndNesting(n); }

    @Override
    public void visit(SwitchStmt n, Void arg) { increaseComplexityAndNesting(n); }

    // Strutture che aumentano la complessità ma non l'annidamento
    @Override
    public void visit(CatchClause n, Void arg) {
        complexity++; // +1 per ogni `catch`
        nesting++;
        n.getBody().accept(this, null); // Visita il corpo del catch
        nesting--;
    }

    @Override
    public void visit(SwitchEntry n, Void arg) {
        if (!n.getLabels().isEmpty()) { // Ignora il 'default' se non ha un case
            complexity++; // +1 per ogni `case`
        }
        super.visit(n, arg);
    }

    @Override
    public void visit(BreakStmt n, Void arg) {
        if (n.getLabel().isPresent()) complexity++; // +1 solo per break/continue con etichetta
    }

    @Override
    public void visit(ContinueStmt n, Void arg) {
        if (n.getLabel().isPresent()) complexity++; // +1
    }

    // Gestione degli operatori logici
    @Override
    public void visit(BinaryExpr n, Void arg) {
        BinaryExpr.Operator op = n.getOperator();
        if (op == BinaryExpr.Operator.AND || op == BinaryExpr.Operator.OR ||!isSameLogicOperator(n.getParentNode().orElse(null), op)) {
            // Incrementa solo se il genitore non è lo stesso tipo di operatore logico
            complexity++;

        }
        super.visit(n, arg);
    }

    private boolean isSameLogicOperator(Node node, BinaryExpr.Operator op) {
        return node instanceof BinaryExpr binaryexpr && binaryexpr.getOperator() == op;
    }

    // Gestione della ricorsione
    @Override
    public void visit(MethodCallExpr n, Void arg) {
        if (n.getNameAsString().equals(this.methodName)) {
            // Assumendo che sia una chiamata ricorsiva diretta
            complexity++;
        }
        super.visit(n, arg);
    }
}
