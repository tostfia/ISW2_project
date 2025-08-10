package org.apache.controller;

import org.apache.model.Release;
import org.apache.model.Ticket;
import org.json.JSONObject;

import java.util.*;

public class JiraController {
    private  List<Release> releases;
    private List<Ticket> tickets;
    private static String targetName;
    private List<Ticket> fixedTickets;
    private List<Ticket> issueTickets;
    private List<Release> affectedReleases;
    //Costruttore
    public JiraController(String targetName){

    }
    //PRIMA FASE
    public void injectRelease() {

    }
    //SECONDA FASE
    public void injectTickets() {
    }
    //TERZA FASE
    public List<Release> getRealeases() {
        return List.of();
    }
    //Prendo i ticket fixati
    public List<Ticket> getFixedTickets() {
        return null;
    }




    //Fine salvo in Map
    public Map<String,String> getMapReleases() {
        Map<String,String> mapReleases = new HashMap<>();
        this.releases.sort(Comparator.comparing(Release::getReleaseDate));
        final String name ="name";
        final String commits = "commits";
        for (Release release : this.releases) {
            Map<String,String> innerMap= new LinkedHashMap<>();
            innerMap.put(name, release.getReleaseName());
            innerMap.put("releaseDate", release.getReleaseDate().toString());
            innerMap.put(commits, String.valueOf(release.getCommitList().size()));
            mapReleases.put(String.valueOf(release.getId()),innerMap.toString());
        }
        return mapReleases;
    }



}
