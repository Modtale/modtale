package net.modtale.model.analytics;

import java.util.List;

public class PlatformAnalyticsSummary {
    private long totalDownloads;
    private long totalViews;
    private long apiDownloads;
    private long frontendDownloads;
    private long totalNewProjects;

    private List<AnalyticsDataPoint> downloadsChart;
    private List<AnalyticsDataPoint> viewsChart;
    private List<AnalyticsDataPoint> newProjectsChart;

    public PlatformAnalyticsSummary() {}

    public long getTotalDownloads() { return totalDownloads; }
    public void setTotalDownloads(long totalDownloads) { this.totalDownloads = totalDownloads; }

    public long getTotalViews() { return totalViews; }
    public void setTotalViews(long totalViews) { this.totalViews = totalViews; }

    public long getApiDownloads() { return apiDownloads; }
    public void setApiDownloads(long apiDownloads) { this.apiDownloads = apiDownloads; }

    public long getFrontendDownloads() { return frontendDownloads; }
    public void setFrontendDownloads(long frontendDownloads) { this.frontendDownloads = frontendDownloads; }

    public long getTotalNewProjects() { return totalNewProjects; }
    public void setTotalNewProjects(long totalNewProjects) { this.totalNewProjects = totalNewProjects; }

    public List<AnalyticsDataPoint> getDownloadsChart() { return downloadsChart; }
    public void setDownloadsChart(List<AnalyticsDataPoint> downloadsChart) { this.downloadsChart = downloadsChart; }

    public List<AnalyticsDataPoint> getViewsChart() { return viewsChart; }
    public void setViewsChart(List<AnalyticsDataPoint> viewsChart) { this.viewsChart = viewsChart; }

    public List<AnalyticsDataPoint> getNewProjectsChart() { return newProjectsChart; }
    public void setNewProjectsChart(List<AnalyticsDataPoint> newProjectsChart) { this.newProjectsChart = newProjectsChart; }
}