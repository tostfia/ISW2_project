package org.apache.model; // Assicurati che il package sia corretto



import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
public class Ticket {


    public final String ticketKey;
    public final LocalDate creationDate;
    public final LocalDate resolutionDate;
    @Setter
    @Getter
    public Release injectedVersion;
    @Setter
    @Getter
    public   Release openingVersion;
    @Setter
    @Getter
    public Release fixedVersion;
    @Setter
    public  List<Release> affectedVersions;
    @Setter
    public  List<Commit> commitList;



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