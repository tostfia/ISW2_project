package org.apache.controller;

import com.github.javaparser.ast.body.MethodDeclaration;
import lombok.Setter;
import org.apache.model.AnalyzedClass;

import java.util.List;
import java.util.Map;

@Setter
public class MetricsController {
    private Map<String, List<AnalyzedClass>> analysisResults;

    public MetricsController(GitController git){}
    public static String getStringBody(MethodDeclaration methodDeclaration) {
        return methodDeclaration.getBody().map(Object::toString).orElse("{}");
    }

    public void start() {
    }

    public void generateDataset(String targetName) {
    }


    public void calculateMetrics(AnalyzedClass classSnapshot) {
    }

    public void calculateStatics(AnalyzedClass classSnapshot) {
    }
}
