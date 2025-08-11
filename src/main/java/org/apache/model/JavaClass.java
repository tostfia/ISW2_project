package org.apache.model;



import com.github.javaparser.ParseProblemException;
import com.github.javaparser.Position;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import lombok.Data;
import lombok.Getter;
import org.apache.logging.CollectLogger;

import java.util.*;

@Getter
public class JavaClass {
    private final String name;
    private final String classBody;
    private String packageName = "";
    private String simpleName = "";
    private final Map<String, String> methods;
    private final Release release;
    private final DataMetrics metrics;
    private final List<Commit> classCommits;
    private final List<Integer> lOCAddedByClass;
    private final List<Integer> lOCRemovedByClass;
    private boolean hasMap = true;

    public JavaClass(String name, String classBody, Release release, boolean update) {
        this.name = name;
        this.classBody = classBody;
        this.methods = new HashMap<>();
        this.release = release;
        /*this.updateMethodsMap(update);
        this.validateMethods();*/
        metrics = new DataMetrics();
        classCommits = new ArrayList<>();
        lOCAddedByClass = new ArrayList<>();
        lOCRemovedByClass = new ArrayList<>();
    }


   /* private void updateMethodsMap(boolean update) {
        CompilationUnit cu;
        try {
            cu = StaticJavaParser.parse(this.classBody);
        }catch (ParseProblemException e){
            CollectLogger.getInstance().getLogger().warning(e.getClass().getSimpleName()+" exception for class="
                    +this.name + "message: " + e.getMessage());
            hasMap = false;
            return;
        }
        cu.getPackageDeclaration().ifPresent(packageDeclaration ->
                this.packageName = packageDeclaration.getNameAsString());
        cu.getTypes().stream()
                .findFirst()
                .map(TypeDeclaration::getNameAsString).ifPresent(className ->
                        this.simpleName = className);

        cu.findAll(MethodDeclaration.class).forEach(methodDeclaration -> {

            String signature = JavaParserUtil.getSignature(methodDeclaration);

            methods.put(signature, JavaParserUtil.getStringBody(methodDeclaration));
            if (update) {
                MethodMetrics methodMetrics = new MethodMetrics();
                methodsMetrics.put(signature, methodMetrics);
                methodMetrics.setParameterCount(JavaParserUtil.computeParameterCount(methodDeclaration));
                methodMetrics.setLinesOfCode(JavaParserUtil.computeLOC(methodDeclaration));
                methodMetrics.setStatementCount(JavaParserUtil.computeStatementCount(methodDeclaration));
                methodMetrics.setCyclomaticComplexity(
                        JavaParserUtil.computeCyclomaticComplexity(methodDeclaration));
                methodMetrics.setNestingDepth(JavaParserUtil.computeNestingDepth(methodDeclaration));
                methodMetrics.setMethodAccessor(methodDeclaration.getAccessSpecifier().asString());
                methodDeclaration.getBody().ifPresent(body ->
                        methodMetrics.setCognitiveComplexity(JavaParserUtil.calculateCognitiveComplexity(body)));
                methodMetrics.setBeginLine(methodDeclaration.getBegin()
                        .orElse(new Position(0, 0)).line);
                methodMetrics.setEndLine(methodDeclaration.getEnd()
                        .orElse(new Position(0, 0)).line);
                methodMetrics.setSimpleName(methodDeclaration.getNameAsString());
                methodMetrics.setAge(this.release.getId());
                methodMetrics.setHalsteadEffort(
                        JavaParserUtil.computeHalsteadEffort(methodDeclaration));
                methodMetrics.setCommentDensity(
                        JavaParserUtil.computeCommentDensity(methodDeclaration));
            }

        });

    }*/



    public void addCommitToClass(Commit commit) {
        this.classCommits.add(commit);
    }


    public void addLOCAddedByClass(Integer lOCAddedByEntry) {
        lOCAddedByClass.add(lOCAddedByEntry);
    }


    public void addLOCRemovedByClass(Integer lOCRemovedByEntry) {
        lOCRemovedByClass.add(lOCRemovedByEntry);
    }


    @Override
    public String toString() {
        return "JavaClass{" +
                "name='" + name + '\'' +
                ", contentOfClass='" + classBody + '\'' +
                ", release=" + release +
                ", metrics=" + metrics +
                ", commitsThatTouchTheClass=" + classCommits +
                ", lOCAddedByClass=" + lOCAddedByClass +
                ", lOCRemovedByClass=" + lOCRemovedByClass +
                '}';
    }

    public String getClassName() {
        return this.packageName + '.' + this.simpleName;
    }
}
