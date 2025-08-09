package org.apache.model;



import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
public  class Release {
    @Setter
    private int id;
    private final String releaseName;
    private final LocalDate releaseDate;
    private final List<Commit> commitList;

    public Release(String releaseName, LocalDate releaseDate) {
        this.releaseName = releaseName;
        this.releaseDate = releaseDate;
        commitList = new ArrayList<>();
    }

    public Release(int id, String releaseName, LocalDate releaseDate) {
        this.id = id;
        this.releaseName = releaseName;
        this.releaseDate = releaseDate;
        commitList = new ArrayList<>();
    }

    public void addCommit(Commit newCommit) {
        if(!commitList.contains(newCommit)){
            commitList.add(newCommit);
        }
    }


    @Override
    public String toString() {
        return "Release{" +
                "getId=" + id +
                ", releaseName='" + releaseName + '\'' +
                ", releaseDate=" + releaseDate +
                '}';
    }
}

