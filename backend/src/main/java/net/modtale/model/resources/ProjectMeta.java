package net.modtale.model.resources;

public class ProjectMeta {
    private String id;
    private String title;
    private long totalDownloads;

    public ProjectMeta(String id, String title, long totalDownloads) {
        this.id = id;
        this.title = title;
        this.totalDownloads = totalDownloads;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public long getTotalDownloads() { return totalDownloads; }
}