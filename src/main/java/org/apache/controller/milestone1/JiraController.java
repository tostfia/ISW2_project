package org.apache.controller.milestone1;

import lombok.Getter;
import org.apache.logging.Printer;
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
import java.util.stream.Collectors;

public class JiraController {
    private final List<Release> releases;
    private final List<Ticket> tickets;
    private final String targetName;



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
                Printer.printYellow("Incomplete version data: " + version);
                continue;
            }
            try {

                Release newRelease = new Release(releaseID, releaseName, releaseDate);
                this.releases.add(newRelease);
            } catch (NullPointerException e) {
                Printer.errorPrint(e.getMessage());
            }
        }
        releases.sort(Comparator.comparing(Release::getReleaseDate));

    }

    //Fase 2: Scarico i ticket (bug "Fixed") da Jira e li carico nello stato interno
    public void injectTickets() throws IOException, URISyntaxException {
        int startAt = 0;
        int total;

        do {
            JSONObject json = fetchTicketsBatch(startAt);
            JSONArray issues = json.getJSONArray("issues");
            total = json.getInt("total");

            for (Object issueObj : issues) {
                JSONObject issue = (JSONObject) issueObj;
                try {
                    Ticket ticket = processIssue(issue);
                    if (ticket != null) tickets.add(ticket);
                } catch (Exception e) {
                    Printer.printYellow("Impossibile processare un ticket. Causa: " + e.getMessage());
                }
            }

            startAt += issues.length();
        } while (startAt < total);

        this.fixedTickets = new ArrayList<>(this.tickets);
        this.fixedTickets.sort(Comparator.comparing(Ticket::getResolutionDate));
        Printer.printGreen(String.format("Trovati e processati %d ticket 'Fixed' per %s",
                this.fixedTickets.size(), targetName));
    }

    private JSONObject fetchTicketsBatch(int startAt) throws IOException, URISyntaxException {
        String jql = String.format("project = \"%s\" AND issuetype = \"Bug\"", targetName);
        String url = JIRA_BASE_URL + "search?jql=" + java.net.URLEncoder.encode(jql, StandardCharsets.UTF_8)
                + "&fields=key,versions,created,resolutiondate&startAt=" + startAt + "&maxResults=1000";
        return JsonReader.readJsonFromUrl(url);
    }

    private Ticket processIssue(JSONObject issue) {
        JSONObject fields = issue.getJSONObject("fields");

        String resolutionDateString = fields.optString("resolutiondate");
        if (resolutionDateString == null || resolutionDateString.isEmpty()) return null;

        String key = issue.getString("key");
        LocalDate creationDate = LocalDate.parse(fields.getString("created").substring(0, 10));
        LocalDate resolutionDate = LocalDate.parse(resolutionDateString.substring(0, 10));

        JSONArray affectedVersions = fields.optJSONArray("versions");

        Release openingVersion = Release.getReleaseAfterOrEqualToDate(creationDate, this.releases);
        Release fixedVersion = Release.getReleaseAfterOrEqualToDate(resolutionDate, this.releases);
        List<Release> affectedVersionList = Release.getAffectedVersions(affectedVersions, this.releases);

        if (!isValidTicket(openingVersion, fixedVersion, affectedVersionList)) return null;

        return new Ticket(key, creationDate, resolutionDate, openingVersion, fixedVersion, affectedVersionList);
    }

    private boolean isValidTicket(Release openingVersion, Release fixedVersion, List<Release> affectedVersionList) {
        if (openingVersion == null || fixedVersion == null) return false;
        if (openingVersion.getReleaseDate().isAfter(fixedVersion.getReleaseDate())) return false;

        if (!affectedVersionList.isEmpty()) {
            Release firstAV = affectedVersionList.get(0);
            Release lastAV = affectedVersionList.get(affectedVersionList.size() - 1);
            if (openingVersion.getReleaseDate().isBefore(firstAV.getReleaseDate())
                    || !fixedVersion.getReleaseDate().isAfter(lastAV.getReleaseDate())) {
                return false;
            }
        }

        return true;
    }



    public List<Release> getRealeases() {
        return this.releases;
    }



    public void applyProportion(List<Double> coldStartData) {
        Printer.printBlue("Avvio dell'euristica Proportion per stimare le Injected Versions...\n");

        ProportionController propController = new ProportionController();

        // Ora chiamiamo il metodo principale del controller, passandogli i dati di cold start
        List<Ticket> repairedTickets = propController.applyProportion(this.fixedTickets, this.releases, coldStartData);

        // Ri-filtriamo per sicurezza e aggiorniamo la lista
        this.fixedTickets = repairedTickets.stream()
                .filter(t -> t.getInjectedVersion() != null &&
                        !t.getInjectedVersion().getReleaseDate().isAfter(t.getFixedVersion().getReleaseDate()))
                .sorted(Comparator.comparing(Ticket::getResolutionDate))
                .collect(Collectors.toList());

        Printer.printlnBlue("Processo Proportion completato. Numero finale di ticket validi: "+ this.fixedTickets.size()+"\n");
    }










}












