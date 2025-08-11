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
import java.time.LocalDate;
import java.util.*;
import java.util.logging.Logger;

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

    public void injectCommits() {
    }

    public void setTickets(Object fixedTickets){}

    public void processCommitsWithIssues() {
    }

    public void processClass() {
    }

    public void closeRepo() {
        this.localGitHub.getRepository().close();
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
