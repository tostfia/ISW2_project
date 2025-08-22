package org.apache.controller.milestone1;

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
import java.util.stream.Collectors;

public class JiraController {
    private final List<Release> releases;
    private final List<Ticket> tickets;
    private final String targetName;
    private static final Logger logger = CollectLogger.getInstance().getLogger();


    @Getter
    private List<Ticket> fixedTickets;
    private static final String JIRA_BASE_URL = "https://issues.apache.org/jira/rest/api/2/";

    //Costruttore
    public JiraController(String targetName) {
        this.targetName = targetName.toUpperCase();
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
        do {
            String jql = String.format("project = \"%s\" AND issuetype = \"Bug\"", targetName);
            String url = JIRA_BASE_URL + "search?jql=" + java.net.URLEncoder.encode(jql, StandardCharsets.UTF_8)
                    + "&fields=key,versions,created,resolutiondate&startAt=" + startAt + "&maxResults=1000";
            JSONObject json = JsonReader.readJsonFromUrl(url);
            JSONArray issues = json.getJSONArray("issues");
            total = json.getInt("total");

            for (Object issueObj : issues) {
                try {
                    processIssue((JSONObject) issueObj);
                } catch (Exception e) {
                    logger.warning("Impossibile processare un ticket. Causa: " + e.getMessage());
                }
            }
            startAt += issues.length();
        } while (startAt < total);

        this.fixedTickets = new ArrayList<>(this.tickets);
        this.fixedTickets.sort(Comparator.comparing(Ticket::getResolutionDate));
        logger.info(String.format("Trovati e processati %d ticket 'Fixed' per %s", this.fixedTickets.size(), targetName));
    }

    private void processIssue(JSONObject issue) {
        JSONObject fields = issue.getJSONObject("fields");
        String resolutionDateString = fields.optString("resolutiondate");
        if (resolutionDateString == null || resolutionDateString.isEmpty()) return;

        String key = issue.getString("key");
        String creationDateString = fields.getString("created");
        LocalDate creationDate = LocalDate.parse(creationDateString.substring(0, 10));
        LocalDate resolutionDate = LocalDate.parse(resolutionDateString.substring(0, 10));
        JSONArray affectedVersions = fields.optJSONArray("versions");

        Release openingVersion = Release.getReleaseAfterOrEqualToDate(creationDate, this.releases);
        Release fixedVersion = Release.getReleaseAfterOrEqualToDate(resolutionDate, this.releases);
        List<Release> affectedVersionList = Release.getAffectedVersions(affectedVersions, this.releases);

        if (!isValidTicket(openingVersion, fixedVersion, affectedVersionList)) return;

        tickets.add(new Ticket(key, creationDate, resolutionDate, openingVersion, fixedVersion, affectedVersionList));
    }

    private boolean isValidTicket(Release openingVersion, Release fixedVersion, List<Release> affectedVersionList) {
        if (openingVersion == null || fixedVersion == null || openingVersion.getReleaseDate().isAfter(fixedVersion.getReleaseDate())) {
            return false;
        }
        return (!affectedVersionList.isEmpty() ||
                openingVersion.getReleaseDate().isBefore(affectedVersionList.getFirst().getReleaseDate()) ||
                !fixedVersion.getReleaseDate().isAfter(affectedVersionList.getLast().getReleaseDate()));

    }


    public List<Release> getRealeases() {
        return this.releases;
    }



    public void applyProportion(List<Double> coldStartData) {
        logger.info("Avvio dell'euristica Proportion per stimare le Injected Versions...");

        ProportionController propController = new ProportionController();

        // Ora chiamiamo il metodo principale del controller, passandogli i dati di cold start
        List<Ticket> repairedTickets = propController.applyProportion(this.fixedTickets, this.releases, coldStartData);

        // Ri-filtriamo per sicurezza e aggiorniamo la lista
        this.fixedTickets = repairedTickets.stream()
                .filter(t -> t.getInjectedVersion() != null &&
                        !t.getInjectedVersion().getReleaseDate().isAfter(t.getFixedVersion().getReleaseDate()))
                .sorted(Comparator.comparing(Ticket::getResolutionDate))
                .collect(Collectors.toList());

        logger.info(String.format("Processo Proportion completato. Numero finale di ticket validi: %d", this.fixedTickets.size()));
    }










}












