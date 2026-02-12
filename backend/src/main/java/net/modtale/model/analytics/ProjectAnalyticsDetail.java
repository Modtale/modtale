package net.modtale.model.analytics;

import java.util.List;
import java.util.Map;

public class ProjectAnalyticsDetail {
    private String projectId;
    private String projectTitle;
    private long totalDownloads;
    private long totalViews;

    private long totalApiDownloads;
    private long totalFrontendDownloads;

    private List<AnalyticsDataPoint> views;
    private Map<String, List<AnalyticsDataPoint>> versionDownloads;

    public ProjectAnalyticsDetail() {}

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getProjectTitle() { return projectTitle; }
    public void setProjectTitle(String projectTitle) { this.projectTitle = projectTitle; }

    public long getTotalDownloads() { return totalDownloads; }
    public void setTotalDownloads(long totalDownloads) { this.totalDownloads = totalDownloads; }

    public long getTotalViews() { return totalViews; }
    public void setTotalViews(long totalViews) { this.totalViews = totalViews; }

    public long getTotalApiDownloads() { return totalApiDownloads; }
    public void setTotalApiDownloads(long totalApiDownloads) { this.totalApiDownloads = totalApiDownloads; }

    public long getTotalFrontendDownloads() { return totalFrontendDownloads; }
    public void setTotalFrontendDownloads(long totalFrontendDownloads) { this.totalFrontendDownloads = totalFrontendDownloads; }

    public List<AnalyticsDataPoint> getViews() { return views; }
    public void setViews(List<AnalyticsDataPoint> views) { this.views = views; }

    public Map<String, List<AnalyticsDataPoint>> getVersionDownloads() { return versionDownloads; }
    public void setVersionDownloads(Map<String, List<AnalyticsDataPoint>> versionDownloads) { this.versionDownloads = versionDownloads; }
}