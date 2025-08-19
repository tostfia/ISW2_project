package org.apache.utilities.metrics;


import org.apache.model.Release;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Comparator;
import java.util.List;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class NumOfCodeSmells {

    private static final String PMD_ANALYSIS_BASE_DIR = "pmd_analysis";
    private static final Logger logger = Logger.getLogger(NumOfCodeSmells.class.getName());

    private final String project;
    private final String repoPath;
    private final Git git;
    private final List<Release> releases;
    private final String originalBranch;

    /**
     * Costruttore per l'orchestratore di analisi PMD.
     * @param project Il nome del progetto (es. "BOOKKEEPER").
     * @param repoPath Il percorso locale del repository Git.
     * @param git L'istanza dell'oggetto Git.
     * @param releases La lista delle release da analizzare.
     */
    public NumOfCodeSmells(String project, String repoPath, Git git, List<Release> releases) throws IOException {
        this.project = project;
        this.repoPath = repoPath;
        this.git = git;
        this.releases = releases;
        this.originalBranch = git.getRepository().getBranch(); // Salva il branch corrente per ripristinarlo
    }

    /**
     * Esegue l'analisi PMD per tutte le release, generando un file CSV per ciascuna.
     */
    // Dentro la classe PmdReportGenerator

    /**
     * Esegue l'analisi PMD per tutte le release, generando un file CSV per ciascuna.
     */
    public void generatePmdReports() {
        String outputDirPath = PMD_ANALYSIS_BASE_DIR + File.separator + this.project;
        new File(outputDirPath).mkdirs(); // Crea la cartella di output se non esiste

        // 1. Ordina le release in base al loro ID per un'esecuzione sequenziale e logica.
        List<Release> sortedReleases = releases.stream()
                .sorted(Comparator.comparing(Release::getId))
                .collect(Collectors.toList()); // Usa .collect per evitare problemi con stream paralleli

        // 2. Itera sulla lista ordinata.
        for (Release release : sortedReleases) {

            // 3. Controlla se la release ha dei commit. Se non ne ha, saltala.
            if (release.getCommitList().isEmpty()) {
                logger.warning("La release " + release.getId() + " (" + release.getReleaseName() + ") non ha commit, impossibile analizzare.");
                continue; // Passa alla prossima release
            }

            // 4. Prendi l'ULTIMO commit della release, che rappresenta lo snapshot finale.
            RevCommit targetCommit = release.getCommitList().get(release.getCommitList().size() - 1).getRevCommit();


            // 5. Chiama il metodo di analisi passando l'ID CORRETTO della release corrente.
            //    Questa è la riga più importante. Non ci sono 'if' o logiche strane.
            runPmdForCommit(release.getReleaseID(), targetCommit);
        }

        // 6. Alla fine di TUTTE le iterazioni, ripristina lo stato del repository.
        restoreRepositoryState();
    }

    // Dentro la classe PmdReportGenerator

    private void runPmdForCommit(String releaseId, RevCommit commit) {
        // CAMBIA QUI L'ESTENSIONE DEL FILE DI REPORT
        String reportPath = PMD_ANALYSIS_BASE_DIR + File.separator + this.project + File.separator + releaseId + ".xml";

        if (new File(reportPath).exists()) {
            logger.info("Report PMD (XML) per la release " + releaseId + " già esistente. Salto l'analisi.");
            return;
        }

        logger.info("Inizio analisi PMD per la release " + releaseId + " (commit: " + commit.getName() + ")");

        try {
            git.checkout().setForced(true).setName(commit.getName()).setStartPoint(commit).call();
            logger.info("Checkout al commit " + commit.getName() + " completato.");

            Process process = buildPmdProcess(reportPath);

            // --- INIZIO DIAGNOSTICA AVANZATA ---

            // 1. Cattura l'output del processo PMD in tempo reale
            StringBuilder pmdOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    pmdOutput.append(line).append("\n");
                }
            }

            // 2. Aspetta che il processo termini e ottieni il codice di uscita
            boolean finished = process.waitFor(5, TimeUnit.MINUTES);
            int exitCode = -1;
            if (finished) {
                exitCode = process.exitValue();
            } else {
                process.destroyForcibly();
                logger.warning("Analisi PMD per la release " + releaseId + " ha superato il tempo limite.");
            }

            // 3. Logga i risultati dettagliati
            logger.info("Analisi PMD per la release " + releaseId + " terminata con codice di uscita: " + exitCode);

            if (exitCode == 0) {
                // PMD ha terminato con successo e non ha trovato violazioni.
                // Potrebbe esserci il caso "No files to analyze".
                if (pmdOutput.toString().contains("No files to analyze")) {
                    logger.warning("PMD ha terminato con successo per la release " + releaseId + " ma non ha trovato file Java da analizzare. (Commit potrebbe non contenere codice Java o filtri attivi).");
                } else {
                    logger.info("PMD ha terminato con successo per la release " + releaseId + " senza trovare violazioni.");
                }
            } else if (exitCode == 4) {
                // PMD ha terminato con successo e ha trovato violazioni.
                logger.info("PMD ha terminato con successo per la release " + releaseId + " trovando violazioni. (Vedi report XML).");
            } else {
                // Qualsiasi altro codice di uscita indica un vero fallimento o errore di configurazione.
                logger.severe("PMD ha fallito inaspettatamente per la release " + releaseId + ". Codice di uscita: " + exitCode + ". Output di PMD:\n" + pmdOutput.toString());
            }
            // --- FINE DIAGNOSTICA AVANZATA ---

        } catch (GitAPIException e) {
            logger.severe("Errore critico di Git durante il checkout del commit " + commit.getName() + ": " + e.getMessage());
            cleanAndResetGitState();
        } catch (IOException | InterruptedException e) {
            logger.severe("Errore durante l'esecuzione di PMD per la release " + releaseId + ": " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    private Process buildPmdProcess(String reportPath) throws IOException {
        String pmdHome = System.getenv("PMD_HOME");
        if (pmdHome == null || pmdHome.isEmpty()) {
            throw new IOException("La variabile d'ambiente PMD_HOME non è impostata.");
        }

        // Determina l'eseguibile corretto (Windows vs Unix)
        String pmdExecutableName = System.getProperty("os.name").toLowerCase().contains("win")
                ? "pmd.bat"
                : "pmd";

        String pmdExecutablePath = pmdHome + File.separator + "bin" + File.separator + pmdExecutableName;

        // Usa repoPath passato al costruttore

        // Path ruleset: meglio passarlo come parametro o config, non hardcoded
        String rulesetPath = "src/main/resources/pmd-ruleset.xml";


        return new ProcessBuilder(
                pmdExecutablePath, "check",
                "-d", repoPath,
                "-R", rulesetPath,
                "-f", "xml", // CAMBIA QUI: da "csv" a "xml"
                "--no-cache",
                "--debug",
                "-r", reportPath.replace(".csv", ".xml") // AGGIORNA QUI: cambia estensione file a .xml
        ).redirectErrorStream(true).start();

    }


    private void cleanAndResetGitState() {
        try {
            logger.info("Tentativo di ripristinare uno stato pulito del repository...");
            git.reset().setMode(ResetCommand.ResetType.HARD).call();
            git.clean().setForce(true).setCleanDirectories(true).call();
            logger.info("Reset e clean completati.");
        } catch (GitAPIException e) {
            logger.severe("Fallito il ripristino dello stato del repository: " + e.getMessage());
        }
    }

    private void restoreRepositoryState() {
        logger.info("Ripristino del repository al branch originale: " + originalBranch);
        try {
            cleanAndResetGitState(); // Pulisci prima di cambiare branch
            git.checkout().setName(originalBranch).call();
            logger.info("Repository ripristinato con successo.");
        } catch (GitAPIException e) {
            logger.severe("Impossibile ripristinare il repository al branch " + originalBranch + ": " + e.getMessage());
        }
    }
}
