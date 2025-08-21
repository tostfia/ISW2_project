package org.apache.controller.milestone1;



import lombok.Getter;
import lombok.Setter;
import org.apache.logging.CollectLogger;
import org.apache.model.*;
import org.apache.utilities.Utility;
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
    private final Map<Commit, List<Commit>> bugIntroducingCommitsMap;
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



    public void applyBugginessLabels(List<AnalyzedClass> allClassSnapshots) throws IOException {
        logger.info("Applying bugginess labels (STREAMING mode, memory-safe).");

        // 1. Reset bugginess
        allClassSnapshots.forEach(ac -> {
            ac.setBuggy(false);
            ac.getMethods().forEach(am -> am.getMetrics().setBuggy(false));
        });

        // 2. Indicizza snapshot per file → ma non in memoria per tutto, solo referenza
        Map<String, List<AnalyzedClass>> snapshotsByClassPath = allClassSnapshots.stream()
                .collect(Collectors.groupingBy(AnalyzedClass::getClassName));

        // 3. Processa fixing commits uno per uno
        for (Commit fixingCommit : this.fixingCommits) {
            Ticket ticket = fixingCommit.getTicket();
            List<String> modifiedFiles = this.buggyFilesPerCommit.get(fixingCommit);
            Release fixedVersion = fixingCommit.getRelease();

            if (ticket == null || modifiedFiles == null || modifiedFiles.isEmpty() || fixedVersion == null) {
                continue;
            }

            // --- Calcola introductionVersion ---
            Release introductionVersion = null;
            if (ticket.getAffectedVersions() != null && !ticket.getAffectedVersions().isEmpty()) {
                introductionVersion = ticket.getAffectedVersions().stream()
                        .min(Comparator.comparing(Release::getReleaseDate))
                        .orElse(null);
            }
            if (introductionVersion == null) introductionVersion = ticket.getInjectedVersion();
            if (introductionVersion == null) continue;

            // --- Estrai i metodi modificati SOLO per questo commit ---
            Map<String, Set<String>> modifiedMethodsInFixingCommit =
                    getModifiedMethodsInCommit(fixingCommit.getRevCommit(), modifiedFiles);

            // --- Etichetta file per file ---
            for (String filePath : modifiedFiles) {
                List<AnalyzedClass> snapshots = snapshotsByClassPath.get(filePath);
                if (snapshots == null) continue;

                for (AnalyzedClass classSnapshot : snapshots) {
                    Release snapshotRelease = classSnapshot.getRelease();

                    // Verifica se la release della classe cade nel range [intro, fix)
                    boolean inRange = !snapshotRelease.getReleaseDate().isBefore(introductionVersion.getReleaseDate()) &&
                            snapshotRelease.getReleaseDate().isBefore(fixedVersion.getReleaseDate());

                    if (!inRange) continue;

                    // Marca la classe come buggy
                    classSnapshot.setBuggy(true);

                    // Marca i metodi modificati
                    Set<String> buggyMethods = modifiedMethodsInFixingCommit.getOrDefault(filePath, Collections.emptySet());
                    for (AnalyzedMethod am : classSnapshot.getMethods()) {
                        if (buggyMethods.contains(am.getSignature())) {
                            am.getMetrics().setBuggy(true);
                        }
                    }
                }
            }


            modifiedMethodsInFixingCommit.clear();
        }

        logger.info("Streaming bugginess labeling completed.");
    }


    private Map<String, Set<String>> getModifiedMethodsInCommit(RevCommit commit, List<String> targetFilePaths) throws IOException {
        Map<String, Set<String>> modifiedMethods = new HashMap<>();

        if (commit.getParentCount() == 0) return modifiedMethods;

        try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
             RevWalk rw = new RevWalk(repository)) {

            RevCommit parent = rw.parseCommit(commit.getParent(0).getId());
            df.setRepository(repository);
            df.setContext(0);

            List<DiffEntry> diffs = df.scan(parent.getTree(), commit.getTree());

            for (DiffEntry diff : diffs) {
                String filePath = diff.getNewPath();
                if (!targetFilePaths.contains(filePath) || !filePath.endsWith(".java")) continue;

                String newContent = getFileContentAtCommit(commit, filePath);
                String oldContent = getFileContentAtCommit(parent, filePath);

                if (diff.getChangeType() == DiffEntry.ChangeType.ADD && newContent != null) {
                    // Solo parsing UNA volta
                    List<AnalyzedMethod> methods = Utility.extractMethodRanges(newContent);
                    methods.forEach(m -> modifiedMethods.computeIfAbsent(filePath, k -> new HashSet<>()).add(m.getSignature()));
                }
                else if (diff.getChangeType() == DiffEntry.ChangeType.MODIFY && newContent != null && oldContent != null) {
                    RawText oldText = new RawText(oldContent.getBytes(StandardCharsets.UTF_8));
                    RawText newText = new RawText(newContent.getBytes(StandardCharsets.UTF_8));
                    List<Edit> edits = MyersDiff.INSTANCE.diff(RawTextComparator.DEFAULT, oldText, newText);

                    // Parsing una sola volta
                    List<AnalyzedMethod> newMethods = Utility.extractMethodRanges(newContent);

                    for (Edit edit : edits) {
                        int start = edit.getBeginB();
                        int end = edit.getEndB();
                        newMethods.stream()
                                .filter(m -> m.overlaps(start, end))
                                .forEach(m -> modifiedMethods
                                        .computeIfAbsent(filePath, k -> new HashSet<>())
                                        .add(m.getSignature()));
                    }
                }
            }
        }
        return modifiedMethods;
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