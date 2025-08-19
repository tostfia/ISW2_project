package org.apache.utilities.metrics;
import lombok.Getter;
import org.apache.logging.CollectLogger;
import org.apache.model.AnalyzedClass;
import org.apache.model.AnalyzedMethod;
import org.apache.model.ClassMetrics;
import org.apache.model.MethodMetrics;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;


public class CodeSmellParser {

    private static final String PMD_ANALYSIS_BASE_DIR = "pmd_analysis";
    private static final Logger logger = CollectLogger.getInstance().getLogger();



    private static String REPO_ROOT_PATH = null; // Sarà impostato dall'esterno

    public static void setRepoRootPath(String path) {
        // Normalizza il percorso, assicurati che sia assoluto e con separatori uniformi
        REPO_ROOT_PATH = Paths.get(path).toAbsolutePath().normalize().toString().replace("\\", "/");
        if (!REPO_ROOT_PATH.endsWith("/")) {
            REPO_ROOT_PATH += "/";
        }
        logger.info("CodeSmellParser: Impostato REPO_ROOT_PATH a: " + REPO_ROOT_PATH);
    }

    private CodeSmellParser() {
        throw new IllegalStateException("Utility class - cannot be instantiated");
    }

    /**
     * Estrae i code smell dai report PMD per tutte le classi analizzate.
     * Ora legge XML.
     *
     * @param classes Lista delle classi analizzate
     * @param project Nome del progetto
     * @param releaseId ID della release
     */
    public static void extractCodeSmell(List<AnalyzedClass> classes, String project, String releaseId) {
        if (classes == null || classes.isEmpty()) {
            System.out.println("extractCodeSmell: Lista classi vuota - nessun code smell da estrarre per release " + releaseId);
            return;
        }

        // CAMBIA QUI L'ESTENSIONE DEL FILE DI REPORT
        String reportPath = buildReportPath(project, releaseId);
        File reportFile = new File(reportPath);


        if (!reportFile.exists() || !reportFile.canRead()) {
            logger.severe("extractCodeSmell: Report PMD XML non trovato o non leggibile: " + reportPath + " per release " + releaseId);
            return;
        }

        try {
            // CAMBIA QUI: chiama il nuovo metodo di parsing XML
            List<CodeSmellInfo> smells = parseCodeSmellXml(reportPath, releaseId);

            int originalSmellCount = smells.size();

            // QUESTA È LA POSIZIONE GIUSTA PER IL FILTRO TEST
            smells.removeIf(s -> {
                try {
                    // Crea un oggetto Path dal filename dello smell (dal report XML, che ora è corretto)
                    Path pmdAbsolutePathObj = Paths.get(s.getFilename()).toAbsolutePath().normalize();
                    String normalizedPathForFilter = pmdAbsolutePathObj.toString().replace("\\", "/");

                    boolean isTestFile = normalizedPathForFilter.contains("/test/") || normalizedPathForFilter.contains("/src/test/");
                    if (isTestFile) {
                        logger.warning("Filtro: Rimosso smell da file di test: " + s.getFilename() + " (Normalizzato per filtro: " + normalizedPathForFilter + ")");
                    }
                    return isTestFile;
                } catch (Exception e) {
                    logger.severe(e.getMessage());
                    return false;
                }
            });
            associateCodeSmellsToClasses(classes, smells);
            logger.info("extractCodeSmell: Estratti e tentata associazione di " + smells.size() + " code smell da " + reportFile.getName() +
                    " per " + classes.size() + " classi nella release " + releaseId);

        } catch (Exception e) {
            logger.severe("Errore durante parsing o associazione del report: " + reportPath + " per release " + releaseId);
        }
    }

    /**
     * Parsa il report XML dei code smell.
     */
    private static List<CodeSmellInfo> parseCodeSmellXml(String xmlFilePath, String releaseId) throws Exception {
        List<CodeSmellInfo> smells = new ArrayList<>();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new File(xmlFilePath));
        doc.getDocumentElement().normalize(); // Normalizza l'albero DOM

        NodeList fileNodes = doc.getElementsByTagName("file"); // Trova tutti i tag <file>

