
package org.apache.utilities.metrics;

import lombok.Setter;
import org.apache.logging.Printer;
import org.apache.model.AnalyzedClass;
import org.apache.model.AnalyzedMethod;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CodeSmellParser {


    private CodeSmellParser() {
        throw new AssertionError("Utility class - non istanziare");
    }

    @Setter
    private static String repoRootPath;

    public static void extractCodeSmell(List<AnalyzedClass> analyzedClasses, String targetName, String releaseId) {
        Printer.print("Inizio estrazione code smell dal report PMD per release " + releaseId+ "...\n");
        Map<String, AnalyzedMethod> methodsByFqmn = new HashMap<>();
        for (AnalyzedClass ac : analyzedClasses) {
            String fqcn = ac.getPackageName() + "." + ac.getFileName().replace(".java", "");
            for (AnalyzedMethod am : ac.getMethods()) {
                String methodName = am.getMethodDeclaration().getNameAsString();
                String fqmn = fqcn + "." + methodName;
                methodsByFqmn.put(fqmn, am);
            }
        }
        Printer.print("Mappa di " + methodsByFqmn.size() + " metodi FQMN creata per lookup.\n");

        String pmdReportPath = "pmd_analysis" + File.separator + targetName + File.separator + releaseId + ".xml";
        File pmdReportFile = new File(pmdReportPath);

        if (!pmdReportFile.exists()) {
            Printer.printYellow("Report PMD non trovato per release " + releaseId + " al percorso: " + pmdReportPath + ". Non verranno associati code smell.");
            return;
        }

        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(pmdReportFile);
            doc.getDocumentElement().normalize();

            NodeList violationList = doc.getElementsByTagName("violation");

            for (int i = 0; i < violationList.getLength(); i++) {
                Element violationElement = (Element) violationList.item(i);
                processViolation(violationElement, analyzedClasses);
            }
            Printer.printGreen("Estrazione code smell completata per release " + releaseId + ". Trovate " + violationList.getLength() + " violazioni.\n");

        } catch (Exception e) {
            Printer.errorPrint("Errore durante parsing o associazione del report PMD per release " + releaseId + " al percorso " + pmdReportPath + ": " + e.getMessage());
        }
    }

    private static void processViolation(Element violationElement, List<AnalyzedClass> analyzedClasses) {
        String className = violationElement.getAttribute("class");
        String methodName = violationElement.getAttribute("method");

        AnalyzedClass matchingClass = null;
        for (AnalyzedClass ac : analyzedClasses) {
            String acClassName = ac.getFileName().replace(".java", "");
            if (acClassName.equals(className)) {
                matchingClass = ac;
                break;
            }
        }

        if (matchingClass != null) {
            AnalyzedMethod matchingMethod = null;
            for (AnalyzedMethod am : matchingClass.getMethods()) {
                if (am.getMethodDeclaration().getNameAsString().equals(methodName)) {
                    matchingMethod = am;
                    break;
                }
            }
            if (matchingMethod != null) {
                matchingMethod.getMetrics().incrementCodeSmellCount();
                Printer.print("Code smell associato a metodo: " + className + "." + methodName + "\n");
            } else {
                Printer.println("Metodo " + methodName + " non trovato nella classe " + className);
            }
        } else {
            Printer.println("Classe " + className + " non trovata tra le classi già analizzate.\n");
        }
    }
}