package org.apache.utilities.metrics;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import lombok.Getter;

public  class NestingVisitor extends VoidVisitorAdapter<Void> {
    private int currentDepth = 0;
    @Getter
    private int maxDepth = 0;

    // Metodo generico per gestire l'incremento e il decremento della profondità
    private void visitNestingNode(Node n, Void arg) {
        currentDepth++;
        // Aggiorna la profondità massima se quella corrente è maggiore
        maxDepth = Math.max(maxDepth, currentDepth);

        // Continua a visitare i figli di questo nodo
        super.visit((AnnotationDeclaration) n, arg);

        currentDepth--; // Cruciale: decrementa la profondità quando esci dal nodo
    }

    // Esegui l'override dei metodi visit per ogni tipo di nodo che crea annidamento
    @Override
    public void visit(IfStmt n, Void arg) {
        visitNestingNode(n, arg);
    }

    @Override
    public void visit(ForStmt n, Void arg) {
        visitNestingNode(n, arg);
    }

    @Override
    public void visit(ForEachStmt n, Void arg) {
        visitNestingNode(n, arg);
    }

    @Override
    public void visit(WhileStmt n, Void arg) {
        visitNestingNode(n, arg);
    }

    @Override
    public void visit(DoStmt n, Void arg) {
        visitNestingNode(n, arg);
    }

    @Override
    public void visit(SwitchStmt n, Void arg) {
        // Nota: alcuni considerano anche `case` un livello di nesting.
        // Qui contiamo solo lo `switch` per coerenza con il tuo codice originale.
        visitNestingNode(n, arg);
    }

    @Override
    public void visit(TryStmt n, Void arg) {
        visitNestingNode(n, arg);
    }

    @Override
    public void visit(CatchClause n, Void arg) {
        visitNestingNode(n, arg);
    }

    // Potresti voler considerare anche le lambda come annidamento
    @Override
    public void visit(LambdaExpr n, Void arg) {
        // Se il corpo della lambda è un blocco di istruzioni, consideralo annidato
        if (n.getBody() instanceof BlockStmt) {
            visitNestingNode(n, arg);
        } else {
            // Altrimenti, visita solo le espressioni interne senza aumentare il livello
            super.visit(n, arg);
        }
    }
}
