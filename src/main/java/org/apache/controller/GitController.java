package org.apache.controller;

import lombok.Getter;
import lombok.Setter;
import org.apache.logging.CollectLogger;
import org.apache.model.Commit;
import org.apache.model.JavaClass;
import org.apache.model.Release;
import org.apache.model.Ticket;
import org.apache.utilities.enums.ReportType;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;


import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GitController {
    private static final String REPO_PATH = "C:/Users/sofia/repo";
    private static final String LOCAL_DATE_FORMAT = "yyyy-MM-dd";
    private static final String GIT = File.separator + ".git";
    private final String repoPath;
    private final String lastBranch;
    @Getter
    @Setter
    private List<Ticket> tickets;
    @Getter
    private List<Release> releases;
    protected final Git localGitHub;
    private final Repository localRepo;
    private List<Commit> commits;
    private ArrayList<Commit> commitsWithIssues;
    private ArrayList<JavaClass> javaClasses;
    private final Map<RevCommit, List<String>> modifiedClasses;
    private  Map<Release, List<JavaClass>> releaseJavaClasses=null;
    private final String targetName;
    private final Logger logger= CollectLogger.getInstance().getLogger();




    public GitController(String targetName, String gitUrl, List<Release> releases) throws IOException, GitAPIException {
        this.targetName = targetName;
        this.repoPath= REPO_PATH+ targetName.toLowerCase(Locale.getDefault());
        File directory = new File(repoPath);
        if (!directory.exists()) {
            localGitHub = Git.cloneRepository().setURI(gitUrl).setDirectory(directory).call();
            localRepo = localGitHub.getRepository();
        } else {
            localRepo = new FileRepository(repoPath + GIT);
            localGitHub = new Git(localRepo);
        }
        this.releases = releases;
        this.tickets = new ArrayList<>();
        this.modifiedClasses = new HashMap<>();
        this.lastBranch= localRepo.getBranch();
    }

    public void injectCommits() throws IOException, GitAPIException {
        logger.info("Inizio analisi dei commit per " + targetName);
        Iterable<RevCommit> log = localGitHub.log().all().call();
        for (RevCommit revCommit : log) {
            LocalDateTime commitDate = LocalDateTime.ofInstant(Instant.ofEpochSecond(revCommit.getCommitTime()), ZoneId.systemDefault());
            //Associo il commit alla release
            Release release = findReleaseForCommit(commitDate);
            if (release != null) {
                Commit commit = new Commit(revCommit, release);
                this.commits.add(commit);
            }
        }
        //Ordino i commit in base alla data di creazione
        this.commits.sort(Comparator.comparing(c -> c.getRevCommit().getCommitTime()));
        logger.info("Trovati e processati" + this.commits.size() + "commit.");
    }

    //Helper per trovare la release di un commit basandosi sulla data
    private Release findReleaseForCommit(LocalDateTime commitDate) {
        for (Release release : this.releases) {
            if (!commitDate.toLocalDate().isAfter(release.getReleaseDate())) {
                return release;
            }
        }
        //Se il commit è più recente dell'ultima release appartiene a quest'ultima
        return this.releases.isEmpty() ? null : this.releases.getLast();
    }

    //Scansiona i commit, cerca gli ID dei ticket nei messaggi e popola la lista dei commit con issues
    public void processCommitsWithIssues() {
        Map<String,Ticket> ticketMap = new HashMap<>();
        for(Ticket ticket: this.tickets) {
            ticketMap.put(ticket.getTicketKey(), ticket);
        }
        //Regex per trovare gli ID dei ticket
        Pattern pattern= Pattern.compile(this.targetName.toUpperCase()+"-\\d+");

        for (Commit commit: this.commits) {
            Matcher matcher= pattern.matcher(commit.getRevCommit().getFullMessage());
            if(matcher.find()){
                String ticketKey = matcher.group(0);
                if(ticketMap.containsKey(ticketKey)) {
                    //Trovato un fixing-commit
                    Ticket ticket = ticketMap.get(ticketKey);
                    commit.setTicket(ticket);//Associa il ticket al commit
                    this.commitsWithIssues.add(commit);
                }
            }
        }
        logger.info("Trovati"+this.commitsWithIssues.size()+"commit che risolvono ticket");
    }

    public void processClass() {
    }

    public void closeRepo() {
        if(this.localGitHub!=null){
            this.localGitHub.close();
        }
        if(this.localRepo!=null){
            this.localRepo.close();
        }
        logger.info("Repository chiusa correttamente per " + targetName);
    }







    //Mapper
    public Map<String, String> getMapTickets() {
        Map<String, String> mapTickets = new HashMap<>();
        this.tickets.sort(Comparator.comparing(Ticket::getCreationDate));
        for (Ticket ticket : this.tickets) {
            List<String> ids = new ArrayList<>();
            for (Release release : ticket.getAffectedVersions()) {
                ids.add(release.getReleaseName());
            }
            Map<String, String> inner = new LinkedHashMap<>();
            inner.put("injectedVersion", ticket.getInjectedVersion().toString());
            inner.put("openingVersion", ticket.getOpeningVersion().toString());
            inner.put("fixedVersion", ticket.getFixedVersion().toString());
            inner.put("affectedVersions", ids.toString());
            inner.put("commits", String.valueOf(ticket.getCommitList().size()));
            inner.put("creationDate", ticket.getCreationDate().toString());
            inner.put("resolutionDate", ticket.getResolutionDate().toString());
            mapTickets.put(ticket.getTicketKey(), inner.toString());
        }

        return mapTickets;
    }

    public Map<String, String> getMapCommits() {
        Map<String, String> mapCommits = new HashMap<>();
        for (Commit commit : this.commits) {
            Map<String, String> inner = new LinkedHashMap<>();
            RevCommit revCommit = commit.getRevCommit();
            Ticket ticket = commit.getTicket();
            Release release = commit.getRelease();
            if (ticket != null) {
                inner.put("ticketKey", commit.getTicket().getTicketKey());
            }
            inner.put("release", release.getReleaseName());
            inner.put("creationDate",
                    String.valueOf(LocalDate.parse((new SimpleDateFormat(GitController.LOCAL_DATE_FORMAT)
                            .format(Date.from(revCommit.getCommitterIdent().getWhenAsInstant()))
                    ))));
            mapCommits.put(revCommit.getName(), inner.toString());
        }
        return mapCommits;
    }

    public Map<String, String> getMapSummary() {
        Map<String, String> summaryMap = new HashMap<>();
        summaryMap.put(ReportType.RELEASE.toString(), String.valueOf(this.releases.size()));
        summaryMap.put(ReportType.TICKETS.toString(), String.valueOf(this.tickets.size()));
        summaryMap.put(ReportType.COMMITS.toString(), String.valueOf(this.commits.size()));
        summaryMap.put(ReportType.SUMMARY.toString(), String.valueOf(this.commitsWithIssues.size()));
        return summaryMap;
    }
    // Method implementation
}
