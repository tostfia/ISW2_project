package org.apache.controller;

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
    @Getter
    private final Repository repository;

    @Getter
    private final List<Commit> allCommits;
    @Getter
    private final List<Commit> fixingCommits;
    @Getter
    private final Map<Commit, List<String>> buggyFilesPerCommit;// Mappa che lega un fixing-commit ai file "buggy"
    @Getter
    private final Map<Commit, List<Commit>> bugIntroducingCommitsMap; // Mappa fixing commit -> bug-introducing commits


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
        // Nel costruttore, aggiungi:
        this.bugIntroducingCommitsMap = new HashMap<>();
    }


    // --- Metodi per raccogliere commit associarli ai ticket e alla release di jira ---
    //Scansiona la storia di Git, crea oggetti Commit e li associa alla release corretta.
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

    public List<AnalyzedClass> processClass(List<Release> releaseList,
                                            List<Commit> commitList) throws IOException {
        List<AnalyzedClass> classList = new ArrayList<>();
        List<Commit> lastCommitsList = new ArrayList<>();

        // For each release we want to take all its classes, so we check their last commit
        for (Release release : releaseList) {
            // Order every commit list for each release
            release.getCommitList().sort(Comparator.comparing(commit -> commit.getRevCommit().getCommitterIdent().getWhen()));
            lastCommitsList.add(release.getCommitList().getLast());
        }

        // For each last commit of each release...
        for(Commit lastCommit: lastCommitsList){
            // Get a map of class name to class code for the actual release
            Map<String, String> classesNameCodeMap = getClassesNameCodeInfos(lastCommit.getRevCommit());
            for(Map.Entry<String, String> classInfo : classesNameCodeMap.entrySet()){
                classList.add(new AnalyzedClass(classInfo.getKey(), classInfo.getValue(), lastCommit.getRelease()));
            }
        }

        // Set the commit list that touches the class for each class
        setTouchingClassesCommits(classList, commitList);

        // Order classes by name
        classList.sort(Comparator.comparing(AnalyzedClass::getClassName));

        return classList;
    }

    /**
     * From a commit takes the class name and code, excluding the test classes
     * @param revCommit the commit to take the classes from
     * @return a map of strings, the class names, to strings, the class code
     * @throws IOException for using jGit apis
     */
    private Map<String, String> getClassesNameCodeInfos(RevCommit revCommit) throws IOException {
        Map<String, String> allClasses = new HashMap<>();
        RevTree tree = revCommit.getTree();
        TreeWalk treeWalk = new TreeWalk(repository);
        treeWalk.addTree(tree);
        treeWalk.setRecursive(true);
        while(treeWalk.next()) {
            if(treeWalk.getPathString().contains(".java") && !treeWalk.getPathString().contains("/src/test/")) {
                allClasses.put(treeWalk.getPathString(), new String(repository.open(treeWalk.getObjectId(0)).getBytes(), StandardCharsets.UTF_8));
            }
        }
        treeWalk.close();
        return allClasses;
    }



    /**
     * Get the class names touched by a commit
     * @param commit the commit that touches the classes
     * @return a list of touched class names
     * @throws IOException if there is some failure reading the classes
     */
    private List<String> getTouchedClassesNames(RevCommit commit) throws IOException  {
        List<String> touchedClassesNames = new ArrayList<>();

        // The diff formatter will format the differences between 2 commits
        try(DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
            ObjectReader reader = repository.newObjectReader()) {
            RevCommit commitParent = commit.getParent(0);
            diffFormatter.setRepository(repository);

            // Get the current commit tree
            CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
            ObjectId newTree = commit.getTree();
            newTreeIter.reset(reader, newTree);

            // Get the parent commit tree of the current one
            CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
            ObjectId oldTree = commitParent.getTree();
            oldTreeIter.reset(reader, oldTree);

            // Get the names
            List<DiffEntry> entries = diffFormatter.scan(oldTreeIter, newTreeIter);
            for(DiffEntry entry : entries) {
                if(entry.getNewPath().contains(".java") && !entry.getNewPath().contains("/src/test/")) {
                    touchedClassesNames.add(entry.getNewPath());
                }
            }
        } catch(ArrayIndexOutOfBoundsException ignored) {
            // ignoring when no parent is found
        }
        return touchedClassesNames;
    }

    public void labelBuggynessWithSZZ(List<AnalyzedClass> classList) {
        // 1. Inizializza tutto come "clean". Questo è il nostro stato di default.
        for (AnalyzedClass classSnapshot : classList) {
            classSnapshot.setBuggy(false);
        }

        // 2. Itera su ogni bug che SZZ ha trovato.
        //    'bugIntroducingCommitsMap' contiene: FixingCommit -> Lista di BugIntroducingCommits
        for (Map.Entry<Commit, List<Commit>> entry : this.bugIntroducingCommitsMap.entrySet()) {
            Commit fixingCommit = entry.getKey();
            Release fixedVersion = fixingCommit.getRelease(); // La release in cui il bug è stato RISOLTO.

            List<Commit> bugIntroducingCommits = entry.getValue();

            for (Commit bugIntroCommit : bugIntroducingCommits) {
                Release injectedVersion = bugIntroCommit.getRelease(); // La release in cui il bug è stato INTRODOTTO.

                // Quali file sono stati toccati da questo bug?
                List<String> affectedFiles = this.buggyFilesPerCommit.get(fixingCommit);
                if (affectedFiles == null) continue;

                // 3. Applica la regola di etichettatura a tutti gli snapshot.
                for (AnalyzedClass classSnapshot : classList) {

                    // Se lo snapshot non riguarda uno dei file affetti, salta.
                    if (!affectedFiles.contains(classSnapshot.getClassName())) {
                        continue;
                    }

                    Release snapshotRelease = classSnapshot.getRelease();

                    // LA REGOLA IBRIDA:
                    // Lo snapshot è buggy se la sua release è compresa nell'intervallo [IV, FV).
                    // (Inclusa la release di iniezione, esclusa la release di fix).
                    boolean isInjectedBeforeOrDuringSnapshot = !injectedVersion.getReleaseDate().isAfter(snapshotRelease.getReleaseDate());
                    boolean isFixedAfterSnapshot = fixedVersion.getReleaseDate().isAfter(snapshotRelease.getReleaseDate());

                    if (isInjectedBeforeOrDuringSnapshot && isFixedAfterSnapshot) {
                        classSnapshot.setBuggy(true);
                    }
                }
            }
        }
    }

    /**
     * Adds each commit to the touching commit list of each class
     * @param classList list of classes to set the touching commit list
     * @param commitList list of commits that touches the classes in classList
     * @throws IOException if there is any failure taking the touched class names
     */
    private void setTouchingClassesCommits(List<AnalyzedClass> classList, List<Commit> commitList) throws IOException {
        List<AnalyzedClass> tempProjClasses;

        for(Commit commit: commitList){
            Release release = commit.getRelease();
            tempProjClasses = new ArrayList<>(classList);

            // Get the class list containing only the class of the current commit release
            tempProjClasses.removeIf(tempProjClass -> !tempProjClass.getRelease().equals(release));

            // Get the classes modified by the current commit
            List<String> modifiedClassesNames = getTouchedClassesNames(commit.getRevCommit());

            // For each class touched by the current commit, add the commit to its touching commit list
            for(String modifiedClass: modifiedClassesNames){
                for(AnalyzedClass projectClass: tempProjClasses){
                    if(projectClass.getClassName().equals(modifiedClass) && !projectClass.getTouchingClassCommitList().contains(commit)) {
                        projectClass.addTouchingClassCommit(commit);
                    }
                }
            }
        }
    }


    /**
     * Implementa l'algoritmo SZZ per trovare i commit che hanno introdotto bug.
     * @param fixingCommit Il commit che ha risolto il bug
     * @param filePath Il percorso del file che è stato modificato
     * @return Lista di commit che potrebbero aver introdotto il bug
     */
    public List<Commit> findBugIntroducingCommits(Commit fixingCommit, String filePath) throws IOException{
        logger.info("Applicazione algoritmo SZZ per " + filePath + " nel fixing commit " + fixingCommit.getRevCommit().getName());
        List<Commit> bugIntroducingCommits = new ArrayList<>();

        RevCommit revCommit = fixingCommit.getRevCommit();

        // Se il commit non ha genitori, non possiamo fare blame
        if (revCommit.getParentCount() == 0) {
            logger.info("Il commit " + revCommit.getName() + " non ha genitori, impossibile applicare SZZ.");
            return bugIntroducingCommits;
        }

        RevCommit parentCommit = revCommit.getParent(0);

        // Identifica le righe modificate nel fixing commit
        try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            df.setRepository(repository);
            df.setContext(0); // Solo le righe modificate, senza contesto

            List<DiffEntry> diffs = df.scan(parentCommit.getTree(), revCommit.getTree());

            for (DiffEntry diff : diffs) {
                if (diff.getNewPath().equals(filePath)) {
                    // Analizza solo il file che ci interessa
                    EditList edits = df.toFileHeader(diff).toEditList();

                    // Prepara il blame per il file nello stato pre-fix
                    BlameCommand blameCommand = git.blame();
                    blameCommand.setStartCommit(parentCommit.getId());
                    blameCommand.setFilePath(filePath);

                    try {
                        BlameResult blameResult = blameCommand.call();

                        if (blameResult == null) {
                            logger.warning("Impossibile eseguire blame su " + filePath + " per il commit " + parentCommit.getName());
                            continue;
                        }

                        Set<RevCommit> suspiciousCommits = new HashSet<>();

                        // Per ogni modifica nel diff
                        for (Edit edit : edits) {
                            int startLine = edit.getBeginA();
                            int endLine = edit.getEndA();

                            // Per ogni riga modificata/eliminata, trova chi l'ha introdotta
                            for (int i = startLine; i < endLine; i++) {
                                RevCommit blameCommit = blameResult.getSourceCommit(i);
                                if (blameCommit != null) {
                                    suspiciousCommits.add(blameCommit);
                                }
                            }
                        }

                        // Converti da RevCommit ai nostri oggetti Commit
                        for (RevCommit suspiciousCommit : suspiciousCommits) {
                            for (Commit commit : allCommits) {
                                if (commit.getRevCommit().getName().equals(suspiciousCommit.getName())) {
                                    bugIntroducingCommits.add(commit);
                                    break;
                                }
                            }
                        }
                    } catch (GitAPIException e) {
                        logger.warning("Errore durante l'esecuzione del blame: " + e.getMessage());
                    }
                }
            }
        }

        logger.info("Trovati " + bugIntroducingCommits.size() + " possibili commit che hanno introdotto bug in " + filePath);
        return bugIntroducingCommits;
    }
    public void findAllBugIntroducingCommits() throws IOException {
        logger.info("Applicazione algoritmo SZZ a tutti i fixing commits...");

        for (Commit fixingCommit : fixingCommits) {
            List<String> buggyFiles = buggyFilesPerCommit.get(fixingCommit);
            if (buggyFiles == null || buggyFiles.isEmpty()) {
                continue;
            }

            List<Commit> bugIntroducingForThisCommit = new ArrayList<>();

            for (String filePath : buggyFiles) {
                List<Commit> bugIntroducingForFile = findBugIntroducingCommits(fixingCommit, filePath);
                // Aggiungi solo commit non già presenti
                for (Commit commit : bugIntroducingForFile) {
                    if (!bugIntroducingForThisCommit.contains(commit)) {
                        bugIntroducingForThisCommit.add(commit);
                    }
                }
            }

            if (!bugIntroducingForThisCommit.isEmpty()) {
                bugIntroducingCommitsMap.put(fixingCommit, bugIntroducingForThisCommit);

                // Aggiorna lo stato di buggy dei commit trovati
                for (Commit bugIntroducingCommit : bugIntroducingForThisCommit) {
                    bugIntroducingCommit.setBuggy(true);
                }
            }
        }

        logger.info("Trovati bug-introducing commits per " + bugIntroducingCommitsMap.size() + " fixing commits");
    }
    /**
     * Estrae tutti gli snapshot delle classi relative a UNA SINGOLA release.
     * @param release La release da analizzare.
     * @return Una lista di AnalyzedClass per quella release.
     */
    public List<AnalyzedClass> getClassesForRelease(Release release) throws IOException {
        List<AnalyzedClass> classList = new ArrayList<>();

        if (release.getCommitList().isEmpty()) {
            return classList; // Release senza commit
        }

        // Prendiamo l'ultimo commit come rappresentativo della release
        Commit lastCommit = release.getCommitList().getLast();

        // Get a map of class name to class code
        Map<String, String> classesNameCodeMap = getClassesNameCodeInfos(lastCommit.getRevCommit());
        for (Map.Entry<String, String> classInfo : classesNameCodeMap.entrySet()) {
            classList.add(new AnalyzedClass(classInfo.getKey(), classInfo.getValue(), release));
        }

        // Associa i commit di QUESTA release alle classi trovate
        setTouchingClassesCommits(classList, release.getCommitList());

        return classList;
    }



}







