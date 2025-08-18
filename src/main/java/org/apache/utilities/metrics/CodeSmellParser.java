package org.apache.utilities.metrics;

import com.github.javaparser.ast.body.MethodDeclaration;
import org.apache.model.AnalyzedClass;
import org.apache.model.AnalyzedMethod;
import org.apache.model.MethodMetrics;
import org.apache.model.Release;
import lombok.Getter;
import lombok.Setter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Parser per l'estrazione di code smell da file CSV generati da PMD.
 * Questa classe è responsabile del parsing dei report PMD e dell'associazione
 * dei code smell ai metodi delle classi analizzate.
 */
public class CodeSmellParser {

    private static final String CSV_SEPARATOR = ",";
    private static final String PMD_ANALYSIS_BASE_DIR = "pmd_analysis";
    private static final Logger logger = Logger.getLogger(CodeSmellParser.class.getName());

    private CodeSmellParser() {
        throw new IllegalStateException("Utility class - cannot be instantiated");
    }

    /**
     * Estrae i code smell dai report PMD per tutte le classi analizzate.
     *
     * @param classes Lista delle classi analizzate per cui estrarre i code smell
     * @param pmdReportPath Percorso del file CSV contenente il report PMD
     */
    public static void extractCodeSmell(List<AnalyzedClass> classes, String pmdReportPath) {
        if (classes == null || classes.isEmpty()) {
            logger.warning("Lista delle classi vuota o null - nessun code smell da estrarre");
            return;
        }

        if (pmdReportPath == null || pmdReportPath.trim().isEmpty()) {
            logger.warning("Percorso del report PMD non valido: " + pmdReportPath);
            return;
        }

        File reportFile = new File(pmdReportPath);
        if (!reportFile.exists() || !reportFile.canRead()) {
            logger.warning("File report PMD non trovato o non leggibile: " + pmdReportPath);
            return;
        }

        try {
            List<CodeSmellInfo> codeSmellInfos = parseCodeSmellCsv(pmdReportPath);
            associateCodeSmellsToClasses(classes, codeSmellInfos);

            logger.info("Estratti " + codeSmellInfos.size() + " code smell da " + reportFile.getName() +
                    " per " + classes.size() + " classi");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Errore durante l'estrazione dei code smell da: " + pmdReportPath, e);
        }
    }

    /**
     * Versione legacy del metodo per mantenere compatibilità con il codice esistente.
     *
     * @param classesPerRelease Mappa delle classi per release
     * @param project Nome del progetto
     * @deprecated Utilizzare invece extractCodeSmell(List<AnalyzedClass>, String)
     */
    @Deprecated
    public static void extractCodeSmell(Map<Release, List<AnalyzedClass>> classesPerRelease, String project) {
        if (classesPerRelease == null || classesPerRelease.isEmpty()) {
            logger.warning("Mappa delle classi per release vuota o null");
            return;
        }

        classesPerRelease.forEach((release, classes) -> {
            if (classes == null || classes.isEmpty()) {
                logger.warning("Nessuna classe trovata per la release: " + release.getId());
                return;
            }

            String reportPath = buildReportPath(project, release.getId());
            extractCodeSmell(classes, reportPath);
        });
    }

    /**
     * Costruisce il percorso del report PMD per una specifica release.
     */
    private static String buildReportPath(String project, int releaseId) {
        return PMD_ANALYSIS_BASE_DIR + File.separator + project + File.separator + releaseId + ".csv";
    }

