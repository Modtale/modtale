package net.modtale.model.project;

public class ProjectMeta {
    private String id;
    private String title;
    private long totalDownloads;
    private String updatedAt;

    public ProjectMeta(String id, String title, long totalDownloads, String updatedAt) {
        this.id = id;
        this.title = title;
        this.totalDownloads = totalDownloads;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public long getTotalDownloads() { return totalDownloads; }
    public String getUpdatedAt() { return updatedAt; }
}
