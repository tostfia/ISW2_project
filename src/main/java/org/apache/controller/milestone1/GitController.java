package org.apache.controller.milestone1;


import lombok.Getter;
import lombok.Setter;
import org.apache.logging.CollectLogger;
import org.apache.model.*;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import org.eclipse.jgit.diff.*;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;

import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.time.LocalDateTime;

import java.time.ZoneOffset;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class GitController {
    // FIXED: Path portabile e configurabile
    private static final String DEFAULT_REPO_BASE_PATH = System.getProperty("user.home") + File.separator + "repo";


    @Getter
    @Setter
    private List<Ticket> tickets;
    @Getter
    private final List<Release> releases;
    @Getter
    protected final Git git;
    @Getter
    private final Repository repository;
    @Getter
    private final Map<String, Commit> allCommits;
    @Getter
    private final List<Commit> fixingCommits;
    @Getter
    private final Map<Commit, List<String>> buggyFilesPerCommit;
    @Getter
    private Map<Commit, List<Commit>> bugIntroducingCommitsMap;
    @Getter
    private Map<String, List<Commit>> commitsPerFile;
    private final String targetName ;



    private final Logger logger = CollectLogger.getInstance().getLogger();

    public GitController(String targetName, String gitUrl, List<Release> releases) throws IOException, GitAPIException {
        this(targetName, gitUrl, releases, DEFAULT_REPO_BASE_PATH);
    }

    public GitController(String targetName, String gitUrl, List<Release> releases, String customBasePath) throws IOException, GitAPIException {
        this.targetName = targetName;
        this.releases = releases;
        Path repoPath = Paths.get(customBasePath, targetName.toLowerCase());
        File repoDir = repoPath.toFile();
        if (!repoDir.exists()) {
            logger.info("Cloning repository: " + gitUrl);
            this.git = Git.cloneRepository().setURI(gitUrl).setDirectory(repoDir).call();
        } else {
            logger.info("Opening local repository: " + repoPath);
            this.git = Git.open(repoDir);
        }
        this.repository = git.getRepository();
        this.tickets = new ArrayList<>();
        this.allCommits = new HashMap<>();
        this.fixingCommits = new ArrayList<>();
        this.buggyFilesPerCommit = new HashMap<>();
        this.bugIntroducingCommitsMap = new HashMap<>();
        this.commitsPerFile = new HashMap<>();
    }

    public void buildCommitHistory() throws GitAPIException, IOException {
        logger.info("Starting commit analysis for " + targetName);
        Iterable<RevCommit> log = git.log().all().call();

        for (RevCommit revCommit : log) {
            LocalDateTime commitDate = LocalDateTime.ofInstant(
                    revCommit.getCommitterIdent().getWhenAsInstant(),
                    ZoneOffset.UTC
            );

            Release release = findReleaseForCommit(commitDate);

            if (release != null) {
                Commit newCommit = new Commit(revCommit, release);
                this.allCommits.put(newCommit.getRevCommit().getName(), newCommit);
                release.addCommit(newCommit);
            }
        }


        releases.removeIf(release -> release.getCommitList().isEmpty());
        logger.info("Found and processed " + this.allCommits.size() + " commits across " + releases.size() + " valid releases.");
    }


    private Release findReleaseForCommit(LocalDateTime commitDate) {
        if (releases == null || releases.isEmpty()) {
            return null;
        }


        for (Release release : releases) {
            if (!commitDate.toLocalDate().isAfter(release.getReleaseDate())) {
                return release;
            }
        }

        // Se il commit è più recente dell'ultima release valida, appartiene a quella
        return releases.getLast();
    }


    public void findBuggyFiles() {
        logger.info("Searching for bug-fixing commits and associated files...");
        Map<String, Ticket> ticketMap = new HashMap<>();


        for (Ticket ticket : this.tickets) {
            ticketMap.put(ticket.getTicketKey().toUpperCase(), ticket);

        }

        logger.info("Found " + ticketMap.size() + " valid bug tickets");

        Pattern pattern = Pattern.compile(this.targetName + "-\\d+", Pattern.CASE_INSENSITIVE);

        for (Commit commit : this.allCommits.values()) {
            Matcher matcher = pattern.matcher(commit.getRevCommit().getFullMessage());
            if (matcher.find()) {
                String ticketKey = matcher.group(0).toUpperCase();

                if (ticketMap.containsKey(ticketKey)) {
                    commit.setTicket(ticketMap.get(ticketKey));
                    this.fixingCommits.add(commit);

                    try {
                        List<String> modifiedFiles = getModifiedFiles(commit.getRevCommit());
                        this.buggyFilesPerCommit.put(commit, modifiedFiles);
                    } catch (IOException e) {
                        logger.warning("Cannot analyze diff for commit " + commit.getRevCommit().getName() + ". Error: " + e.getMessage());
                    }
                }
            }
        }
        logger.info("Found " + this.fixingCommits.size() + " bug-fixing commits.");
    }



    private List<String> getModifiedFiles(RevCommit commit) throws IOException {
        List<String> modifiedPaths = new ArrayList<>();

        if (commit.getParentCount() == 0) {
            return modifiedPaths;
        }

        try (RevWalk rw = new RevWalk(repository);
             DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {

            RevCommit parent = rw.parseCommit(commit.getParent(0).getId());
            df.setRepository(repository);
            df.setContext(0);

            List<DiffEntry> diffs = df.scan(parent.getTree(), commit.getTree());
            for (DiffEntry diff : diffs) {
                if (isValidJavaFile(diff)) {
                    modifiedPaths.add(diff.getNewPath());
                }
            }
        }
        return modifiedPaths;
    }

    // NEW: Helper method per validare file Java
    private boolean isValidJavaFile(DiffEntry diff) {
        return (diff.getChangeType() == DiffEntry.ChangeType.MODIFY ||
                diff.getChangeType() == DiffEntry.ChangeType.ADD) &&
                diff.getNewPath().endsWith(".java") &&
                !diff.getNewPath().contains("/test/") &&
                !diff.getNewPath().contains("/src/test/");
    }






    public void closeRepo() {
        try {
            if (this.git != null) {
                this.git.close();
                logger.info("Repository for " + targetName + " closed successfully.");
            }
        } catch (Exception e) {
            logger.warning("Error closing repository: " + e.getMessage());
        }
    }


    private Map<String, String> getClassesNameCodeInfos(RevCommit revCommit) throws IOException {
        Map<String, String> allClasses = new HashMap<>();
        RevTree tree = revCommit.getTree();

        try (TreeWalk treeWalk = new TreeWalk(repository)) {
            treeWalk.addTree(tree);
            treeWalk.setRecursive(true);

            while (treeWalk.next()) {
                String path = treeWalk.getPathString();
                if (path.endsWith(".java") && !path.contains("/src/test/")) {
                    try {
                        String content = new String(repository.open(treeWalk.getObjectId(0)).getBytes(), StandardCharsets.UTF_8);
                        allClasses.put(path, content);
                    } catch (IOException e) {
                        logger.warning("Cannot read file " + path + " in commit " + revCommit.getName());
                    }
                }
            }
        }
        return allClasses;
    }


    private List<String> getTouchedClassesNames(RevCommit commit) throws IOException {
        List<String> touchedClassesNames = new ArrayList<>();

        if (commit.getParentCount() == 0) {
            return touchedClassesNames;
        }

        try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
             ObjectReader reader = repository.newObjectReader()) {

            RevCommit commitParent = commit.getParent(0);
            diffFormatter.setRepository(repository);

            CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
            ObjectId newTree = commit.getTree();
            newTreeIter.reset(reader, newTree);

            CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
            ObjectId oldTree = commitParent.getTree();
            oldTreeIter.reset(reader, oldTree);

            List<DiffEntry> entries = diffFormatter.scan(oldTreeIter, newTreeIter);
            for (DiffEntry entry : entries) {
                if (entry.getNewPath().endsWith(".java") &&
                        !entry.getNewPath().contains("/src/test/")) {
                    touchedClassesNames.add(entry.getNewPath());
                }
            }
        }
        return touchedClassesNames;
    }

    public void labelBugginess(List<AnalyzedClass> classList) {
        logger.info("Inizio del processo di etichettatura della bugginess per " + classList.size() + " classi.");

        // Mappa per accedere rapidamente agli snapshot di classe tramite il loro percorso (nome)
        // Le chiavi qui saranno i percorsi normalizzati delle classi
        Map<String, List<AnalyzedClass>> snapshotsByClassPath = classList.stream()
                .collect(Collectors.groupingBy(AnalyzedClass::getClassName));
        logger.info("Mappa degli snapshot per percorso classe creata. Percorsi classe unici: " + snapshotsByClassPath.size());
        // Se vuoi vedere alcuni esempi di chiavi, puoi scommentare la riga seguente:
        // snapshotsByClassPath.keySet().stream().limit(5).forEach(key -> logger.fine("  Esempio di percorso chiave (snapshotsByClassPath): '" + key + "'"));

        // Inizialmente, imposta tutti i metodi di tutte le classi a non buggy
        classList.forEach(classSnapshot ->
                classSnapshot.getMethods().forEach(method -> method.setBuggy(false))
        );
        logger.info("Tutti i metodi sono stati inizialmente impostati come non buggy.");

        // Contatori per riassumere l'attività, utili per il debugging
        int totalFixingCommitsConsidered = 0;
        int totalAffectedFilesConsidered = 0;
        int totalClassSnapshotsMatched = 0;
        int totalMethodsLabeledBuggy = 0;


        logger.info("Iterazione attraverso " + this.bugIntroducingCommitsMap.size() + " voci di fixing commit che introducono bug.");
        for (Map.Entry<Commit, List<Commit>> entry : this.bugIntroducingCommitsMap.entrySet()) {
            Commit fixingCommit = entry.getKey();
            Release fixedVersion = fixingCommit.getRelease();
            totalFixingCommitsConsidered++; // Incrementa il contatore dei fixing commit considerati

            if (fixedVersion == null) {
                logger.fine("Skipping fixing commit " + fixingCommit.getRevCommit().getName() + " (Ticket: " + (fixingCommit.getTicket() != null ? fixingCommit.getTicket().getTicketKey() : "N/A") + ") a causa di fixedVersion nulla.");
                continue;
            }
            logger.fine("Processing fixing commit: " + fixingCommit.getRevCommit().getName() + " (Fixed Version Date: " + fixedVersion.getReleaseDate() + ")");


            List<String> affectedFiles = this.buggyFilesPerCommit.get(fixingCommit);
            if (affectedFiles == null || affectedFiles.isEmpty()) {
                logger.fine("Nessun file affetto trovato per il fixing commit: " + fixingCommit.getRevCommit().getName() + ". Saltando. (Questo potrebbe essere un problema se accade spesso)");
                // Se sospetti un disallineamento, puoi loggare le chiavi presenti in buggyFilesPerCommit:
                // logger.fine("  Chiavi presenti in buggyFilesPerCommit: " + String.join(", ", this.buggyFilesPerCommit.keySet().stream().map(c -> c.getRevCommit().getName()).limit(5).collect(Collectors.toList())));
                continue;
            }
            logger.fine("Trovati " + affectedFiles.size() + " file affetti per il fixing commit: " + fixingCommit.getRevCommit().getName());
            totalAffectedFilesConsidered += affectedFiles.size();


            for (Commit bugIntroCommit : entry.getValue()) {
                Release injectedVersion = bugIntroCommit.getRelease();
                if (injectedVersion == null) {
                    logger.fine("Saltando il commit che introduce il bug " + bugIntroCommit.getRevCommit().getName() + " a causa di injectedVersion nulla.");
                    continue;
                }
                logger.fine("  Considerando il commit che introduce il bug: " + bugIntroCommit.getRevCommit().getName() + " (Injected Version Date: " + injectedVersion.getReleaseDate() + ")");


                for (String filePath : affectedFiles) { // Questo filePath è già normalizzato da getModifiedFiles
                    logger.fine("    Tentando di trovare snapshot per filePath (da affectedFiles): '" + filePath + "'");
                    List<AnalyzedClass> snapshots = snapshotsByClassPath.get(filePath);

                    if (snapshots == null) {

                        continue;
                    }

                    totalClassSnapshotsMatched += snapshots.size();


                    for (AnalyzedClass classSnapshot : snapshots) {
                        Release snapshotRelease = classSnapshot.getRelease();
                        if (snapshotRelease == null) {
                            logger.fine("      Lo snapshot della classe " + classSnapshot.getClassName() + " ha una release nulla. Saltando.");
                            continue;
                        }

                        boolean isInjectedBeforeOrDuringSnapshot =
                                !injectedVersion.getReleaseDate().isAfter(snapshotRelease.getReleaseDate());
                        boolean isFixedAfterSnapshot =
                                fixedVersion.getReleaseDate().isAfter(snapshotRelease.getReleaseDate());

                        logger.fine(String.format("        Classe: '%s', Release Snapshot: %s (%s). Injected: %s, Fixed: %s. Condizioni: Injected<=Snapshot (%b), Fixed>Snapshot (%b)",
                                classSnapshot.getClassName(), snapshotRelease.getReleaseName(), snapshotRelease.getReleaseDate(),
                                injectedVersion.getReleaseDate(), fixedVersion.getReleaseDate(),
                                isInjectedBeforeOrDuringSnapshot, isFixedAfterSnapshot));


                        if (isInjectedBeforeOrDuringSnapshot && isFixedAfterSnapshot) {
                            if (!classSnapshot.getMethods().isEmpty()) {
                                classSnapshot.getMethods().forEach(method -> method.setBuggy(true));
                                totalMethodsLabeledBuggy += classSnapshot.getMethods().size();
                            } else {
                                logger.warning("          ATTENZIONE: La classe " + classSnapshot.getClassName() + " (Release " + snapshotRelease.getReleaseName() + ") non ha metodi, ma le condizioni per etichettare erano soddisfatte. (Probabile file di interfaccia/enum senza metodi)");
                            }
                        }
                    }
                }
            }
        }
        logger.info(String.format("labelBugginess completata. Processati %d fixing commits, considerati %d file affetti, trovati %d snapshot di classi, etichettati %d metodi come buggy.",
                totalFixingCommitsConsidered, totalAffectedFilesConsidered, totalClassSnapshotsMatched, totalMethodsLabeledBuggy));
        logger.info("Bugginess etichettata a livello di metodo per le classi analizzate.");
    }

    public void findAllBugIntroducingCommits() {
        if (this.tickets == null || this.tickets.isEmpty()) {
            logger.warning("Nessun ticket ricevuto dal JiraController. Impossibile costruire la mappa dei commit che introducono bug.");
            return;
        }
        // Il controllo su allCommits e fixingCommits è stato spostato qui per completezza
        if (this.allCommits == null || this.allCommits.isEmpty()) {
            logger.warning("Nessun commit disponibile (allCommits è vuoto). Assicurarsi che buildCommitHistory sia stata chiamata.");
            return;
        }
        if (this.fixingCommits == null || this.fixingCommits.isEmpty()) {
            logger.warning("Nessun fixing commit identificato. Assicurarsi che findBuggyFiles sia stata chiamata.");
            return;
        }

        logger.info("Costruzione della mappa dei commit che introducono bug dai ticket del JiraController...");

        this.bugIntroducingCommitsMap.clear();

        Map<Release, List<Commit>> commitsByRelease = allCommits.values().stream()
                .filter(c -> c.getRelease() != null)
                .collect(Collectors.groupingBy(Commit::getRelease));
        logger.fine("Mappa dei commit per release creata con " + commitsByRelease.size() + " voci.");


        Map<Release, List<Ticket>> ticketsByFixedRelease = this.tickets.stream()
                .filter(t -> t.getFixedVersion() != null)
                .collect(Collectors.groupingBy(Ticket::getFixedVersion));
        logger.fine("Mappa dei ticket per fixed release creata con " + ticketsByFixedRelease.size() + " voci.");

        int totalBugIntroCommitsFound = 0;

        for (Commit fixingCommit : this.fixingCommits) {
            Release fixingCommitRelease = fixingCommit.getRelease();
            if (fixingCommitRelease == null) {
                logger.fine("Saltando il fixing commit " + fixingCommit.getRevCommit().getName() + " (ticket: " + (fixingCommit.getTicket() != null ? fixingCommit.getTicket().getTicketKey() : "N/A") + ") a causa di informazioni sulla release mancanti.");
                continue;
            }
            logger.fine("Processing fixing commit: " + fixingCommit.getRevCommit().getName() + ", Release: " + fixingCommitRelease.getReleaseName());

            List<Ticket> relatedTickets = ticketsByFixedRelease.getOrDefault(fixingCommitRelease, Collections.emptyList());
            logger.fine("  Trovati " + relatedTickets.size() + " ticket correlati per questa fixing release.");

            Set<Commit> bugIntroCommitsForThisFixingCommit = new HashSet<>();

            for (Ticket ticket : relatedTickets) {
                Release injectedRelease = ticket.getInjectedVersion();
                if (injectedRelease == null) {
                    logger.fine("  Saltando il ticket " + ticket.getTicketKey() + " a causa della Injected Version mancante.");
                    continue;
                }
                logger.fine("    Ticket " + ticket.getTicketKey() + ", Injected Release: " + injectedRelease.getReleaseName());

                List<Commit> potentialIntroCommitsInRelease = commitsByRelease.getOrDefault(injectedRelease, Collections.emptyList());
                logger.fine("    Trovati " + potentialIntroCommitsInRelease.size() + " commit potenzialmente introduttivi nella Injected Release.");

                bugIntroCommitsForThisFixingCommit.addAll(potentialIntroCommitsInRelease);
            }

            if (!bugIntroCommitsForThisFixingCommit.isEmpty()) {
                this.bugIntroducingCommitsMap.put(fixingCommit, new ArrayList<>(bugIntroCommitsForThisFixingCommit));
                totalBugIntroCommitsFound += bugIntroCommitsForThisFixingCommit.size();
                logger.fine("Aggiunti " + bugIntroCommitsForThisFixingCommit.size() + " bug-introducing commits per fixing commit " + fixingCommit.getRevCommit().getName());
            } else {
                logger.fine("Nessun bug-introducing commit realistico trovato per il fixing commit: " + fixingCommit.getRevCommit().getName());
            }
        }

        logger.info("Costruzione completata. " + this.bugIntroducingCommitsMap.size() +
                " fixing commits mappati a " + totalBugIntroCommitsFound + " bug-introducing commits totali (basato su Proportion).");

        this.bugIntroducingCommitsMap.values().forEach(
                commitList -> commitList.forEach(commit -> commit.setBuggy(true))
        );
        logger.info("Flag 'isBuggy' impostato per tutti i commit che introducono bug.");
    }









    public List<AnalyzedClass> getClassesForRelease(Release release) throws IOException {
        List<AnalyzedClass> classList = new ArrayList<>();
        if (release.getCommitList().isEmpty()) {
            return classList;
        }

        Commit lastCommit = release.getCommitList().getLast();
        Map<String, String> classesNameCodeMap = getClassesNameCodeInfos(lastCommit.getRevCommit());

        for (Map.Entry<String, String> classInfo : classesNameCodeMap.entrySet()) {
            String className = classInfo.getKey();

            AnalyzedClass ac = getAnalyzedClass(release, classInfo, className);


            List<Commit> fullHistory = this.commitsPerFile.get(className);
            if (fullHistory != null) {
                ac.setTouchingClassCommitList(new ArrayList<>(fullHistory));
            }
            classList.add(ac);
        }
        return classList;
    }



    public void buildFileCommitHistoryMap() {
        logger.info("Building commit history map for each file...");
        for (Commit commit : allCommits.values()) {
            if (commit.getRevCommit().getParentCount() > 0) {
                try {
                    List<String> touchedFiles = getTouchedClassesNames(commit.getRevCommit());
                    for (String filePath : touchedFiles) {
                        commitsPerFile.computeIfAbsent(filePath, k -> new ArrayList<>()).add(commit);
                    }
                } catch (IOException e) {
                    logger.warning("Error analyzing touched files for commit " + commit.getRevCommit().getName() + ": " + e.getMessage());
                }
            }
        }

        // Sort commit lists by date
        commitsPerFile.values().forEach(commitList ->
                commitList.sort(Comparator.comparing(c -> c.getRevCommit().getCommitTime()))
        );

        logger.info("File history map completed.");
    }



    public List<ClassChangeStats> calculateClassChangeHistory(List<Commit> classCommits, String filePath) {
        List<ClassChangeStats> changeStats = new ArrayList<>();

        for (int i = 1; i < classCommits.size(); i++) {
            Commit currentCommit = classCommits.get(i);
            Commit parentCommit = classCommits.get(i - 1);

            try {
                String currentContent = getFileContentAtCommit(currentCommit.getRevCommit(), filePath);
                String parentContent = getFileContentAtCommit(parentCommit.getRevCommit(), filePath);

                if (currentContent == null || parentContent == null) {
                    continue;
                }

                RawText currentText = new RawText(currentContent.getBytes(StandardCharsets.UTF_8));
                RawText parentText = new RawText(parentContent.getBytes(StandardCharsets.UTF_8));
                EditList edits = new EditList();
                edits.addAll(MyersDiff.INSTANCE.diff(RawTextComparator.DEFAULT, parentText, currentText));

                int linesAdded = 0;
                int linesDeleted = 0;
                for (Edit edit : edits) {
                    linesDeleted += edit.getEndA() - edit.getBeginA();
                    linesAdded += edit.getEndB() - edit.getBeginB();
                }
                changeStats.add(new ClassChangeStats(linesAdded, linesDeleted));

            } catch (IOException e) {
                logger.warning("Cannot calculate class diff for " + filePath + " between commits " +
                        parentCommit.getRevCommit().getName() + " and " + currentCommit.getRevCommit().getName());
            }
        }
        return changeStats;

    }


    private String getFileContentAtCommit(RevCommit commit, String filePath) throws IOException {
        try (TreeWalk treeWalk = TreeWalk.forPath(repository, filePath, commit.getTree())) {
            if (treeWalk != null) {
                byte[] data = repository.open(treeWalk.getObjectId(0)).getBytes();
                return new String(data, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            logger.fine("File " + filePath + " not found in commit " + commit.getName());
        }
        return null;
    }

    public record ClassChangeStats(int linesAdded, int linesDeleted) {}

    public String getRepoPath() {
        return DEFAULT_REPO_BASE_PATH+ File.separator + targetName.toLowerCase() + File.separator;
    }


    private static AnalyzedClass getAnalyzedClass(Release release, Map.Entry<String, String> classInfo, String className) {
        String packageName = "";
        String fileName = className;

        int lastSlashIndex = className.lastIndexOf('/');
        if (lastSlashIndex != -1) {
            packageName = className.substring(0, lastSlashIndex);
            fileName = className.substring(lastSlashIndex + 1);
        }
        // Questo costruttore non dovrebbe parsare i metodi o popolare la loro storia.
        // Tale logica è stata spostata in populateMethodsForAnalyzedClass.
        return new AnalyzedClass(className, classInfo.getValue(), release,packageName,fileName);
    }



}