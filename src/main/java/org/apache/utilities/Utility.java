package org.apache.utilities;

import com.github.javaparser.ast.body.MethodDeclaration;

public class Utility {

    public static String getStringBody(MethodDeclaration md) {
        return md.getBody()
                .map(Object::toString)
                .orElse(""); // Restituisce una stringa vuota se il corpo è vuoto
    }
}
