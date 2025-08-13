package org.apache.controller;

import lombok.Getter;
import lombok.Setter;
import org.apache.logging.CollectLogger;
import org.apache.model.Commit;
import org.apache.model.Release;
import org.apache.model.Ticket;
import org.apache.utilities.enums.ReportType;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GitController {
    // Usiamo File.separator per la portabilità tra sistemi operativi
    private static final String REPO_BASE_PATH = "C:" + File.separator + "Users" + File.separator + "sofia" + File.separator + "repo";

    @Getter
    @Setter
    private List<Ticket> tickets;
    @Getter
    private final List<Release> releases;
    protected final Git git;
    private final Repository repository;
    @Getter
    private final List<Commit> allCommits;
    @Getter
    private final List<Commit> fixingCommits;
    @Getter
    private final Map<Commit, List<String>> buggyFilesPerCommit; // Mappa che lega un fixing-commit ai file "buggy"

    private final String targetName;
    private final Logger logger = CollectLogger.getInstance().getLogger();

    public GitController(String targetName, String gitUrl, List<Release> releases) throws IOException, GitAPIException {
        this.targetName = targetName;
        // Costruzione del percorso in modo sicuro
        String repoPath = REPO_BASE_PATH + File.separator + targetName.toLowerCase();
        this.releases = releases;

        // Gestione robusta del repository
        File repoDir = new File(repoPath);
        if (!repoDir.exists()) {
            logger.info("Clonazione del repository: " + gitUrl);
            this.git = Git.cloneRepository().setURI(gitUrl).setDirectory(repoDir).call();
        } else {
            logger.info("Apertura del repository locale: " + repoPath);
            this.git = Git.open(repoDir);
        }
        this.repository = git.getRepository();

        // Inizializzazione di tutte le liste e mappe
        this.tickets = new ArrayList<>();
        this.allCommits = new ArrayList<>();
        this.fixingCommits = new ArrayList<>();
        this.buggyFilesPerCommit = new HashMap<>();
    }

    /**
     * Scansiona la storia di Git, crea oggetti Commit e li associa alla release corretta.
     */
    public void buildCommitHistory() throws GitAPIException, IOException {
        logger.info("Inizio analisi dei commit per " + targetName);
        Iterable<RevCommit> log = git.log().all().call();

        for (RevCommit revCommit : log) {
            LocalDateTime commitDate = LocalDateTime.ofInstant(revCommit.getCommitterIdent().getWhenAsInstant(), ZoneId.systemDefault());
            Release release = findReleaseForCommit(commitDate);

            if (release != null) {
                Commit newCommit = new Commit(revCommit, release);
                this.allCommits.add(newCommit);
                release.addCommit(newCommit); // Assumendo che questo metodo esista
            }
        }
        // Rimuove le release che non hanno avuto commit (improbabile, ma è una sicurezza)
        releases.removeIf(release -> release.getCommitList().isEmpty());
        // Ordina i commit per data per un'elaborazione cronologica
        allCommits.sort(Comparator.comparing(commit -> commit.getRevCommit().getCommitTime()));
        logger.info("Trovati e processati " + this.allCommits.size() + " commit.");
    }

    private Release findReleaseForCommit(LocalDateTime commitDate) {
        // Le release sono già ordinate per data dal JiraController
        for (Release release : this.releases) {
            if (!commitDate.toLocalDate().isAfter(release.getReleaseDate())) {
                return release;
            }
        }
        // Se il commit è più recente dell'ultima release, appartiene logicamente a quella
        return this.releases.isEmpty() ? null : this.releases.getLast();
    }

    /**
     * Identifica i commit che risolvono bug (fixing-commits) e li collega
     * ai file Java che hanno modificato.
     */
    public void findBuggyFiles() {
        logger.info("Ricerca dei commit che risolvono bug e dei file associati...");
        Map<String, Ticket> ticketMap = new HashMap<>();
        for (Ticket ticket : this.tickets) {
            ticketMap.put(ticket.getTicketKey().toUpperCase(), ticket);
        }




        // Regex migliorata: case-insensitive e cerca la chiave ovunque
        Pattern pattern = Pattern.compile(this.targetName + "-\\d+", Pattern.CASE_INSENSITIVE);

        for (Commit commit : this.allCommits) {



            Matcher matcher = pattern.matcher(commit.getRevCommit().getFullMessage());
            if (matcher.find()) {
                String ticketKey = matcher.group(0).toUpperCase();// Normalizza a maiuscolo

                if (ticketMap.containsKey(ticketKey)) {
                    // Trovato un fixing-commit!
                    commit.setTicket(ticketMap.get(ticketKey));
                    this.fixingCommits.add(commit);

                    // Ora, per questo fixing-commit, troviamo i file che ha modificato
                    try {
                        List<String> modifiedFiles = getModifiedFiles(commit.getRevCommit());
                        this.buggyFilesPerCommit.put(commit, modifiedFiles);
                    } catch (IOException e) {
                        logger.warning("Impossibile analizzare il diff per il commit " + commit.getRevCommit().getName() + ". Errore: " + e.getMessage());
                    }
                }
            }
        }
        logger.info("Trovati " + this.fixingCommits.size() + " commit che risolvono ticket.");
    }

    // Helper per trovare i file .java modificati in un commit
    private List<String> getModifiedFiles(RevCommit commit) throws IOException {
        List<String> modifiedPaths = new ArrayList<>();
        // Se il commit non ha un padre (es. il primo commit), non ha modifiche
        if (commit.getParentCount() == 0) {
            return modifiedPaths;
        }
        // Usa un RevWalk per ottenere il genitore
        try (RevWalk rw = new RevWalk(repository)) {
            RevCommit parent = rw.parseCommit(commit.getParent(0).getId());
            try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                df.setRepository(repository);
                df.setContext(0);
                List<DiffEntry> diffs = df.scan(parent.getTree(), commit.getTree());
                for (DiffEntry diff : diffs) {
                    // Considera solo file .java modificati o aggiunti, ignorando i file di test
                    if ((diff.getChangeType() == DiffEntry.ChangeType.MODIFY || diff.getChangeType() == DiffEntry.ChangeType.ADD)
                            && diff.getNewPath().endsWith(".java") && !diff.getNewPath().contains("/test/")) {
                        modifiedPaths.add(diff.getNewPath());
                    }
                }
            }
        }
        return modifiedPaths;
    }

    public void closeRepo() {
        if (this.git != null) {
            this.git.close();
        }
        logger.info("Repository per " + targetName + " chiuso.");
    }

    // --- Metodi Mapper Corretti per Produrre JSON Valido ---

    public Map<String, JSONObject> getMapTickets() {
        Map<String, JSONObject> mapTickets = new LinkedHashMap<>();
        if (this.tickets == null) return mapTickets;
        this.tickets.sort(Comparator.comparing(Ticket::getCreationDate));
        for (Ticket ticket : this.tickets) {
            Map<String, Object> innerMap = new LinkedHashMap<>();
            List<String> affectedVersionNames = new ArrayList<>();
            for (Release release : ticket.getAffectedVersions()) {
                affectedVersionNames.add(release.getReleaseName());
            }

            innerMap.put("injectedVersion", ticket.getInjectedVersion() != null ? ticket.getInjectedVersion().getReleaseName() : "N/A");
            innerMap.put("openingVersion", ticket.getOpeningVersion() != null ? ticket.getOpeningVersion().getReleaseName() : "N/A");
            innerMap.put("fixedVersion", ticket.getFixedVersion() != null ? ticket.getFixedVersion().getReleaseName() : "N/A");
            innerMap.put("affectedVersions", affectedVersionNames);
            innerMap.put("creationDate", ticket.getCreationDate().toString());
            innerMap.put("resolutionDate", ticket.getResolutionDate().toString());

            mapTickets.put(ticket.getTicketKey(), new JSONObject(innerMap));
        }
        return mapTickets;
    }

    public Map<String, JSONObject> getMapCommits() {
        Map<String, JSONObject> mapCommits = new LinkedHashMap<>();
        for (Commit commit : this.allCommits) {
            Map<String, Object> innerMap = new LinkedHashMap<>();
            RevCommit revCommit = commit.getRevCommit();

            if (commit.getTicket() != null) {
                innerMap.put("ticketKey", commit.getTicket().getTicketKey());
            }
            if (commit.getRelease() != null) {
                innerMap.put("release", commit.getRelease().getReleaseName());
            }
            LocalDateTime commitDate = LocalDateTime.ofInstant(revCommit.getCommitterIdent().getWhenAsInstant(), ZoneId.systemDefault());
            innerMap.put("commitDate", commitDate.toString());

            mapCommits.put(revCommit.getName(), new JSONObject(innerMap));
        }
        return mapCommits;
    }

    public Map<String, String> getMapSummary() {
        Map<String, String> summaryMap = new HashMap<>();
        summaryMap.put(ReportType.RELEASE.toString(), String.valueOf(this.releases.size()));
        summaryMap.put(ReportType.TICKETS.toString(), String.valueOf(this.tickets.size()));
        summaryMap.put(ReportType.COMMITS.toString(), String.valueOf(this.allCommits.size()));
        summaryMap.put("FIXING_COMMITS", String.valueOf(this.fixingCommits.size()));
        return summaryMap;
    }


}