    /**
     * Parsa il file CSV contenente i code smell.
     */
    private static List<CodeSmellInfo> parseCodeSmellCsv(String csvFilePath) throws IOException {
        List<CodeSmellInfo> codeSmellInfos = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(csvFilePath))) {
            String headerRow = reader.readLine();
            if (headerRow == null) {
                logger.warning("File CSV vuoto: " + csvFilePath);
                return codeSmellInfos;
            }

            validateCsvHeader(headerRow);

            String dataRow;
            int lineNumber = 1;

            while ((dataRow = reader.readLine()) != null) {
                lineNumber++;
                try {
                    if (!dataRow.trim().isEmpty()) {
                        CodeSmellInfo info = new CodeSmellInfo(dataRow, lineNumber);
                        codeSmellInfos.add(info);
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Errore parsing riga " + lineNumber + " in " + csvFilePath + ": " + dataRow, e);
                    // Continua con le altre righe invece di fermarsi
                }
            }
        }

        return codeSmellInfos;
    }

    /**
     * Valida l'header del file CSV.
     */
    private static void validateCsvHeader(String headerRow) {
        if (headerRow == null || headerRow.trim().isEmpty()) {
            throw new IllegalArgumentException("Header del CSV vuoto o null");
        }

        String[] columns = headerRow.split(CSV_SEPARATOR);
        CsvHeader[] expectedHeaders = CsvHeader.values();

        if (columns.length < expectedHeaders.length) {
            logger.warning("Il CSV ha meno colonne del previsto. Previste: " + expectedHeaders.length +
                    ", trovate: " + columns.length);
        }

        // Verifica le colonne essenziali
        for (CsvHeader header : CsvHeader.getEssentialHeaders()) {
            boolean found = false;
            for (int i = 0; i < Math.min(columns.length, expectedHeaders.length); i++) {
                if (columns[i].trim().replaceAll("\"", "").equalsIgnoreCase(header.getCleanValue())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                logger.warning("Colonna essenziale non trovata nell'header: " + header.getCleanValue());
            }
        }
    }

    /**
     * Associa i code smell alle classi e ai loro metodi.
     */
    private static void associateCodeSmellsToClasses(List<AnalyzedClass> classes, List<CodeSmellInfo> codeSmellInfos) {
        for (AnalyzedClass analyzedClass : classes) {
            String className = analyzedClass.getClassName();

            List<CodeSmellInfo> classSmells = codeSmellInfos.stream()
                    .filter(smell -> isSmellForClass(smell, className))
                    .toList();

            if (!classSmells.isEmpty()) {
                logger.fine("Trovati " + classSmells.size() + " code smell per la classe: " + className);
                associateSmellsToMethods(analyzedClass, classSmells);
            }
        }
    }

    /**
     * Verifica se un code smell appartiene alla classe specificata.
     */
    private static boolean isSmellForClass(CodeSmellInfo smell, String className) {
        if (smell.getFilename() == null || className == null) {
            return false;
        }

        // Rimuovi estensioni e percorsi per confrontare solo il nome della classe
        String cleanFilename = smell.getFilename().replaceAll("\"", "");
        String cleanClassName = className.replaceAll("\\.java$", "");

        // Verifica diversi pattern di matching
        return cleanFilename.endsWith(cleanClassName + ".java") ||
                cleanFilename.endsWith(cleanClassName) ||
                cleanFilename.contains(File.separator + cleanClassName + ".java") ||
                cleanFilename.equals(cleanClassName + ".java");
    }

    /**
     * Associa i code smell ai metodi della classe.
     */
    private static void associateSmellsToMethods(AnalyzedClass analyzedClass, List<CodeSmellInfo> classSmells) {
        for (AnalyzedMethod method : analyzedClass.getMethods()) {
            MethodDeclaration decl = method.getMethodDeclaration();
            int beginLine = decl.getBegin().map(p -> p.line).orElse(-1);
            int endLine = decl.getEnd().map(p -> p.line).orElse(-1);

            for (CodeSmellInfo smell : classSmells) {
                int smellLine = smell.getLine();
                if (smellLine >= beginLine && smellLine <= endLine) {
                    MethodMetrics metrics = method.getMetrics();
                    metrics.setNumberOfCodeSmells(metrics.getNumberOfCodeSmells() + 1);
                }
            }
        }
    }



    /**
     * Classe che rappresenta le informazioni di un code smell estratto dal CSV.
     */
    @Getter
    @Setter
    public static class CodeSmellInfo {
        private String problem;
        private String packagePath;
        private String filename;
        private String priority;
        private int line;
        private String description;
        private String ruleSet;
        private String rule;

        public CodeSmellInfo(String csvRow, int rowNumber) {
            if (csvRow == null || csvRow.trim().isEmpty()) {
                throw new IllegalArgumentException("Riga CSV vuota alla riga: " + rowNumber);
            }

            String[] values = csvRow.split(CSV_SEPARATOR);
            CsvHeader[] headers = CsvHeader.values();

            try {
                // Parsing robusto con valori di default
                this.problem = getValueSafely(values, CsvHeader.PROBLEM.ordinal());
                this.packagePath = getValueSafely(values, CsvHeader.PACKAGE.ordinal());
                this.filename = getValueSafely(values, CsvHeader.FILE.ordinal());
                this.priority = getValueSafely(values, CsvHeader.PRIORITY.ordinal());
                this.description = getValueSafely(values, CsvHeader.DESCRIPTION.ordinal());
                this.ruleSet = getValueSafely(values, CsvHeader.RULE_SET.ordinal());
                this.rule = getValueSafely(values, CsvHeader.RULE.ordinal());

                // Parsing della linea con gestione errori
                String lineValue = getValueSafely(values, CsvHeader.LINE.ordinal());
                this.line = parseLineNumber(lineValue, rowNumber);

            } catch (Exception e) {
                throw new IllegalArgumentException("Errore parsing riga CSV " + rowNumber + ": " + csvRow, e);
            }
        }

        private String getValueSafely(String[] values, int index) {
            if (index >= values.length) {
                return "";
            }
            return values[index] != null ? values[index].replaceAll("\"", "").trim() : "";
        }

        private int parseLineNumber(String lineValue, int rowNumber) {
            try {
                String cleanValue = lineValue.replaceAll("[\"\\s]", "");
                if (cleanValue.isEmpty()) {
                    logger.warning("Numero di riga vuoto alla riga CSV: " + rowNumber);
                    return 0;
                }
                return Integer.parseInt(cleanValue);
            } catch (NumberFormatException e) {
                logger.warning("Numero di riga non valido '" + lineValue + "' alla riga CSV: " + rowNumber);
                return 0;
            }
        }
    }

    /**
     * Enum che definisce l'header del CSV PMD.
     */
    @Getter
    public enum CsvHeader {
        PROBLEM("\"Problem\"", true),
        PACKAGE("\"Package\"", false),
        FILE("\"File\"", true),
        PRIORITY("\"Priority\"", false),
        LINE("\"Line\"", true),
        DESCRIPTION("\"Description\"", false),
        RULE_SET("\"Rule set\"", false),
        RULE("\"Rule\"", false);

        private final String value;
        private final boolean essential;

        CsvHeader(String value, boolean essential) {
            this.value = value;
            this.essential = essential;
        }

        public String getCleanValue() {
            return value.replaceAll("\"", "");
        }

        public static CsvHeader[] getEssentialHeaders() {
            return java.util.Arrays.stream(values())
                    .filter(CsvHeader::isEssential)
                    .toArray(CsvHeader[]::new);
        }
    }
}