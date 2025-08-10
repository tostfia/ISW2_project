package org.apache.controller;

import org.apache.model.Release;
import org.apache.model.Ticket;
import org.json.JSONObject;

import java.util.List;

public class JiraController {
    public JiraController(String targetName){}
    public void injectRelease() {

    }

    public List<Release> getRealeases() {
        return List.of();
    }

    public void injectTickets() {
    }

    public List<Ticket> getFixedTickets() {
        return null;
    }

    public JSONObject getMapReleases() {
        return null;
    }

    public JSONObject getMapTickets() {
        return  null;
    }

}
