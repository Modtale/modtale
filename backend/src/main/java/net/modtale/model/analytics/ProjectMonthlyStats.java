package net.modtale.model.analytics;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashMap;
import java.util.Map;

@Document(collection = "project_monthly_stats")
@CompoundIndexes({
        @CompoundIndex(def = "{'projectId': 1, 'year': 1, 'month': 1}", unique = true),
        @CompoundIndex(def = "{'authorId': 1, 'year': 1, 'month': 1}")
})
public class ProjectMonthlyStats {
    @Id
    private String id;

    private String projectId;
    private String authorId;
    private int year;
    private int month;

    private long totalViews;
    private long totalDownloads;

    private long apiDownloads;
    private long frontendDownloads;

    private Map<String, DayStats> days = new HashMap<>();

    private Map<String, Map<String, Integer>> versionDownloads = new HashMap<>();

    public ProjectMonthlyStats() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }

    public int getMonth() { return month; }
    public void setMonth(int month) { this.month = month; }

    public long getTotalViews() { return totalViews; }
    public void setTotalViews(long totalViews) { this.totalViews = totalViews; }

    public long getTotalDownloads() { return totalDownloads; }
    public void setTotalDownloads(long totalDownloads) { this.totalDownloads = totalDownloads; }

    public long getApiDownloads() { return apiDownloads; }
    public void setApiDownloads(long apiDownloads) { this.apiDownloads = apiDownloads; }

    public long getFrontendDownloads() { return frontendDownloads; }
    public void setFrontendDownloads(long frontendDownloads) { this.frontendDownloads = frontendDownloads; }

    public Map<String, DayStats> getDays() { return days; }
    public void setDays(Map<String, DayStats> days) { this.days = days; }

    public Map<String, Map<String, Integer>> getVersionDownloads() { return versionDownloads; }
    public void setVersionDownloads(Map<String, Map<String, Integer>> versionDownloads) { this.versionDownloads = versionDownloads; }

    public static class DayStats {
        private int v;
        private int d;

        public int getV() { return v; }
        public void setV(int v) { this.v = v; }
        public int getD() { return d; }
        public void setD(int d) { this.d = d; }
    }
}