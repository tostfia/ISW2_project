package org.apache.controller.milestone1;


import lombok.Getter;
import lombok.Setter;
import org.apache.logging.Printer;
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
    private  static final String JAVA=".java";
    private static final String TEST="/src/test/";





    public GitController(String targetName, String gitUrl, List<Release> releases) throws IOException, GitAPIException {
        this(targetName, gitUrl, releases, DEFAULT_REPO_BASE_PATH);
    }

    public GitController(String targetName, String gitUrl, List<Release> releases, String customBasePath) throws IOException, GitAPIException {
        this.targetName = targetName;
        this.releases = releases;
        Path repoPath = Paths.get(customBasePath, targetName.toLowerCase());
        File repoDir = repoPath.toFile();
        if (!repoDir.exists()) {
            Printer.print("Cloning repository: " + gitUrl+"\n");
            this.git = Git.cloneRepository().setURI(gitUrl).setDirectory(repoDir).call();
        } else {
            Printer.print("Opening local repository: " + repoPath+"\n");
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
        Printer.printBlue("Starting commit analysis for " + targetName+"\n");
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
        Printer.printBlue("Found and processed " + this.allCommits.size() + " commits across " + releases.size() + " valid releases.\n");
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
        Printer.printBlue("Searching for bug-fixing commits and associated files...\n");
        Map<String, Ticket> ticketMap = new HashMap<>();


        for (Ticket ticket : this.tickets) {
            ticketMap.put(ticket.getTicketKey().toUpperCase(), ticket);

        }

        Printer.printGreen("Found " + ticketMap.size() + " valid bug tickets\n");

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
                        Printer.errorPrint("Cannot analyze diff for commit " + commit.getRevCommit().getName() + ". Error: " + e.getMessage());
                    }
                }
            }
        }
        Printer.printlnBlue("Found " + this.fixingCommits.size() + " bug-fixing commits.\n");
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
                diff.getNewPath().endsWith(JAVA) &&
                !diff.getNewPath().contains("/test/") &&
                !diff.getNewPath().contains(TEST);
    }






    public void closeRepo() {
        try {
            if (this.git != null) {
                this.git.close();
                Printer.printGreen("Repository for " + targetName + " closed successfully.\n");
            }
        } catch (Exception e) {
            Printer.errorPrint("Error closing repository: " + e.getMessage());
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
                if (path.endsWith(JAVA) && !path.contains(TEST)) {
                    try {
                        String content = new String(repository.open(treeWalk.getObjectId(0)).getBytes(), StandardCharsets.UTF_8);
                        allClasses.put(path, content);
                    } catch (IOException e) {
                        Printer.errorPrint("Cannot read file " + path + " in commit " + revCommit.getName()+"\n");
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
                if (entry.getNewPath().endsWith(JAVA) &&
                        !entry.getNewPath().contains(TEST)) {
                    touchedClassesNames.add(entry.getNewPath());
                }
            }
        }
        return touchedClassesNames;
    }

    void labelBugginess(List<AnalyzedClass> classList) {
        Printer.print("Inizio del processo di etichettatura della bugginess per " + classList.size() + " classi.\n");

        Map<String, List<AnalyzedClass>> snapshotsByClassPath = classList.stream()
                .collect(Collectors.groupingBy(AnalyzedClass::getClassName));

        resetMethodsBuggyFlag(classList);

        int totalFixingCommitsConsidered = 0;
        int totalAffectedFilesConsidered = 0;
        int totalClassSnapshotsMatched = 0;
        int totalMethodsLabeledBuggy = 0;

        for (Map.Entry<Commit, List<Commit>> entry : bugIntroducingCommitsMap.entrySet()) {
            int[] results = processFixingCommit(entry, snapshotsByClassPath);
            totalFixingCommitsConsidered++;
            totalAffectedFilesConsidered += results[0];
            totalClassSnapshotsMatched += results[1];
            totalMethodsLabeledBuggy += results[2];
        }

        Printer.printlnBlue(String.format(
                "labelBugginess completata. Processati %d fixing commits, considerati %d file affetti, trovati %d snapshot di classi, etichettati %d metodi come buggy.",
                totalFixingCommitsConsidered, totalAffectedFilesConsidered, totalClassSnapshotsMatched, totalMethodsLabeledBuggy));
        Printer.print("Bugginess etichettata a livello di metodo per le classi analizzate.\n");
    }

    private void resetMethodsBuggyFlag(List<AnalyzedClass> classList) {
        classList.forEach(c -> c.getMethods().forEach(m -> m.setBuggy(false)));
    }

    private int[] processFixingCommit(Map.Entry<Commit, List<Commit>> entry, Map<String, List<AnalyzedClass>> snapshotsByClassPath) {
        Commit fixingCommit = entry.getKey();
        Release fixedVersion = fixingCommit.getRelease();
        if (fixedVersion == null) return new int[]{0, 0, 0};

        List<String> affectedFiles = buggyFilesPerCommit.get(fixingCommit);
        if (affectedFiles == null || affectedFiles.isEmpty()) return new int[]{0, 0, 0};

        int totalAffectedFiles = affectedFiles.size();
        int totalClassSnapshots = 0;
        int totalMethodsLabeled = 0;

        for (Commit bugIntroCommit : entry.getValue()) {
            Release injectedVersion = bugIntroCommit.getRelease();
            if (injectedVersion == null) continue;

            for (String filePath : affectedFiles) {
                List<AnalyzedClass> snapshots = snapshotsByClassPath.get(filePath);
                if (snapshots == null) continue;

                totalClassSnapshots += snapshots.size();
                totalMethodsLabeled += labelSnapshotsBuggy(snapshots, injectedVersion, fixedVersion);
            }
        }
        return new int[]{totalAffectedFiles, totalClassSnapshots, totalMethodsLabeled};
    }

    private int labelSnapshotsBuggy(List<AnalyzedClass> snapshots, Release injectedVersion, Release fixedVersion) {
        int buggyMethods = 0;
        for (AnalyzedClass classSnapshot : snapshots) {
            Release snapshotRelease = classSnapshot.getRelease();
            if (snapshotRelease == null) continue;

            boolean isInjectedBeforeOrDuringSnapshot = !injectedVersion.getReleaseDate().isAfter(snapshotRelease.getReleaseDate());
            boolean isFixedAfterSnapshot = fixedVersion.getReleaseDate().isAfter(snapshotRelease.getReleaseDate());

            if (isInjectedBeforeOrDuringSnapshot && isFixedAfterSnapshot) {
                if (!classSnapshot.getMethods().isEmpty()) {
                    classSnapshot.getMethods().forEach(method -> method.setBuggy(true));
                    buggyMethods += classSnapshot.getMethods().size();
                } else {
                    Printer.printlnBlue("La classe " + classSnapshot.getClassName() + " (Release " + snapshotRelease.getReleaseName() + ") non ha metodi, potrebbe essere enum o interfaccia, le condizioni per etichettare erano soddisfatte.");
                }
            }
        }
        return buggyMethods;
    }

    public void findAllBugIntroducingCommits() {
        if (!validatePreconditions()) return;

        Printer.print("Costruzione della mappa dei commit che introducono bug dai ticket del JiraController...\n");

        this.bugIntroducingCommitsMap.clear();

        Map<Release, List<Commit>> commitsByRelease = groupCommitsByRelease();
        Map<Release, List<Ticket>> ticketsByFixedRelease = groupTicketsByFixedRelease();

        int totalBugIntroCommitsFound = 0;

        for (Commit fixingCommit : this.fixingCommits) {
            int foundForCommit = processFixingCommit(fixingCommit, commitsByRelease, ticketsByFixedRelease);
            totalBugIntroCommitsFound += foundForCommit;
        }

        Printer.printBlue("Costruzione completata. " + this.bugIntroducingCommitsMap.size() +
                " fixing commits mappati a " + totalBugIntroCommitsFound + " bug-introducing commits totali.\n");

        setBuggyFlagForAllIntroCommits();
    }

    private boolean validatePreconditions() {
        if (this.tickets == null || this.tickets.isEmpty()) {
            Printer.printYellow("Nessun ticket ricevuto dal JiraController. Impossibile costruire la mappa dei commit che introducono bug.\n");
            return false;
        }
        if (this.allCommits == null || this.allCommits.isEmpty()) {
            Printer.printYellow("Nessun commit disponibile (allCommits è vuoto). Assicurarsi che buildCommitHistory sia stata chiamata.\n");
            return false;
        }
        if (this.fixingCommits == null || this.fixingCommits.isEmpty()) {
            Printer.printYellow("Nessun fixing commit identificato. Assicurarsi che findBuggyFiles sia stata chiamata.\n");
            return false;
        }
        return true;
    }

    private Map<Release, List<Commit>> groupCommitsByRelease() {
        Map<Release, List<Commit>> map = allCommits.values().stream()
                .filter(c -> c.getRelease() != null)
                .collect(Collectors.groupingBy(Commit::getRelease));
        Printer.print("Mappa dei commit per release creata con " + map.size() + " voci.\n");
        return map;
    }

    private Map<Release, List<Ticket>> groupTicketsByFixedRelease() {
        Map<Release, List<Ticket>> map = this.tickets.stream()
                .filter(t -> t.getFixedVersion() != null)
                .collect(Collectors.groupingBy(Ticket::getFixedVersion));
        Printer.print("Mappa dei ticket per fixed release creata con " + map.size() + " voci.\n");
        return map;
    }

    private int processFixingCommit(Commit fixingCommit, Map<Release, List<Commit>> commitsByRelease, Map<Release, List<Ticket>> ticketsByFixedRelease) {
        Release fixingCommitRelease = fixingCommit.getRelease();
        if (fixingCommitRelease == null) {
            Printer.print("Saltando fixing commit " + fixingCommit.getRevCommit().getName() + " per informazioni sulla release mancanti.\n");
            return 0;
        }

        Printer.print("Processing fixing commit: " + fixingCommit.getRevCommit().getName() +
                ", Release: " + fixingCommitRelease.getReleaseName() + "\n");

        List<Ticket> relatedTickets = ticketsByFixedRelease.getOrDefault(fixingCommitRelease, Collections.emptyList());
        Printer.print("  Trovati " + relatedTickets.size() + " ticket correlati per questa fixing release.\n");

        Set<Commit> bugIntroCommitsForThisFixingCommit = new HashSet<>();

        for (Ticket ticket : relatedTickets) {
            Release injectedRelease = ticket.getInjectedVersion();
            if (injectedRelease == null) {
                Printer.print("  Saltando ticket " + ticket.getTicketKey() + " per Injected Version mancante.\n");
                continue;
            }

            List<Commit> potentialIntroCommits = commitsByRelease.getOrDefault(injectedRelease, Collections.emptyList());
            Printer.print("    Ticket " + ticket.getTicketKey() + ", trovati " + potentialIntroCommits.size() +
                    " commit potenzialmente introduttivi.\n");

            bugIntroCommitsForThisFixingCommit.addAll(potentialIntroCommits);
        }

        if (!bugIntroCommitsForThisFixingCommit.isEmpty()) {
            this.bugIntroducingCommitsMap.put(fixingCommit, new ArrayList<>(bugIntroCommitsForThisFixingCommit));
            Printer.print("Aggiunti " + bugIntroCommitsForThisFixingCommit.size() + " bug-introducing commits per fixing commit " +
                    fixingCommit.getRevCommit().getName() + "\n");
        } else {
            Printer.print("Nessun bug-introducing commit trovato per fixing commit " + fixingCommit.getRevCommit().getName() + "\n");
        }

        return bugIntroCommitsForThisFixingCommit.size();
    }

    private void setBuggyFlagForAllIntroCommits() {
        this.bugIntroducingCommitsMap.values().forEach(
                commitList -> commitList.forEach(commit -> commit.setBuggy(true))
        );
        Printer.printBlue("Flag 'isBuggy' impostato per tutti i commit che introducono bug.\n");
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
        Printer.print("Building commit history map for each file...\n");
        for (Commit commit : allCommits.values()) {
            if (commit.getRevCommit().getParentCount() > 0) {
                try {
                    List<String> touchedFiles = getTouchedClassesNames(commit.getRevCommit());
                    for (String filePath : touchedFiles) {
                        commitsPerFile.computeIfAbsent(filePath, k -> new ArrayList<>()).add(commit);
                    }
                } catch (IOException e) {
                    Printer.printYellow("Error analyzing touched files for commit " + commit.getRevCommit().getName() + ": " + e.getMessage());
                }
            }
        }

        // Sort commit lists by date
        commitsPerFile.values().forEach(commitList ->
                commitList.sort(Comparator.comparing(c -> c.getRevCommit().getCommitTime()))
        );

        Printer.print("File history map completed.\n");
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
                Printer.printYellow("Cannot calculate class diff for " + filePath + " between commits " +
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
            Printer.errorPrint("File " + filePath + " not found in commit " + commit.getName());
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