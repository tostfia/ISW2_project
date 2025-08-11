package org.apache.controller;

import lombok.Getter;
import org.apache.logging.CollectLogger;
import org.apache.model.Release;
import org.apache.model.Ticket;
import org.apache.utilities.JsonReader;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.*;
import java.util.logging.Logger;

public class JiraController {
    private final List<Release> releases;
    private final List<Ticket> tickets;
    private static String targetName;
    private static final Logger logger = CollectLogger.getInstance().getLogger();
    //Prendo i ticket fixati
    @Getter
    private List<Ticket> fixedTickets;
    private static final String JIRA_BASE_URL = "https://issues.apache.org/jira/rest/api/2/";

    //Costruttore
    public JiraController(String targetName) {
        JiraController.targetName = targetName.toUpperCase();
        this.releases = new ArrayList<>();
        this.tickets = new ArrayList<>();
        this.fixedTickets = new ArrayList<>();
    }


    //Fase 1: Scarico le informazioni sulle versioni da Jira e le carico nello stato interno
    public void injectRelease() throws IOException, URISyntaxException {
        String urlString = JIRA_BASE_URL + "project/" + targetName + "/versions";
        JSONObject json = JsonReader.readJsonFromUrl(urlString);
        JSONArray versions = json.getJSONArray("versions");

        for (int i = 0; i < versions.length(); i++) {
            String releaseID = null;
            String releaseName = null;
            String releaseDate = null;
            JSONObject version = versions.getJSONObject(i);
            if (version.has("id")) releaseID = version.getString("id");
            if (version.has("name")) releaseName = version.getString("name");
            if (version.has("releaseDate")) releaseDate = version.getString("releaseDate");
            try {

                Release newRelease = new Release(releaseID, releaseName, releaseDate);
                this.releases.add(newRelease);
            } catch (NullPointerException e) {
                logger.severe(e.getMessage());
            }
        }
        releases.sort(Comparator.comparing(Release::getReleaseDate));

    }

    //Fase 2: Scarico i ticket (bug "Fixed") da Jira e li carico nello stato interno
    public void injectTickets() throws IOException, URISyntaxException {
        int total;
        int startAt = 0;
        int results;
        do {
            results = startAt + 1000;
            String url = JIRA_BASE_URL + "search?jql=project=%22" + targetName + "%22AND%22issueType%22=%22Bug%22AND" +
                    "(%22status%22=%22Closed%22OR%22status%22=%22Resolved%22)" +
                    "AND%22resolution%22=%22Fixed%22&fields=key,versions,created,resolutiondate&startAt="
                    + startAt + "&maxResults=" + results;
            JSONObject json = JsonReader.readJsonFromUrl(url);
            JSONArray issues = json.getJSONArray("issues");
            total = json.getInt("total");
            //itero sui ticket ricordo che key è l'identificativo del ticket
            for (; startAt < total && startAt < results; startAt++) {
                String key = issues.getJSONObject(startAt % 1000).getString("key");
                //Prendo la data di risoluzione
                JSONObject fields = issues.getJSONObject(startAt % 1000).getJSONObject("fields");
                String creationDateString = fields.getString("created");
                String resolutionDateString = fields.getString("resolutiondate");
                LocalDate creationDate = LocalDate.parse(creationDateString.substring(0, 10));
                LocalDate resolutionDate = LocalDate.parse(resolutionDateString.substring(0, 10));

                //Prendo le versioni affette dalle issues
                JSONArray affectedVersions = fields.optJSONArray("versions");

                //Prendo OV of the issues
                Release openingVersion = Release.getReleaseAfterOrEqualToDate(creationDate, this.releases);
                //Prendo FV of the issues
                Release fixedVersion = Release.getReleaseAfterOrEqualToDate(resolutionDate, this.releases);
                List<Release> affectedVersionList = Release.getAffectedVersions(affectedVersions, this.releases);
                if (openingVersion == null || fixedVersion == null || openingVersion.getReleaseDate().isAfter(fixedVersion.getReleaseDate()) || (affectedVersionList.isEmpty() && (openingVersion.getReleaseDate().isBefore(affectedVersionList.getFirst().getReleaseDate()) || !fixedVersion.getReleaseDate().isAfter(affectedVersionList.getLast().getReleaseDate())))
                ) continue;
                tickets.add(new Ticket(key, creationDate, resolutionDate, openingVersion, fixedVersion, affectedVersionList));

            }

        } while (startAt < total);
        //Filtro i ticket che hanno una versione fissa
        tickets.sort(Comparator.comparing(Ticket::getResolutionDate));


    }


    public List<Release> getRealeases() {
        return this.releases;
    }


    //Fine salvo in Map
    public Map<String, String> getMapReleases() {
        Map<String, String> mapReleases = new HashMap<>();
        for (Release release : this.releases) {
            Map<String, String> innerMap = new LinkedHashMap<>();
            innerMap.put("name", release.getReleaseName());
            innerMap.put("releaseDate", release.getReleaseDate().toString());
            innerMap.put("commits", String.valueOf(release.getCommitList().size()));
            mapReleases.put(String.valueOf(release.getId()), innerMap.toString());
        }
        return mapReleases;
    }
}







