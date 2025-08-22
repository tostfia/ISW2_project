package org.apache.model; // Assicurati che il package sia corretto

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
public class Ticket {


    private final String ticketKey;
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



    // --- COSTRUTTORE AGGIORNATO ---
    public Ticket(String ticketKey, LocalDate creationDate, LocalDate resolutionDate,
                  Release openingVersion, Release fixedVersion, List<Release> affectedVersions) { // <-- Nuovi parametri
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

    }






    public boolean isCorrect() {
        return !getAffectedVersions().isEmpty();
    }


}