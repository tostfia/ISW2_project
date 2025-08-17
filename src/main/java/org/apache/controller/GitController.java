package org.apache.controller;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.CollectLogger;
import org.apache.model.AnalyzedClass;
import org.apache.model.Commit;
import org.apache.model.Release;
import org.apache.model.Ticket;
import org.eclipse.jgit.api.BlameCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.blame.BlameResult;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class GitController {
    // FIXED: Path portabile e configurabile
    private static final String DEFAULT_REPO_BASE_PATH = System.getProperty("user.home") + File.separator + "repositories";
    private static final Map<String, BlameResult> GLOBAL_BLAME_CACHE = new ConcurrentHashMap<>();

    // REFACTOR: Costanti per la logica di realismo di SZZ
    private static final long SZZ_MAX_DAYS_THRESHOLD = 365; // Un anno di soglia temporale
    private static final Set<String> SZZ_IGNORE_KEYWORDS = Set.of(
            "refactor", "style", "cleanup", "rename", "reformat", "docs", "javadoc", "comment"
    );

    @Getter
    @Setter
    private List<Ticket> tickets;
    @Getter
    private final List<Release> releases;
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
    private final Map<Commit, List<Commit>> bugIntroducingCommitsMap;
    @Getter
    private Map<String, List<Commit>> commitsPerFile;

    private final String targetName;
    private final Logger logger = CollectLogger.getInstance().getLogger();

    public GitController(String targetName, String gitUrl, List<Release> releases) throws IOException, GitAPIException {
        this(targetName, gitUrl, releases, DEFAULT_REPO_BASE_PATH);
    }

    public GitController(String targetName, String gitUrl, List<Release> releases, String customBasePath) throws IOException, GitAPIException {
        // ... (il costruttore rimane identico)
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

    // ... (buildCommitHistory, findReleaseForCommit, findBuggyFiles, getModifiedFiles, isValidJavaFile, closeRepo, ecc. rimangono identici)
    public void buildCommitHistory() throws GitAPIException, IOException {
        logger.info("Starting commit analysis for " + targetName);
        Iterable<RevCommit> log = git.log().all().call();

        for (RevCommit revCommit : log) {
            LocalDateTime commitDate = LocalDateTime.ofInstant(
                    revCommit.getCommitterIdent().getWhenAsInstant(),
                    ZoneOffset.UTC
            );

            Release release = findReleaseForCommit(commitDate); // USA validReleases

            if (release != null) {
                Commit newCommit = new Commit(revCommit, release);
                this.allCommits.put(newCommit.getRevCommit().getName(), newCommit);
                release.addCommit(newCommit);
            }
        }


        releases.removeIf(release -> release.getCommitList().isEmpty());
        logger.info("Found and processed " + this.allCommits.size() + " commits across " + releases.size() + " valid releases.");
    }

    // IMPROVED: Metodo refactored per chiarezza
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

    // IMPROVED: Better error handling and ticket filtering
    public void findBuggyFiles() {
        logger.info("Searching for bug-fixing commits and associated files...");
        Map<String, Ticket> ticketMap = new HashMap<>();

        // NEW: Filtra i ticket secondo i requisiti del Milestone
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


    // IMPROVED: Better resource management
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

    // IMPROVED: Better resource management in getClassesNameCodeInfos
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

    // IMPROVED: Better exception handling in getTouchedClassesNames
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

    /**
     * REFACTOR: Questo metodo ora è più chiaro nel suo scopo. La logica di "realismo"
     * è applicata nel metodo `findAllBugIntroducingCommits`.
     */
    public void labelBuggynessWithSZZ(List<AnalyzedClass> classList) {
        Map<String, List<AnalyzedClass>> snapshotsByClassPath = classList.stream()
                .collect(Collectors.groupingBy(AnalyzedClass::getClassName));

        classList.forEach(classSnapshot -> classSnapshot.setBuggy(false));

        for (Map.Entry<Commit, List<Commit>> entry : this.bugIntroducingCommitsMap.entrySet()) {
            Commit fixingCommit = entry.getKey();
            Release fixedVersion = fixingCommit.getRelease();

            if (fixedVersion == null) continue; // Sicurezza aggiuntiva

            List<String> affectedFiles = this.buggyFilesPerCommit.get(fixingCommit);
            if (affectedFiles == null || affectedFiles.isEmpty()) continue;

            for (Commit bugIntroCommit : entry.getValue()) {
                Release injectedVersion = bugIntroCommit.getRelease();
                if (injectedVersion == null) continue;

                for (String filePath : affectedFiles) {
                    List<AnalyzedClass> snapshots = snapshotsByClassPath.get(filePath);
                    if (snapshots == null) continue;

                    for (AnalyzedClass classSnapshot : snapshots) {
                        Release snapshotRelease = classSnapshot.getRelease();
                        // Questa logica di propagazione temporale è una forma di "realismo":
                        // una classe è buggy solo nell'intervallo di tempo tra l'introduzione e la correzione.
                        boolean isInjectedBeforeOrDuringSnapshot =
                                !injectedVersion.getReleaseDate().isAfter(snapshotRelease.getReleaseDate());
                        boolean isFixedAfterSnapshot =
                                fixedVersion.getReleaseDate().isAfter(snapshotRelease.getReleaseDate());

                        if (isInjectedBeforeOrDuringSnapshot && isFixedAfterSnapshot) {
                            classSnapshot.setBuggy(true);
                        }
                    }
                }
            }
        }
    }

    // ... (findBugIntroducingCommits rimane identico)
    public List<Commit> findBugIntroducingCommits(Commit fixingCommit, String filePath) throws IOException {
        logger.info("Applying SZZ algorithm for " + filePath + " in fixing commit " + fixingCommit.getRevCommit().getName());
        List<Commit> bugIntroducingCommits = new ArrayList<>();

        RevCommit revCommit = fixingCommit.getRevCommit();

        if (revCommit.getParentCount() == 0) {
            logger.info("Commit " + revCommit.getName() + " has no parents, cannot apply SZZ.");
            return bugIntroducingCommits;
        }

        RevCommit parentCommit = revCommit.getParent(0);

        try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            df.setRepository(repository);
            df.setContext(0);

            List<DiffEntry> diffs = df.scan(parentCommit.getTree(), revCommit.getTree());

            for (DiffEntry diff : diffs) {
                if (diff.getNewPath().equals(filePath)) {
                    EditList edits = df.toFileHeader(diff).toEditList();

                    BlameCommand blameCommand = git.blame();
                    blameCommand.setStartCommit(parentCommit.getId());
                    blameCommand.setFilePath(filePath);

                    try {
                        BlameResult blameResult = blameCommand.call();

                        if (blameResult == null) {
                            logger.warning("Cannot perform blame on " + filePath + " for commit " + parentCommit.getName());
                            continue;
                        }

                        Set<RevCommit> suspiciousCommits = new HashSet<>();

                        for (Edit edit : edits) {
                            int startLine = edit.getBeginA();
                            int endLine = edit.getEndA();

                            for (int i = startLine; i < endLine && i < blameResult.getResultContents().size(); i++) {
                                RevCommit blameCommit = blameResult.getSourceCommit(i);
                                if (blameCommit != null) {
                                    suspiciousCommits.add(blameCommit);
                                }
                            }
                        }

                        for (RevCommit suspiciousCommit : suspiciousCommits) {
                            Commit bugIntroCommit = allCommits.get(suspiciousCommit.getName());
                            if (bugIntroCommit != null) {
                                bugIntroducingCommits.add(bugIntroCommit);
                            }
                        }
                    } catch (GitAPIException e) {
                        logger.warning("Error during blame execution: " + e.getMessage());
                    }
                }
            }
        }

        logger.info("Found " + bugIntroducingCommits.size() + " possible bug-introducing commits in " + filePath);
        return bugIntroducingCommits;
    }


    /**
     * REFACTOR: Questo metodo ora orchestra SZZ e applica i filtri di realismo.
     */
    public void findAllBugIntroducingCommits() {
        logger.info("Applying SZZ algorithm to all fixing commits...");

        for (Commit fixingCommit : fixingCommits) {
            List<String> buggyFiles = buggyFilesPerCommit.get(fixingCommit);
            if (buggyFiles == null || buggyFiles.isEmpty()) {
                continue;
            }

            Set<Commit> potentialIntroducers = new HashSet<>();
            for (String filePath : buggyFiles) {
                try {
                    // SZZ puro trova una lista di candidati
                    List<Commit> candidates = findBugIntroducingCommits(fixingCommit, filePath);
                    potentialIntroducers.addAll(candidates);
                } catch (IOException e) {
                    logger.warning("Error finding bug-introducing commits for " + filePath + ": " + e.getMessage());
                }
            }

            // REFACTOR: Applichiamo la logica di realismo per filtrare i candidati.
            List<Commit> realisticIntroducers = potentialIntroducers.stream()
                    .filter(introCommit -> isRealisticBugIntroducingCommit(introCommit, fixingCommit))
                    .collect(Collectors.toList());

            if (!realisticIntroducers.isEmpty()) {
                bugIntroducingCommitsMap.put(fixingCommit, realisticIntroducers);
                realisticIntroducers.forEach(commit -> commit.setBuggy(true));
            }
        }
        logger.info("Found bug-introducing commits for " + bugIntroducingCommitsMap.size() + " fixing commits (after applying realism filters).");
    }

    /**
     * REFACTOR: Nuovo metodo helper per applicare i filtri di realismo SZZ.
     * Scarta i commit che sono probabilmente falsi positivi.
     *
     * @param introCommit Il commit sospetto che potrebbe aver introdotto il bug.
     * @param fixingCommit Il commit che ha corretto il bug.
     * @return true se il commit è un candidato realistico, false altrimenti.
     */
    private boolean isRealisticBugIntroducingCommit(Commit introCommit, Commit fixingCommit) {
        if (introCommit == null || fixingCommit == null) return false;

        // Filtro 1: Messaggio del Commit (ignora refactoring, stile, ecc.)
        String message = introCommit.getRevCommit().getFullMessage().toLowerCase();
        for (String keyword : SZZ_IGNORE_KEYWORDS) {
            if (message.contains(keyword)) {
                logger.fine("SZZ false positive filtered by keyword '" + keyword + "': " + introCommit.getRevCommit().getName());
                return false;
            }
        }

        // Filtro 2: Proporzionalità Temporale
        LocalDateTime introDate = LocalDateTime.ofInstant(introCommit.getRevCommit().getCommitterIdent().getWhenAsInstant(), ZoneId.systemDefault());
        LocalDateTime fixDate = LocalDateTime.ofInstant(fixingCommit.getRevCommit().getCommitterIdent().getWhenAsInstant(), ZoneId.systemDefault());

        if (Duration.between(introDate, fixDate).toDays() > SZZ_MAX_DAYS_THRESHOLD) {
            logger.fine("SZZ false positive filtered by time threshold: " + introCommit.getRevCommit().getName());
            return false;
        }

        return true; // Se il commit supera tutti i filtri, è considerato realistico.
    }

    // ... (tutti gli altri metodi come getClassesForRelease, buildFileCommitHistoryMap, ecc. rimangono identici)
    public List<AnalyzedClass> getClassesForRelease(Release release) throws IOException {
        List<AnalyzedClass> classList = new ArrayList<>();
        if (release.getCommitList().isEmpty()) {
            return classList;
        }

        Commit lastCommit = release.getCommitList().getLast();
        Map<String, String> classesNameCodeMap = getClassesNameCodeInfos(lastCommit.getRevCommit());

        for (Map.Entry<String, String> classInfo : classesNameCodeMap.entrySet()) {
            String className = classInfo.getKey();
            AnalyzedClass ac = new AnalyzedClass(className, classInfo.getValue(), release);

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

    public List<MethodChangeStats> calculateMethodChangeHistory(List<Commit> methodCommits, String filePath, String signature) {
        List<MethodChangeStats> changeStats = new ArrayList<>();

        for (int i = 1; i < methodCommits.size(); i++) {
            Commit currentCommit = methodCommits.get(i);
            Commit parentCommit = methodCommits.get(i - 1);

            try {
                String currentBody = getMethodBodyAtCommit(currentCommit.getRevCommit(), filePath, signature);
                String parentBody = getMethodBodyAtCommit(parentCommit.getRevCommit(), filePath, signature);

                if (currentBody == null || parentBody == null) {
                    continue;
                }

                RawText currentText = new RawText(currentBody.getBytes(StandardCharsets.UTF_8));
                RawText parentText = new RawText(parentBody.getBytes(StandardCharsets.UTF_8));
                EditList edits = new EditList();
                edits.addAll(MyersDiff.INSTANCE.diff(RawTextComparator.DEFAULT, parentText, currentText));

                int linesAdded = 0;
                int linesDeleted = 0;
                for (Edit edit : edits) {
                    linesDeleted += edit.getEndA() - edit.getBeginA();
                    linesAdded += edit.getEndB() - edit.getBeginB();
                }
                changeStats.add(new MethodChangeStats(linesAdded, linesDeleted));

            } catch (IOException e) {
                logger.warning("Cannot calculate method diff " + signature + " between commits " +
                        parentCommit.getRevCommit().getName() + " and " + currentCommit.getRevCommit().getName());
            }
        }
        return changeStats;
    }

    private String getMethodBodyAtCommit(RevCommit commit, String filePath, String signature) throws IOException {
        try (TreeWalk treeWalk = TreeWalk.forPath(repository, filePath, commit.getTree())) {
            if (treeWalk != null) {
                byte[] data = repository.open(treeWalk.getObjectId(0)).getBytes();
                String fileContent = new String(data, StandardCharsets.UTF_8);

                return new JavaParser().parse(fileContent).getResult()
                        .flatMap(cu -> cu.findFirst(MethodDeclaration.class, md -> md.getSignature().toString().equals(signature)))
                        .flatMap(MethodDeclaration::getBody)
                        .map(BlockStmt::toString)
                        .orElse(null);
            }
        } catch (Exception e) {
            logger.fine("File " + filePath + " not found or not parseable in commit " + commit.getName());
        }
        return null;
    }




    public record MethodChangeStats(int linesAdded, int linesDeleted) {}



    public BlameResult performBlame(String filePath, RevCommit snapshotCommit) throws GitAPIException, IOException {
        // Creiamo una chiave unica per lo stato di un file in un dato commit.
        String cacheKey = filePath + ":" + snapshotCommit.getName();

        // 1. Controlla prima la cache globale.
        BlameResult cachedResult = GLOBAL_BLAME_CACHE.get(cacheKey);
        if (cachedResult != null) {
            logger.fine("Cache HIT per il blame di: " + filePath);
            return cachedResult;
        }

        // 2. Se non è in cache, esegui l'operazione costosa.
        logger.info("Cache MISS. Eseguo 'git blame' per: " + filePath);
        BlameCommand blameCommand = this.git.blame();
        blameCommand.setStartCommit(snapshotCommit.getId());
        blameCommand.setFilePath(filePath);
        BlameResult result = blameCommand.call();

        // 3. Salva il risultato nella cache globale prima di restituirlo.
        if (result != null) {
            GLOBAL_BLAME_CACHE.put(cacheKey, result);
        }

        return result;
    }


    public List<Commit> extractCommitsFromBlameResult(BlameResult blameResult, int startLine, int endLine) {
        Set<Commit> touchingCommits = new HashSet<>();
        if (blameResult == null) {
            return new ArrayList<>();
        }

        // Le linee in BlameResult sono 0-indexed, mentre JavaParser è 1-indexed. Convertiamo.
        int start = Math.max(0, startLine - 1);
        int end = Math.min(blameResult.getResultContents().size() - 1, endLine - 1);

        for (int i = start; i <= end; i++) {
            RevCommit sourceCommit = blameResult.getSourceCommit(i);
            if (sourceCommit != null) {
                // Usa la mappa 'allCommits' (che è un campo di questa classe) per una conversione veloce.
                Commit commit = this.allCommits.get(sourceCommit.getName());
                if (commit != null) {
                    touchingCommits.add(commit);
                }
            }
        }
        return new ArrayList<>(touchingCommits);
    }
}