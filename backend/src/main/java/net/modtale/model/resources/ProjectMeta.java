package net.modtale.model.resources;

public class ProjectMeta {
    private String id;
    private String title;
    private double currentRating;
    private long totalDownloads;

    public ProjectMeta(String id, String title, double currentRating, long totalDownloads) {
        this.id = id;
        this.title = title;
        this.currentRating = currentRating;
        this.totalDownloads = totalDownloads;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public double getCurrentRating() { return currentRating; }
    public long getTotalDownloads() { return totalDownloads; }
}