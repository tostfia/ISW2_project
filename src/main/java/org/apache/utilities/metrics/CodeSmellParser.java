package org.apache.utilities.metrics;

import lombok.Setter;
import org.apache.logging.CollectLogger;
import org.apache.model.AnalyzedClass;
import org.apache.model.AnalyzedMethod;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.List;
import java.util.logging.Logger;

public class CodeSmellParser {

    // Questo campo è cruciale per costruire il percorso del report
    @Setter
    private static String repoRootPath; // Deve essere impostato esternamente (dal GitController o main)
    private static final Logger logger = CollectLogger.getInstance().getLogger();

    /**
     * Estrae i code smell dal report PMD XML per una specifica release
     * e li associa ai rispettivi AnalyzedMethod.
     *
     * @param analyzedClasses La lista di AnalyzedClass (già popolata con i metodi) per la release corrente.
     * @param targetName Il nome del progetto (es. "BOOKKEEPER").
     * @param releaseId L'ID della release per cui si sta analizzando il report PMD.
     */

    public static void extractCodeSmell(List<AnalyzedClass> analyzedClasses, String targetName, String releaseId) {
        logger.info("Inizio estrazione code smell dal report PMD per release " + releaseId);



        String pmdReportPath = "pmd_analysis" + File.separator + targetName + File.separator + releaseId + ".xml";
        File pmdReportFile = new File(pmdReportPath);

        if (!pmdReportFile.exists()) {
            logger.warning("Report PMD non trovato per release " + releaseId + " al percorso: " + pmdReportPath + ". Non verranno associati code smell.");
            return;
        }

        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(pmdReportFile);
            doc.getDocumentElement().normalize();

            NodeList violationList = doc.getElementsByTagName("violation");
            associateViolationsToMethods(violationList, analyzedClasses);

            logger.info("Estrazione code smell completata per release " + releaseId + ". Trovate " + violationList.getLength() + " violazioni.");
        } catch (Exception e) {
            logger.severe("Errore durante parsing o associazione del report PMD per release " + releaseId + " al percorso " + pmdReportPath + ": " + e.getMessage());
        }
    }



    private static void associateViolationsToMethods(NodeList violationList, List<AnalyzedClass> analyzedClasses) {
        for (int i = 0; i < violationList.getLength(); i++) {
            Element violationElement = (Element) violationList.item(i);
            String className = violationElement.getAttribute("class");
            String methodName = violationElement.getAttribute("method");

            AnalyzedClass matchingClass = findClassByName(analyzedClasses, className);
            if (matchingClass != null) {
                AnalyzedMethod matchingMethod = findMethodByName(matchingClass, methodName);
                if (matchingMethod != null) {
                    matchingMethod.getMetrics().incrementCodeSmellCount();
                    logger.finest("Code smell associato a metodo: " + className + "." + methodName);
                } else {
                    logger.fine("Metodo " + methodName + " non trovato nella classe " + className);
                }
            } else {
                logger.fine("Classe " + className + " non trovata tra le classi analizzate.");
            }
        }
    }

    private static AnalyzedClass findClassByName(List<AnalyzedClass> analyzedClasses, String className) {
        for (AnalyzedClass ac : analyzedClasses) {
            String acClassName = ac.getFileName().replace(".java", "");
            if (acClassName.equals(className)) {
                return ac;
            }
        }
        return null;
    }

    private static AnalyzedMethod findMethodByName(AnalyzedClass analyzedClass, String methodName) {
        for (AnalyzedMethod am : analyzedClass.getMethods()) {
            if (am.getMethodDeclaration().getNameAsString().equals(methodName)) {
                return am;
            }
        }
        return null;
    }
}