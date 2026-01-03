package net.modtale.model.analytics;

import net.modtale.model.resources.ProjectMeta;

import java.util.List;
import java.util.Map;

public class CreatorAnalytics {
    private long totalDownloads;
    private long totalViews;

    private long periodDownloads;
    private long previousPeriodDownloads;
    private long periodViews;
    private long previousPeriodViews;

    private Map<String, List<AnalyticsDataPoint>> projectDownloads;
    private Map<String, List<AnalyticsDataPoint>> projectViews;

    private Map<String, ProjectMeta> projectMeta;

    public CreatorAnalytics() {}

    public CreatorAnalytics(long totalDownloads, long totalViews,
                            long periodDownloads, long previousPeriodDownloads,
                            long periodViews, long previousPeriodViews,
                            Map<String, List<AnalyticsDataPoint>> projectDownloads,
                            Map<String, List<AnalyticsDataPoint>> projectViews,
                            Map<String, ProjectMeta> projectMeta) {
        this.totalDownloads = totalDownloads;
        this.totalViews = totalViews;
        this.periodDownloads = periodDownloads;
        this.previousPeriodDownloads = previousPeriodDownloads;
        this.periodViews = periodViews;
        this.previousPeriodViews = previousPeriodViews;
        this.projectDownloads = projectDownloads;
        this.projectViews = projectViews;
        this.projectMeta = projectMeta;
    }

    public long getTotalDownloads() { return totalDownloads; }
    public void setTotalDownloads(long totalDownloads) { this.totalDownloads = totalDownloads; }
    public long getTotalViews() { return totalViews; }
    public void setTotalViews(long totalViews) { this.totalViews = totalViews; }

    public long getPeriodDownloads() { return periodDownloads; }
    public void setPeriodDownloads(long periodDownloads) { this.periodDownloads = periodDownloads; }
    public long getPreviousPeriodDownloads() { return previousPeriodDownloads; }
    public void setPreviousPeriodDownloads(long previousPeriodDownloads) { this.previousPeriodDownloads = previousPeriodDownloads; }
    public long getPeriodViews() { return periodViews; }
    public void setPeriodViews(long periodViews) { this.periodViews = periodViews; }
    public long getPreviousPeriodViews() { return previousPeriodViews; }
    public void setPreviousPeriodViews(long previousPeriodViews) { this.previousPeriodViews = previousPeriodViews; }

    public Map<String, List<AnalyticsDataPoint>> getProjectDownloads() { return projectDownloads; }
    public void setProjectDownloads(Map<String, List<AnalyticsDataPoint>> projectDownloads) { this.projectDownloads = projectDownloads; }
    public Map<String, List<AnalyticsDataPoint>> getProjectViews() { return projectViews; }
    public void setProjectViews(Map<String, List<AnalyticsDataPoint>> projectViews) { this.projectViews = projectViews; }
    public Map<String, ProjectMeta> getProjectMeta() { return projectMeta; }
    public void setProjectMeta(Map<String, ProjectMeta> projectMeta) { this.projectMeta = projectMeta; }
}