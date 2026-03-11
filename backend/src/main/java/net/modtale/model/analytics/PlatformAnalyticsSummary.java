package net.modtale.model.analytics;

import java.util.List;

public class PlatformAnalyticsSummary {
    private long totalDownloads;
    private long previousTotalDownloads;
    private long totalViews;
    private long previousTotalViews;
    private long apiDownloads;
    private long previousApiDownloads;
    private long frontendDownloads;
    private long previousFrontendDownloads;
    private long totalNewProjects;
    private long previousTotalNewProjects;
    private long totalNewUsers;
    private long previousTotalNewUsers;
    private long totalNewOrgs;
    private long previousTotalNewOrgs;

    private List<AnalyticsDataPoint> downloadsChart;
    private List<AnalyticsDataPoint> viewsChart;
    private List<AnalyticsDataPoint> newProjectsChart;
    private List<AnalyticsDataPoint> newUsersChart;
    private List<AnalyticsDataPoint> newOrgsChart;
    private List<AnalyticsDataPoint> apiDownloadsChart;

    public PlatformAnalyticsSummary() {}

    public long getTotalDownloads() { return totalDownloads; }
    public void setTotalDownloads(long totalDownloads) { this.totalDownloads = totalDownloads; }
    public long getPreviousTotalDownloads() { return previousTotalDownloads; }
    public void setPreviousTotalDownloads(long previousTotalDownloads) { this.previousTotalDownloads = previousTotalDownloads; }

    public long getTotalViews() { return totalViews; }
    public void setTotalViews(long totalViews) { this.totalViews = totalViews; }
    public long getPreviousTotalViews() { return previousTotalViews; }
    public void setPreviousTotalViews(long previousTotalViews) { this.previousTotalViews = previousTotalViews; }

    public long getApiDownloads() { return apiDownloads; }
    public void setApiDownloads(long apiDownloads) { this.apiDownloads = apiDownloads; }
    public long getPreviousApiDownloads() { return previousApiDownloads; }
    public void setPreviousApiDownloads(long previousApiDownloads) { this.previousApiDownloads = previousApiDownloads; }

    public long getFrontendDownloads() { return frontendDownloads; }
    public void setFrontendDownloads(long frontendDownloads) { this.frontendDownloads = frontendDownloads; }
    public long getPreviousFrontendDownloads() { return previousFrontendDownloads; }
    public void setPreviousFrontendDownloads(long previousFrontendDownloads) { this.previousFrontendDownloads = previousFrontendDownloads; }

    public long getTotalNewProjects() { return totalNewProjects; }
    public void setTotalNewProjects(long totalNewProjects) { this.totalNewProjects = totalNewProjects; }
    public long getPreviousTotalNewProjects() { return previousTotalNewProjects; }
    public void setPreviousTotalNewProjects(long previousTotalNewProjects) { this.previousTotalNewProjects = previousTotalNewProjects; }

    public long getTotalNewUsers() { return totalNewUsers; }
    public void setTotalNewUsers(long totalNewUsers) { this.totalNewUsers = totalNewUsers; }
    public long getPreviousTotalNewUsers() { return previousTotalNewUsers; }
    public void setPreviousTotalNewUsers(long previousTotalNewUsers) { this.previousTotalNewUsers = previousTotalNewUsers; }

    public long getTotalNewOrgs() { return totalNewOrgs; }
    public void setTotalNewOrgs(long totalNewOrgs) { this.totalNewOrgs = totalNewOrgs; }
    public long getPreviousTotalNewOrgs() { return previousTotalNewOrgs; }
    public void setPreviousTotalNewOrgs(long previousTotalNewOrgs) { this.previousTotalNewOrgs = previousTotalNewOrgs; }

    public List<AnalyticsDataPoint> getDownloadsChart() { return downloadsChart; }
    public void setDownloadsChart(List<AnalyticsDataPoint> downloadsChart) { this.downloadsChart = downloadsChart; }

    public List<AnalyticsDataPoint> getViewsChart() { return viewsChart; }
    public void setViewsChart(List<AnalyticsDataPoint> viewsChart) { this.viewsChart = viewsChart; }

    public List<AnalyticsDataPoint> getNewProjectsChart() { return newProjectsChart; }
    public void setNewProjectsChart(List<AnalyticsDataPoint> newProjectsChart) { this.newProjectsChart = newProjectsChart; }

    public List<AnalyticsDataPoint> getNewUsersChart() { return newUsersChart; }
    public void setNewUsersChart(List<AnalyticsDataPoint> newUsersChart) { this.newUsersChart = newUsersChart; }

    public List<AnalyticsDataPoint> getNewOrgsChart() { return newOrgsChart; }
    public void setNewOrgsChart(List<AnalyticsDataPoint> newOrgsChart) { this.newOrgsChart = newOrgsChart; }

    public List<AnalyticsDataPoint> getApiDownloadsChart() { return apiDownloadsChart; }
    public void setApiDownloadsChart(List<AnalyticsDataPoint> apiDownloadsChart) { this.apiDownloadsChart = apiDownloadsChart; }
}