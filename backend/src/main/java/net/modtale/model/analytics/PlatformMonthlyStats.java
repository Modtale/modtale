package net.modtale.model.analytics;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashMap;
import java.util.Map;

@Document(collection = "platform_monthly_stats")
@CompoundIndex(def = "{'year': 1, 'month': 1}", unique = true)
public class PlatformMonthlyStats {
    @Id
    private String id;

    private int year;
    private int month;

    private long totalViews;
    private long totalDownloads;
    private long apiDownloads;
    private long frontendDownloads;

    private Map<String, DayStats> days = new HashMap<>();

    public PlatformMonthlyStats() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

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

    public static class DayStats {
        private int v;
        private int d;

        public int getV() { return v; }
        public void setV(int v) { this.v = v; }
        public int getD() { return d; }
        public void setD(int d) { this.d = d; }
    }
}