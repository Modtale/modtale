package net.modtale.model.analytics;

import java.util.List;
import java.util.Map;

public class ProjectAnalyticsDetail {
    private String projectId;
    private String projectTitle;
    private long totalDownloads;
    private long totalViews;

    private Map<String, List<AnalyticsDataPoint>> versionDownloads;
    private List<AnalyticsDataPoint> views;
    private List<AnalyticsDataPoint> ratingHistory;

    public ProjectAnalyticsDetail() {}

    public ProjectAnalyticsDetail(String projectId, String projectTitle,
                                  long totalDownloads, long totalViews,
                                  Map<String, List<AnalyticsDataPoint>> versionDownloads,
                                  List<AnalyticsDataPoint> views,
                                  List<AnalyticsDataPoint> ratingHistory) {
        this.projectId = projectId;
        this.projectTitle = projectTitle;
        this.totalDownloads = totalDownloads;
        this.totalViews = totalViews;
        this.versionDownloads = versionDownloads;
        this.views = views;
        this.ratingHistory = ratingHistory;
    }

    public String getProjectId() { return projectId; }
    public String getProjectTitle() { return projectTitle; }
    public long getTotalDownloads() { return totalDownloads; }
    public long getTotalViews() { return totalViews; }
    public Map<String, List<AnalyticsDataPoint>> getVersionDownloads() { return versionDownloads; }
    public List<AnalyticsDataPoint> getViews() { return views; }
    public List<AnalyticsDataPoint> getRatingHistory() { return ratingHistory; }

    public void setProjectId(String projectId) { this.projectId = projectId; }
    public void setProjectTitle(String projectTitle) { this.projectTitle = projectTitle; }
    public void setTotalDownloads(long totalDownloads) { this.totalDownloads = totalDownloads; }
    public void setTotalViews(long totalViews) { this.totalViews = totalViews; }
    public void setVersionDownloads(Map<String, List<AnalyticsDataPoint>> versionDownloads) { this.versionDownloads = versionDownloads; }
    public void setViews(List<AnalyticsDataPoint> views) { this.views = views; }
    public void setRatingHistory(List<AnalyticsDataPoint> ratingHistory) { this.ratingHistory = ratingHistory; }
}