        for (int i = 0; i < fileNodes.getLength(); i++) {
            Element fileElement = (Element) fileNodes.item(i);
            String filename = fileElement.getAttribute("name"); // Estrai il nome del file dall'attributo "name"

            NodeList violationNodes = fileElement.getElementsByTagName("violation"); // Trova tutti i tag <violation> all'interno del <file>
            for (int j = 0; j < violationNodes.getLength(); j++) {
                Element violationElement = (Element) violationNodes.item(j);

                int beginLine = Integer.parseInt(violationElement.getAttribute("beginline"));
                // PMD fornisce anche endline, begincolumn, endcolumn, rule, ruleset, package, class, method, variable, description
                // Puoi estrarre tutte le informazioni che ti servono e passarle al costruttore di CodeSmellInfo

                // Per ottenere il nome del metodo (se presente):
                String methodName = violationElement.getAttribute("method"); // L'attributo "method" è presente nell'XML!
                if (methodName.isEmpty()) { // Se l'attributo è vuoto (es. code smell a livello di classe)
                    methodName = null; // O un placeholder come "CLASS_LEVEL"
                }

                // Costruisci CodeSmellInfo. Non abbiamo un "row" come prima.
                // Modificheremo il costruttore di CodeSmellInfo per accettare questi parametri.
                smells.add(new CodeSmellInfo(filename, beginLine, releaseId, methodName)); // Adatta il costruttore

            }
        }
        logger.info("parseCodeSmellXml: Terminato parsing di " + xmlFilePath + ". Trovati " + smells.size() + " code smell.");
        return smells;
    }


    // AGGIORNATO: Associa i code smell alle classi/metodi.
    private static void associateCodeSmellsToClasses(List<AnalyzedClass> classes, List<CodeSmellInfo> smells) {
        for (AnalyzedClass analyzedClass : classes) {
            String className = analyzedClass.getClassName(); // getClassName() dovrebbe già essere il percorso relativo e pulito


            List<CodeSmellInfo> classSmells = smells.stream()
                    .filter(s -> {
                        return isSmellForClass(s, className);
                    })
                    .collect(Collectors.toList()); // o .toList() per Java 16+

            if (!classSmells.isEmpty()) {
                associateSmellsToMethods(analyzedClass, classSmells);
            } else {
                logger.warning("associateCodeSmellsToClasses: Nessun code smell trovato per la classe " + className + " in questo report.");
            }
        }
    }




    private static boolean isSmellForClass(CodeSmellInfo smell, String className) {
        if (REPO_ROOT_PATH == null) {
            logger.severe("isSmellForClass: REPO_ROOT_PATH non è stato impostato. Assicurati di chiamare setRepoRootPath prima di usare questa funzione.");
            return false;
        }

        try {
            // 1. Normalizza il percorso del file dallo smell (dal report XML)
            // L'XML ci dà già un percorso pulito e assoluto (C:\Users\sofia\repo\bookkeeper\...)
            // Lo convertiamo in oggetto Path e poi stringa normalizzata con '/'
            Path pmdAbsolutePathObj = Paths.get(smell.getFilename()).toAbsolutePath().normalize();
            String pmdFilePathNormalized = pmdAbsolutePathObj.toString().replace("\\", "/");

            // Normalizza REPO_ROOT_PATH (dovrebbe già essere pulito da setRepoRootPath)
            Path repoRootPathObj = Paths.get(REPO_ROOT_PATH);

            // Confronta se il percorso PMD normalizzato inizia con la root del repository.
            if (!pmdAbsolutePathObj.startsWith(repoRootPathObj)) {
                logger.severe("Path non corrisponde");// Usa pmdAbsolutePathObj per startsWith
                return false;
            }

            // Rendi il percorso PMD relativo alla root del repository.
            Path relativePmdPathObj = repoRootPathObj.relativize(pmdAbsolutePathObj);
            String relativePmdPath = relativePmdPathObj.toString().replace("\\", "/");


            // 2. Normalizza il percorso della classe analizzata (dal tuo modello AnalyzedClass)
            // Assumiamo che className sia già relativo alla root del repository (es. "bookkeeper-server/src/main/java/...")
            String normalizedAnalyzedClassPath = className.replace("\\", "/");
            // Assicurati che il percorso della classe non inizi con '/' se è già relativo
            if (normalizedAnalyzedClassPath.startsWith("/")) {
                normalizedAnalyzedClassPath = normalizedAnalyzedClassPath.substring(1);
            }


            // LOG: Logga i valori normalizzati finali che vengono confrontati

            return relativePmdPath.equals(normalizedAnalyzedClassPath);

        } catch (Exception e) {
            logger.severe(e.getMessage());
            return false;
        }
    }


    private static void associateSmellsToMethods(AnalyzedClass analyzedClass, List<CodeSmellInfo> classSmells) {

        List<CodeSmellInfo> smellsNotYetAssigned = new ArrayList<>(classSmells);

        for (AnalyzedMethod method : analyzedClass.getMethods()) {
            String methodName = method.getMethodDeclaration().getNameAsString();
            int begin = method.getMethodDeclaration().getBegin().map(p -> p.line).orElse(-1);
            int end = method.getMethodDeclaration().getEnd().map(p -> p.line).orElse(-1);
            Iterator<CodeSmellInfo> iterator = smellsNotYetAssigned.iterator();
            while (iterator.hasNext()) {
                CodeSmellInfo smell = iterator.next();
                boolean lineMatches = smell.getLine() >= begin - 2 && smell.getLine() <= end + 2;
                if (lineMatches) {
                    MethodMetrics metrics = method.getMetrics();
                    metrics.setNumberOfCodeSmells(metrics.getNumberOfCodeSmells() + 1);
                    iterator.remove();
                }
            }
        }
        if (!smellsNotYetAssigned.isEmpty()) {
            ClassMetrics classMetrics = analyzedClass.getProcessMetrics();
            for (CodeSmellInfo smell : smellsNotYetAssigned) {
                classMetrics.setNumberOfCodeSmells(classMetrics.getNumberOfCodeSmells() + 1);
            }
        }

    }


    private static String buildReportPath(String project, String releaseId) {
        Path path = Paths.get(PMD_ANALYSIS_BASE_DIR, project, releaseId + ".xml"); //
        path.getParent().toFile().mkdirs();
        return path.toString();
    }


    private static String normalizeClassName(String className) {
        if (className == null) return "";
        return className.replaceAll("\\.java$", "").trim();
    }


    /**
     * Rappresentazione di un code smell da XML.
     * Costruttore modificato per prendere dati da XML.
     */
    public static class CodeSmellInfo {
        private final String problem; // Puoi anche estrarre la descrizione della violazione dall'XML
        @Getter
        private final String filename;
        @Getter
        private final int line;
        @Getter
        private final String releaseId;
        @Getter
        private final String methodName; // Aggiunto per catturare il nome del metodo dall'XML

        // Nuovo costruttore per il parsing XML
        public CodeSmellInfo(String filename, int line, String releaseId, String methodName) {
            this.filename = filename;
            this.line = line;
            this.releaseId = releaseId;
            this.methodName = methodName;
            this.problem = "N/A"; // L'XML ha una descrizione completa, puoi passarla qui se vuoi
            System.out.println("CodeSmellInfo: Creato smell per file '" + filename + "' a riga " + line + ", metodo: " + methodName);
        }

        // Il vecchio costruttore da CSV non è più usato, ma potresti tenerlo per compatibilità
        // se hai bisogno di parsare sia CSV che XML in altri contesti.
        public CodeSmellInfo(String[] row, int rowNumber, String releaseId) {
            this.problem = getSafe(row, 0);
            this.line = parseLine(getSafe(row, 4), rowNumber);
            this.releaseId = releaseId;
            this.filename = getSafe(row, 2);
            this.methodName = null; // O prova a estrarlo dal CSV se il formato lo permette
            System.out.println("CodeSmellInfo (CSV): Creato smell per file '" + filename + "' a riga " + line + ", problema: '" + problem + "'");
        }

        private String getSafe(String[] arr, int idx) {
            return idx < arr.length ? arr[idx].replaceAll("\"", "").trim() : "";
        }

        private int parseLine(String val, int rowNum) {
            try {
                return Integer.parseInt(val.trim());
            } catch (Exception e) {
               logger.severe(e.getMessage());
               return -1;
            }

        }
    }
}
