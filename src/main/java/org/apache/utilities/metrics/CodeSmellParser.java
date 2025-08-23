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

    // Questo campo è cruciale per costruire il percorso del report
    @Setter
    private static String repoRootPath; // Deve essere impostato esternamente (dal GitController o main)


    /**
     * Estrae i code smell dal report PMD XML per una specifica release
     * e li associa ai rispettivi AnalyzedMethod.
     *
     * @param analyzedClasses La lista di AnalyzedClass (già popolata con i metodi) per la release corrente.
     * @param targetName Il nome del progetto (es. "BOOKKEEPER").
     * @param releaseId L'ID della release per cui si sta analizzando il report PMD.
     */
    public static void extractCodeSmell(List<AnalyzedClass> analyzedClasses, String targetName, String releaseId) {
        Printer.print("Inizio estrazione code smell dal report PMD per release " + releaseId+ "...\n");

        // 1. Costruisci una mappa per un lookup efficiente degli AnalyzedMethod
        // La chiave sarà il Fully Qualified Method Name (FQMN): "package.Class.methodName"
        Map<String, AnalyzedMethod> methodsByFqmn = new HashMap<>();
        for (AnalyzedClass ac : analyzedClasses) {
            // Deriva il Fully Qualified Class Name (FQCN) dal percorso del file.
            // Assumiamo che ac.getPackageName() contenga il package corretto
            // e ac.getFileName() sia "NomeClasse.java".
            String fqcn = ac.getPackageName() + "." + ac.getFileName().replace(".java", "");

            for (AnalyzedMethod am : ac.getMethods()) {
                // Per semplicità, usiamo solo il nome del metodo senza la firma completa
                // dato che il report PMD fornisce solo il nome del metodo.
                String methodName = am.getMethodDeclaration().getNameAsString();
                String fqmn = fqcn + "." + methodName;
                methodsByFqmn.put(fqmn, am);
            }
        }
        Printer.print("Mappa di " + methodsByFqmn.size() + " metodi FQMN creata per lookup.\n");

        // 2. Percorso del file XML del report PMD
        // Assicurati che PMD_REPORTS_BASE_DIR sia accessibile qui, o passalo come parametro.
        String pmdReportPath = "pmd_analysis" + File.separator + targetName + File.separator + releaseId + ".xml";
        File pmdReportFile = new File(pmdReportPath);

        if (!pmdReportFile.exists()) {
            Printer.printYellow("Report PMD non trovato per release " + releaseId + " al percorso: " + pmdReportPath + ". Non verranno associati code smell.");
            return;
        }

        // 3. Parsifica il file XML e associa i code smell
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(pmdReportFile);
            doc.getDocumentElement().normalize();

            NodeList violationList = doc.getElementsByTagName("violation");

            // Sostituisci la parte che costruisce la mappa FQMN e il lookup con questa logica
            for (int i = 0; i < violationList.getLength(); i++) {
                Element violationElement = (Element) violationList.item(i);

                String className = violationElement.getAttribute("class");
                String methodName = violationElement.getAttribute("method");

                // Cerca la classe corrispondente
                AnalyzedClass matchingClass = null;
                for (AnalyzedClass ac : analyzedClasses) {
                    String acClassName = ac.getFileName().replace(".java", "");
                    if (acClassName.equals(className)) {
                        matchingClass = ac;
                        break;
                    }
                }

                if (matchingClass != null) {
                    // Cerca il metodo corrispondente nella classe
                    AnalyzedMethod matchingMethod = null;
                    for (AnalyzedMethod am : matchingClass.getMethods()) {
                        if (am.getMethodDeclaration().getNameAsString().equals(methodName)) {
                            matchingMethod = am;
                            break;
                        }
                    }
                    if (matchingMethod != null) {
                        matchingMethod.getMetrics().incrementCodeSmellCount();
                        Printer.print("Code smell associato a metodo: " + className + "." + methodName+ "\n");
                    } else {
                        Printer.println("Metodo " + methodName + " non trovato nella classe " + className);

                    }
                } else {
                    Printer.println("Classe " + className + " non trovata tra le classi già analizzate.\n");
                }
            }
            Printer.printGreen("Estrazione code smell completata per release " + releaseId + ". Trovate " + violationList.getLength() + " violazioni.\n");

        } catch (Exception e) {
            Printer.errorPrint("Errore durante parsing o associazione del report PMD per release " + releaseId + " al percorso " + pmdReportPath + ": " + e.getMessage());
        }
    }
}