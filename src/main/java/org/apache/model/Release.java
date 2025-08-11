package org.apache.model;



import lombok.Getter;
import lombok.Setter;
import org.json.JSONArray;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Getter
public  class Release {
    @Setter
    private int id;
    private final String releaseID;
    private final String releaseName;
    private final LocalDate releaseDate;
    private final String releaseDateString;
    private final List<Commit> commitList;


    public Release(String releaseID ,String releaseName, String releaseDateString) {
        if(releaseID == null || releaseName == null || releaseDateString == null){
            throw new IllegalArgumentException();
        }
        this.id = 0;
        this.releaseID = releaseID;
        this.releaseName = releaseName;
        this.releaseDateString = releaseDateString;
        this.releaseDate= LocalDate.parse(releaseDateString);
        commitList = new ArrayList<>();
    }

    public static Release getReleaseAfterOrEqualToDate(LocalDate creationDate, List<Release> releases) {
        releases.sort (Comparator.comparing(Release::getReleaseDate));
        for(Release release: releases){
            if(!release.getReleaseDate().isBefore(creationDate)){
                return release;
            }
        }
        return null;

    }

    public static List<Release> getAffectedVersions(JSONArray affectedVersions, List<Release> releases) {
        List<Release> affectedVersionList = new ArrayList<>();
        for (int i=0; i<affectedVersions.length(); i++) {
            String affectedVersionName = affectedVersions.getJSONObject(i).getString("name");
            Release release =getReleaseByName( releases, affectedVersionName);
            if(release != null){
                affectedVersionList.add(release);
            }
        }
        affectedVersionList.sort(Comparator.comparing(Release::getReleaseDate));
        return affectedVersionList;
    }
    public static Release getReleaseByName(List<Release> releases, String releaseName) {
        for (Release release : releases) {
            if (Objects.equals(releaseName, release.getReleaseName())) {
                return release;
            }
        }
        return null;
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

