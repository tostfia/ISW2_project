package org.apache.model;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
public class Ticket {

    @Setter
    private String ticketKey;
    private final LocalDate creationDate;
    private final LocalDate resolutionDate;
    private  Release injectedVersion;
    private final Release openingVersion;
    private final Release fixedVersion;
    private List<Release> affectedVersions;
    private final List<Commit> commitList;

    public Ticket(String ticketKey,LocalDate creationDate,LocalDate resolutionDate,Release openingVersion,Release fixedVersion, List<Release> affectedVersions) {
        this.ticketKey = ticketKey;
        this.creationDate = creationDate;
        this.resolutionDate = resolutionDate;
        if(affectedVersions.isEmpty()){
            injectedVersion=null;
        }else{
            injectedVersion=affectedVersions.getFirst();
        }
        this.openingVersion=openingVersion;
        this.fixedVersion=fixedVersion;
        this.affectedVersions = affectedVersions;
        commitList=new ArrayList<>();
    }

    public void addCommit(Commit commit) {
        if(commit != null && !commitList.contains(commit)) {
            commitList.add(commit);
        }
    }
    public boolean isCorrect(){
        return !getAffectedVersions().isEmpty();
    }
    public Ticket cloneTicketAtRelease(Release release){
        List<Release> newAffectedVersions = affectedVersions.stream().filter(av->av.getId()<= release.getId()).toList();
        Release newFixedVersion = fixedVersion.getId() <= release.getId() ? fixedVersion : null;
        if (newFixedVersion ==null) return null;
        return new Ticket(ticketKey,creationDate,resolutionDate,release,newFixedVersion,newAffectedVersions);
    }




}
