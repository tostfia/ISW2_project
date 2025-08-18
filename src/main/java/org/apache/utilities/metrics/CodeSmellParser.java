package org.apache.utilities.metrics;


import lombok.Getter;
import org.apache.model.AnalyzedClass;
import org.apache.model.AnalyzedMethod;
import org.apache.model.ClassMetrics;
import org.apache.model.MethodMetrics;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CodeSmellParser {

    private static final String PMD_ANALYSIS_BASE_DIR = "pmd_analysis";
    private static final Logger logger = Logger.getLogger(CodeSmellParser.class.getName());

    private CodeSmellParser() {
        throw new IllegalStateException("Utility class - cannot be instantiated");
    }

    /**
     * Estrae i code smell dai report PMD per tutte le classi analizzate.
     *
     * @param classes Lista delle classi analizzate
     * @param project Nome del progetto
     * @param releaseId ID della release
     */
    public static void extractCodeSmell(List<AnalyzedClass> classes, String project, String releaseId) {
        if (classes == null || classes.isEmpty()) {
            logger.warning("Lista classi vuota - nessun code smell da estrarre");
            return;
        }

        String reportPath = buildReportPath(project, releaseId);
        File reportFile = new File(reportPath);

        if (!reportFile.exists() || !reportFile.canRead()) {
            logger.warning("Report non trovato o non leggibile: " + reportPath);
            return;
        }

        try {
            List<CodeSmellInfo> smells = parseCodeSmellCsv(reportPath,releaseId);
            associateCodeSmellsToClasses(classes, smells);
            logger.info("Estratti " + smells.size() + " code smell da " + reportFile.getName() +
                    " per " + classes.size() + " classi");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Errore durante parsing del report: " + reportPath, e);
        }
    }

    /**
     * Costruisce il percorso del report CSV per una specifica release.
     */
    private static String buildReportPath(String project, String releaseId) {
        Path path = Paths.get(PMD_ANALYSIS_BASE_DIR, project, releaseId + ".csv");
        path.getParent().toFile().mkdirs(); // crea le cartelle se non esistono
        return path.toString();
    }

    /**
     * Parsa il CSV dei code smell usando OpenCSV (più robusto dello split).
     */
    private static List<CodeSmellInfo> parseCodeSmellCsv(String csvFilePath,String releaseId) throws IOException, CsvValidationException {
        List<CodeSmellInfo> smells = new ArrayList<>();

        try (CSVReader reader = new CSVReader(new FileReader(csvFilePath))) {
            String[] header = reader.readNext();
            if (header == null) {
                logger.warning("CSV vuoto: " + csvFilePath);
                return smells;
            }

            String[] row;
            int lineNum = 1;

            while ((row = reader.readNext()) != null) {
                lineNum++;
                try {
                    CodeSmellInfo info = new CodeSmellInfo(row, lineNum,releaseId);
                    smells.add(info);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Errore parsing riga " + lineNum, e);
                }
            }
        }
        return smells;
    }

    /**
     * Associa i code smell alle classi/metodi.
     */
    private static void associateCodeSmellsToClasses(List<AnalyzedClass> classes, List<CodeSmellInfo> smells)  {
        for (AnalyzedClass analyzedClass : classes) {
            String className = normalizeClassName(analyzedClass.getClassName());

            List<CodeSmellInfo> classSmells = smells.stream()
                    .filter(s -> isSmellForClass(s, className))
                    .toList();

            if (!classSmells.isEmpty()) {
                logger.fine("Trovati " + classSmells.size() + " code smell per classe " + className);
                associateSmellsToMethods(analyzedClass, classSmells);
            }
        }
    }

    /**
     * Normalizza il nome della classe (senza .java, path pulito).
     */
    private static String normalizeClassName(String className) {
        if (className == null) return "";
        return className.replaceAll("\\.java$", "").trim();
    }

    private static boolean isSmellForClass(CodeSmellInfo smell, String className) {
        String cleanFilename = smell.getFilename().replaceAll("\"", "").replace("\\", "/");
        String fileOnly = Paths.get(cleanFilename).getFileName().toString();
        return fileOnly.equals(className + ".java");
    }

    private static void associateSmellsToMethods(AnalyzedClass analyzedClass, List<CodeSmellInfo> classSmells) {
        boolean assignedToMethod = false;

        for (AnalyzedMethod method : analyzedClass.getMethods()) {
            String methodName = method.getMethodDeclaration().getNameAsString();
            int begin = method.getMethodDeclaration().getBegin().map(p -> p.line).orElse(-1);
            int end = method.getMethodDeclaration().getEnd().map(p -> p.line).orElse(-1);

            for (CodeSmellInfo smell : classSmells) {
                // Associa lo smell se cade dentro (o vicino) al range del metodo
                boolean lineMatches = smell.getLine() >= begin - 2 && smell.getLine() <= end + 2;

                if (lineMatches) {
                    MethodMetrics metrics = method.getMetrics();
                    metrics.setNumberOfCodeSmells(metrics.getNumberOfCodeSmells() + 1);
                    assignedToMethod = true;
                }
            }
        }

        // Se nessun metodo ha catturato lo smell → assegnalo alla classe
        if (!assignedToMethod) {
            ClassMetrics classMetrics = analyzedClass.getProcessMetrics();
            for (CodeSmellInfo smell : classSmells) {
                classMetrics.setNumberOfCodeSmells(classMetrics.getNumberOfCodeSmells() + 1);
            }
        }
    }




    /**
     * Rappresentazione di un code smell da CSV.
     */
    public static class CodeSmellInfo {
        private final String problem;
        @Getter
        private final String filename;
        @Getter
        private final int line;
        @Getter
        private final String releaseId;      // release ID
        @Getter
        private final String methodName;     // nome del metodo, se presente nel CSV

        public CodeSmellInfo(String[] row, int rowNumber,String releaseId) {
            this.problem = getSafe(row, 0);
            this.line = parseLine(getSafe(row, 4), rowNumber); // colonna LINE
            this.releaseId = releaseId;       // colonna RELEASE (da adattare al CSV)
            String fileCol = getSafe(row, 2); // FILE column
            this.filename = fileCol.contains(":") ? fileCol.substring(0, fileCol.indexOf(":")) : fileCol;
            this.methodName = fileCol.contains(":") ? fileCol.substring(fileCol.indexOf(":") + 1) : null;
        }

        private String getSafe(String[] arr, int idx) {
            return idx < arr.length ? arr[idx].replaceAll("\"", "").trim() : "";
        }

        private int parseLine(String val, int rowNum) {
            try {
                return Integer.parseInt(val.trim());
            } catch (Exception e) {
                logger.warning("Linea non valida alla riga " + rowNum + ": " + val);
                return -1;
            }
        }
    }
}
