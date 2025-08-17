package org.apache.controller;


import org.apache.model.Release;
import org.apache.model.Ticket;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * [CLASSE ADATTATA PER IL TUO PROGETTO]
 * Applica l'euristica "Proportion" per stimare la Injected Version (IV)
 * dei ticket che ne sono sprovvisti, usando un approccio incrementale.
 */
public class ProportionController {


    private static final double COLD_START_P_VALUE = 0.5; // Valore di P di default se non ci sono abbastanza dati

    /**
     * Metodo principale. Prende una lista di ticket, la ordina e applica
     * l'algoritmo di proporzione incrementale per stimare le IV mancanti.
     *
     * @param allTickets La lista completa di ticket validati da Jira.
     * @param allReleases La lista di tutte le release del progetto.
     * @return La stessa lista di ticket, ma con le IV mancanti "riparate".
     */
    public List<Ticket> applyProportion(List<Ticket> allTickets, List<Release> allReleases) {

        // 1. Ordina tutti i ticket per data di risoluzione. Questo è fondamentale per l'approccio incrementale.
        allTickets.sort(Comparator.comparing(Ticket::getResolutionDate));

        double p = COLD_START_P_VALUE; // Inizia con un valore di P di default
        int updates = 0; // Contatore di quanti ticket con IV abbiamo usato per calcolare P

        // 2. Itera su ogni ticket in ordine cronologico
        for (Ticket ticket : allTickets) {

            // 3. Se il ticket ha una IV valida, usiamolo per migliorare la nostra stima di P
            if (ticket.getInjectedVersion() != null) {
                double newP = calculatePForTicket(ticket);
                if (newP >= 0) { // Ignora i valori non validi (es. se FV e OV sono uguali)
                    updates++;
                    // Formula per l'aggiornamento incrementale della media
                    p = p + (1.0 / updates) * (newP - p);
                }
            }

            // 4. Se il ticket NON ha una IV, usiamo il valore attuale di P per stimarla
            if (ticket.getInjectedVersion() == null) {
                estimateIvForTicket(ticket, p, allReleases);
            }
        }

        return allTickets; // Ritorna la lista modificata
    }

    /**
     * Calcola il valore di P per un singolo ticket che ha una IV nota.
     * Formula: P = (FV - IV) / (FV - OV)
     */
    private double calculatePForTicket(Ticket ticket) {
        // Usiamo i giorni per un calcolo preciso
        long fvDays = ticket.getFixedVersion().getReleaseDate().toEpochDay();
        long ovDays = ticket.getOpeningVersion().getReleaseDate().toEpochDay();
        long ivDays = ticket.getInjectedVersion().getReleaseDate().toEpochDay();

        long denominator = fvDays - ovDays;
        if (denominator <= 0) {
            return -1.0; // Valore sentinella per indicare che non è calcolabile
        }

        return (double) (fvDays - ivDays) / denominator;
    }

    /**
     * Stima e imposta la Injected Version per un ticket, usando il valore di P calcolato.
     * Formula: IV = FV - (FV - OV) * P
     */
    private void estimateIvForTicket(Ticket ticket, double p, List<Release> allReleases) {
        long fvDays = ticket.getFixedVersion().getReleaseDate().toEpochDay();
        long ovDays = ticket.getOpeningVersion().getReleaseDate().toEpochDay();

        long timeSpanInDays = fvDays - ovDays;

        // Calcola di quanti giorni tornare indietro dalla data della Fixed Version
        long daysToSubtract = (long) (timeSpanInDays * p);

        LocalDate estimatedIvDate = ticket.getFixedVersion().getReleaseDate().minusDays(daysToSubtract);

        // Trova la release più appropriata per questa data stimata
        Release estimatedIvRelease = findReleaseForDate(estimatedIvDate, allReleases);

        if (estimatedIvRelease != null) {
            ticket.setInjectedVersion(estimatedIvRelease);
            // Opzionale: potresti voler ricalcolare anche la lista delle Affected Versions
            adjustAffectedVersions(ticket, allReleases);
        }
    }

    /**
     * Trova la release che contiene una data specifica o quella immediatamente successiva.
     */
    private Release findReleaseForDate(LocalDate date, List<Release> allReleases) {
        // Assicura che le release siano ordinate per data
        allReleases.sort(Comparator.comparing(Release::getReleaseDate));
        for (Release release : allReleases) {
            if (!date.isAfter(release.getReleaseDate())) {
                return release;
            }
        }
        // Se la data è successiva a tutte le release, ritorna l'ultima
        return allReleases.isEmpty() ? null : allReleases.getLast();
    }

    /**
     *
     * Ricostruisce la lista delle Affected Versions dopo aver stimato una nuova IV.
     */
    private void adjustAffectedVersions(Ticket ticket, List<Release> allReleases) {
        if (ticket.getInjectedVersion() == null) return;

        List<Release> newAffectedVersions = new ArrayList<>();
        for (Release release : allReleases) {
            // Una release è "affected" se si trova tra la IV (inclusa) e la FV (esclusa)
            if (!release.getReleaseDate().isBefore(ticket.getInjectedVersion().getReleaseDate()) &&
                    release.getReleaseDate().isBefore(ticket.getFixedVersion().getReleaseDate())) {
                newAffectedVersions.add(release);
            }
        }
        ticket.setAffectedVersions(newAffectedVersions);
    }
}
