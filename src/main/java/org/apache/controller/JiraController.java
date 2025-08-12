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
import java.nio.charset.StandardCharsets;
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
        String urlString = JIRA_BASE_URL + "project/" + targetName ;
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
            if (releaseID == null || releaseName == null || releaseDate == null) {
                logger.warning("Incomplete version data: " + version);
                continue;
            }
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
            // --- INIZIO MODIFICA DI DEBUG ---
            // Query JQL Semplificata: "dammi TUTTI i ticket di tipo Bug, indipendentemente dallo stato"
            String jql = String.format("project = \"%s\" AND issuetype = \"Bug\"", targetName);

            // Se anche questa non restituisce nulla, prova la query più permissiva di tutte:
            // String jql = String.format("project = \"%s\"", this.targetName);

            logger.info(String.format("Eseguo query JQL: %s", jql)); // Logghiamo la query che stiamo usando
            // --- FINE MODIFICA DI DEBUG ---

            String url = JIRA_BASE_URL + "search?jql=" + java.net.URLEncoder.encode(jql, StandardCharsets.UTF_8)
                    + "&fields=key,versions,created,resolutiondate&startAt=" + startAt + "&maxResults=" + results;
            JSONObject json = JsonReader.readJsonFromUrl(url);
            JSONArray issues = json.getJSONArray("issues");
            total = json.getInt("total");
            //itero sui ticket ricordo che key è l'identificativo del ticket
            for (Object issueObj : issues) {
                JSONObject issue = (JSONObject) issueObj;
                try {
                    JSONObject fields = issue.getJSONObject("fields");

                    // --- INIZIO CORREZIONE ---
                    // Prima di tutto, controlliamo se il ticket è stato risolto.
                    // Usiamo optString per evitare eccezioni su campi null.
                    String resolutionDateString = fields.optString("resolutiondate");

                    // Procediamo SOLO se la data di risoluzione esiste e non è vuota.
                    if (resolutionDateString != null && !resolutionDateString.isEmpty()) {

                        String key = issue.getString("key");
                        String creationDateString = fields.getString("created");

                        // Ora che siamo sicuri che le stringhe esistano, possiamo fare il parsing.
                        LocalDate creationDate = LocalDate.parse(creationDateString.substring(0, 10));
                        LocalDate resolutionDate = LocalDate.parse(resolutionDateString.substring(0, 10));

                        JSONArray affectedVersions = fields.optJSONArray("versions");

                        //Prendo OV of the issues
                        Release openingVersion = Release.getReleaseAfterOrEqualToDate(creationDate, this.releases);
                        //Prendo FV of the issues
                        Release fixedVersion = Release.getReleaseAfterOrEqualToDate(resolutionDate, this.releases);
                        List<Release> affectedVersionList = Release.getAffectedVersions(affectedVersions, this.releases);
                        // Prima, controlliamo le condizioni di base che causano sempre lo scarto del ticket.
                        if (openingVersion == null || fixedVersion == null || openingVersion.getReleaseDate().isAfter(fixedVersion.getReleaseDate())) {
                            continue; // Scarta il ticket e passa al successivo
                        }

                        // Ora, gestiamo la logica complessa legata alle affected versions.
                        // Eseguiamo questo controllo SOLO SE la lista delle affected versions NON è vuota.
                        if (!affectedVersionList.isEmpty()) {
                            // Se la lista non è vuota, controlliamo la coerenza.
                            // Se una di queste condizioni è vera, il ticket non è coerente e lo scartiamo.
                            if (openingVersion.getReleaseDate().isBefore(affectedVersionList.getFirst().getReleaseDate()) ||
                                    !fixedVersion.getReleaseDate().isAfter(affectedVersionList.getLast().getReleaseDate())) {
                                continue; // Scarta il ticket
                            }
                        }
                        tickets.add(new Ticket(key, creationDate, resolutionDate, openingVersion, fixedVersion, affectedVersionList));
                    }
                } catch (Exception e) {
                    logger.warning("Impossibile processare un ticket. Causa: " + e.getMessage());
                }

            }
        }while (startAt < total) ;
            this.fixedTickets = new ArrayList<>(this.tickets);
            this.fixedTickets.sort(Comparator.comparing(Ticket::getResolutionDate));
            logger.info(String.format("Trovati e processati %d ticket 'Fixed' per %s", this.fixedTickets.size(), targetName));



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







