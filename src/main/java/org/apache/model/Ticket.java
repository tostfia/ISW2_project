package org.apache.model; // Assicurati che il package sia corretto

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
public class Ticket {


    private String ticketKey;
    private final LocalDate creationDate;
    private final LocalDate resolutionDate;
    @Setter
    private Release injectedVersion;
    @Setter
    private  Release openingVersion;
    @Setter
    private  Release fixedVersion;
    @Setter
    private  List<Release> affectedVersions;
    @Setter
    private  List<Commit> commitList;

    // --- NUOVI CAMPI AGGIUNTI ---
    private final String type;
    private final String status;
    private final String resolution;
    // ----------------------------

    // --- COSTRUTTORE AGGIORNATO ---
    public Ticket(String ticketKey, LocalDate creationDate, LocalDate resolutionDate,
                  Release openingVersion, Release fixedVersion, List<Release> affectedVersions,
                  String type, String status, String resolution) { // <-- Nuovi parametri
        this.ticketKey = ticketKey;
        this.creationDate = creationDate;
        this.resolutionDate = resolutionDate;

        if (affectedVersions.isEmpty()) {
            this.injectedVersion = null;
        } else {
            this.injectedVersion = affectedVersions.getFirst();
        }

        this.openingVersion = openingVersion;
        this.fixedVersion = fixedVersion;
        this.affectedVersions = affectedVersions;
        this.commitList = new ArrayList<>();

        // Assegna i nuovi valori ai campi
        this.type = type;
        this.status = status;
        this.resolution = resolution;
    }

    public Ticket(String key, LocalDate creationDate, LocalDate resolutionDate, Release openingVersion, Release fixedVersion, List<Release> affectedVersionList) {
        this(key, creationDate, resolutionDate, openingVersion, fixedVersion, affectedVersionList, "Unknown", "Unknown", "Unknown");
    }


    public void addCommit(Commit commit) {
        if (commit != null && !commitList.contains(commit)) {
            commitList.add(commit);
        }
    }

    public boolean isCorrect() {
        return !getAffectedVersions().isEmpty();
    }

    public Ticket cloneTicketAtRelease(Release release) {
        List<Release> newAffectedVersions = affectedVersions.stream().filter(av -> av.getId() <= release.getId()).toList();
        Release newFixedVersion = fixedVersion.getId() <= release.getId() ? fixedVersion : null;
        if (newFixedVersion == null) return null;

        // Passa i campi type, status, resolution al nuovo ticket clonato
        return new Ticket(ticketKey, creationDate, resolutionDate, release, newFixedVersion, newAffectedVersions, this.type, this.status, this.resolution);
    }
}