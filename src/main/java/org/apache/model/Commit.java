package org.apache.model;


import lombok.Getter;
import lombok.Setter;
import org.eclipse.jgit.revwalk.RevCommit;

import java.util.Date;
import java.util.Objects;

/**
 * Modello che rappresenta un singolo commit di Git.
 * Refactor: Questa classe è ora un wrapper più semplice e robusto attorno
 * all'oggetto RevCommit di JGit. Ha un unico costruttore e deriva
 * tutte le sue informazioni dal RevCommit, garantendo coerenza.
 */
@Getter
public class Commit {

    private final RevCommit revCommit;
    private final Release release;

    @Setter
    private Ticket ticket;

    @Setter
    private boolean buggy; // Questa è l'unica proprietà che può cambiare


    /**
     * Costruttore unico e principale.
     * Crea un oggetto Commit a partire da un RevCommit di JGit e la sua release associata.
     */
    public Commit(RevCommit revCommit, Release release) {
        // Controlli di validità per prevenire errori
        Objects.requireNonNull(revCommit, "RevCommit non può essere null.");
        Objects.requireNonNull(release, "La Release non può essere null.");

        this.revCommit = revCommit;
        this.release = release;
        this.buggy= false; // Inizializzato a false di default
        this.ticket = null;
    }



    public String getAuthor() {
        return this.revCommit.getAuthorIdent().getName();
    }




    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Commit commit = (Commit) o;
        // Confrontiamo direttamente gli oggetti RevCommit, che JGit gestisce in modo efficiente.
        return revCommit.equals(commit.revCommit);
    }

    @Override
    public int hashCode() {
        // Usiamo l'hashCode del RevCommit, che è basato sul suo hash SHA-1.
        return revCommit.hashCode();
    }


}