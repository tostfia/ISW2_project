package org.apache.model;

import lombok.Getter;
import lombok.Setter;
import org.eclipse.jgit.revwalk.RevCommit;

import java.util.Objects;

@Getter
public class Commit {
    private final RevCommit revCommit;
    @Setter
    private Ticket ticket;
    private final Release release;
    @Setter
    private boolean isBuggy;

    public Commit(RevCommit revCommit, Release release) {
        this.revCommit = revCommit;
        this.release = release;
        ticket = null;
        this.isBuggy = false;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Commit commit = (Commit) o;
        return Objects.equals(revCommit, commit.revCommit);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(revCommit);
    }





}

