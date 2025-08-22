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
import java.util.logging.Level;
import java.util.logging.Logger;



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
    public void generatePmdReports() {
        String outputDirPath = PMD_ANALYSIS_BASE_DIR + File.separator + this.project;
        new File(outputDirPath);

        // 1. Ordina le release in base al loro ID per un'esecuzione sequenziale e logica.
        List<Release> sortedReleases = releases.stream()
                .sorted(Comparator.comparing(Release::getId))
                .toList();

        // 2. Itera sulla lista ordinata.
        for (Release release : sortedReleases) {

            // 3. Controlla se la release ha dei commit. Se non ne ha, saltala.
            if (release.getCommitList().isEmpty()) {
                logger.warning("La release " + release.getId() + " (" + release.getReleaseName() + ") non ha commit, impossibile analizzare.");
                continue; // Passa alla prossima release
            }

            // 4. Prendi l'ULTIMO commit della release, che rappresenta lo snapshot finale.
            RevCommit targetCommit = release.getCommitList().getLast().getRevCommit();


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
            logger.log(Level.INFO, "Report PMD (XML) per la release {0} già esistente. Salto l'analisi.", releaseId);

            return;
        }

        logger.log(Level.INFO, "Inizio analisi PMD per la release {0} (commit: {1})", new Object[]{releaseId, commit.getName()});

        try {
            git.checkout().setForced(true).setName(commit.getName()).setStartPoint(commit).call();
            logger.log(Level.INFO, "Checkout al commit {0} completato.", commit.getName());

            Process process = buildPmdProcess(reportPath);

            // --- INIZIO DIAGNOSTICA AVANZATA ---


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
                logger.log(Level.WARNING, "Analisi PMD per la release {0} ha superato il tempo limite.", releaseId);

            }

            // 3. Logga i risultati dettagliati
            logger.log(Level.INFO, "Analisi PMD per la release {0} terminata con codice di uscita: {1}", new Object[]{releaseId, exitCode});


            if (exitCode == 0) {
                // PMD ha terminato con successo e non ha trovato violazioni.
                // Potrebbe esserci il caso "No files to analyze".
                if (pmdOutput.toString().contains("No files to analyze")) {
                    logger.log(Level.WARNING, "PMD ha terminato con successo per la release {0} ma non ha trovato file Java da analizzare.", releaseId);

                }
            } else if (exitCode == 4) {
                // PMD ha terminato con successo e ha trovato violazioni.
                logger.log(Level.SEVERE, "PMD ha terminato con successo per la release {0} trovando violazioni. (Vedi report XML).", releaseId);

            }else {
                logger.log(Level.SEVERE, "PMD ha fallito inaspettatamente per la release {0}. Codice di uscita: {1}. Output di PMD:\n{2}",
                        new Object[]{releaseId, exitCode, pmdOutput.toString()});
            }



        } catch (GitAPIException e) {
            logger.log(Level.SEVERE, "Errore critico di Git durante il checkout del commit {0}: {1}", new Object[]{commit.getName(), e.getMessage()});
            cleanAndResetGitState();
        } catch (IOException | InterruptedException e) {
            logger.log(Level.SEVERE, "Errore durante l'esecuzione di PMD per la release {0}: {1}", new Object[]{releaseId, e.getMessage()});
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
            logger.log(Level.SEVERE, "Fallito il ripristino dello stato del repository: {0}", e.getMessage());

        }
    }

    private void restoreRepositoryState() {
        logger.log(Level.INFO,"Ripristino del repository al branch originale: " ,originalBranch);
        try {
            cleanAndResetGitState(); // Pulisci prima di cambiare branch
            git.checkout().setName(originalBranch).call();
            logger.info("Repository ripristinato con successo.");
        } catch (GitAPIException e) {
            logger.log(Level.SEVERE, "Impossibile ripristinare il repository al branch {0}: {1}", new Object[]{originalBranch, e.getMessage()});

        }
    }
}
