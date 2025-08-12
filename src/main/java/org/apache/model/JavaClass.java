package org.apache.model;



import lombok.Getter;

import java.util.*;

@Getter
public class JavaClass {
    private final String name;
    private final String content;
    private final Release release;
    private final DataMetrics metrics;
    private final List<Commit> commits;
    private final List<Integer> addLOC;
    private final List<Integer> delLOC;

    public JavaClass(String name, String content, Release release) {
        this.name = name;
        this.content = content;
        this.release = release;
        this.metrics = new DataMetrics();
        this.commits = new ArrayList<>();
        this.addLOC = new ArrayList<>();
        this.delLOC = new ArrayList<>();
    }


    public void addTouchedClass(Commit commit) {
        this.commits.add(commit);
    }

    public void addAddLOC(int loc) {
        this.addLOC.add(loc);
    }

    public void addDelLOC(int loc) {
        this.delLOC.add(loc);
    }
}
