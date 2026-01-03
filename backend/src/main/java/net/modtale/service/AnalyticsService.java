package net.modtale.service;

import net.modtale.model.analytics.*;
import net.modtale.model.resources.Mod;
import net.modtale.model.resources.ProjectMeta;
import net.modtale.repository.analytics.DownloadLogRepository;
import net.modtale.repository.analytics.ViewLogRepository;
import net.modtale.repository.resources.ModRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class AnalyticsService {

    private static final int CHART_BUFFER_DAYS = 14;

    @Autowired private DownloadLogRepository downloadLogRepository;
    @Autowired private ViewLogRepository viewLogRepository;
    @Autowired private ModRepository modRepository;
    @Autowired private MongoTemplate mongoTemplate;

    public void logDownload(String projectId, String versionId, String authorId) {
        DownloadLog log = new DownloadLog(projectId, versionId, authorId);
        downloadLogRepository.save(log);
    }

    public void logView(String projectId, String authorId) {
        ViewLog log = new ViewLog(projectId, authorId);
        viewLogRepository.save(log);
    }

    public CreatorAnalytics getCreatorDashboard(String username, String range, List<String> include) {
        LocalDate end = LocalDate.now();
        LocalDate start = calculateStartDate(range);

        LocalDate chartStart = start.minusDays(CHART_BUFFER_DAYS);

        LocalDate prevStart = start.minusDays(java.time.temporal.ChronoUnit.DAYS.between(start, end));

        long periodDownloads = downloadLogRepository.countByAuthorIdAndTimestampBetween(username, start.atStartOfDay(), end.atTime(23, 59, 59));
        long prevDownloads = downloadLogRepository.countByAuthorIdAndTimestampBetween(username, prevStart.atStartOfDay(), start.minusDays(1).atTime(23, 59, 59));

        long periodViews = viewLogRepository.countByAuthorIdAndTimestampBetween(username, start.atStartOfDay(), end.atTime(23, 59, 59));
        long prevViews = viewLogRepository.countByAuthorIdAndTimestampBetween(username, prevStart.atStartOfDay(), start.minusDays(1).atTime(23, 59, 59));

        long totalDownloads = downloadLogRepository.countByAuthorId(username);
        long totalViews = viewLogRepository.countByAuthorId(username);

        CreatorAnalytics analytics = new CreatorAnalytics();
        analytics.setTotalDownloads(totalDownloads);
        analytics.setTotalViews(totalViews);
        analytics.setPeriodDownloads(periodDownloads);
        analytics.setPreviousPeriodDownloads(prevDownloads);
        analytics.setPeriodViews(periodViews);
        analytics.setPreviousPeriodViews(prevViews);

        List<Mod> projects = modRepository.findByAuthor(username);
        Map<String, ProjectMeta> metaMap = new HashMap<>();
        Map<String, List<AnalyticsDataPoint>> projectDownloads = new HashMap<>();
        Map<String, List<AnalyticsDataPoint>> projectViews = new HashMap<>();

        for (Mod mod : projects) {
            metaMap.put(mod.getId(), new ProjectMeta(mod.getId(), mod.getTitle(), mod.getRating(), mod.getDownloadCount()));
            projectDownloads.put(mod.getId(), getTimeSeries(mod.getId(), "downloads", chartStart, end, true));
            projectViews.put(mod.getId(), getTimeSeries(mod.getId(), "views", chartStart, end, true));
        }

        analytics.setProjectMeta(metaMap);
        analytics.setProjectDownloads(projectDownloads);
        analytics.setProjectViews(projectViews);

        return analytics;
    }

    public ProjectAnalyticsDetail getProjectAnalytics(String projectId, String username, String range) {
        LocalDate end = LocalDate.now();
        LocalDate start = calculateStartDate(range);

        LocalDate chartStart = start.minusDays(CHART_BUFFER_DAYS);

        Mod mod = modRepository.findById(projectId).orElse(null);
        String title = (mod != null) ? mod.getTitle() : "Unknown";

        ProjectAnalyticsDetail detail = new ProjectAnalyticsDetail();
        detail.setProjectId(projectId);
        detail.setProjectTitle(title);

        detail.setTotalDownloads(downloadLogRepository.countByProjectId(projectId));
        detail.setTotalViews(viewLogRepository.countByProjectId(projectId));

        detail.setViews(getTimeSeries(projectId, "views", chartStart, end, true));

        Map<String, List<AnalyticsDataPoint>> verDownloads = new HashMap<>();

        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("projectId").is(projectId).and("timestamp").gte(start.atStartOfDay())),
                Aggregation.group("versionId").count().as("count"),
                Aggregation.sort(Sort.Direction.DESC, "count"),
                Aggregation.limit(10)
        );

        AggregationResults<Map> results = mongoTemplate.aggregate(agg, "download_logs", Map.class);
        for (Map result : results.getMappedResults()) {
            String vid = (String) result.get("_id");
            if(vid != null) {
                verDownloads.put(vid, getVersionTimeSeries(projectId, vid, chartStart, end));
            }
        }
        detail.setVersionDownloads(verDownloads);

        detail.setRatingHistory(new ArrayList<>());

        return detail;
    }

    private LocalDate calculateStartDate(String range) {
        if ("7d".equals(range)) return LocalDate.now().minusDays(7);
        if ("90d".equals(range)) return LocalDate.now().minusDays(90);
        if ("1y".equals(range)) return LocalDate.now().minusYears(1);
        return LocalDate.now().minusDays(30);
    }

    private List<AnalyticsDataPoint> getTimeSeries(String id, String type, LocalDate start, LocalDate end, boolean isProject) {
        String collection = type.equals("downloads") ? "download_logs" : "view_logs";
        String idField = isProject ? "projectId" : "authorId";

        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where(idField).is(id).and("timestamp").gte(start.atStartOfDay()).lte(end.atTime(23, 59, 59))),
                Aggregation.project().and("timestamp").dateAsFormattedString("%Y-%m-%d").as("date"),
                Aggregation.group("date").count().as("count"),
                Aggregation.sort(Sort.Direction.ASC, "_id")
        );

        AggregationResults<Map> results = mongoTemplate.aggregate(agg, collection, Map.class);
        Map<String, Integer> dataMap = new HashMap<>();
        for (Map r : results.getMappedResults()) {
            dataMap.put((String) r.get("_id"), ((Number) r.get("count")).intValue());
        }

        return fillDates(start, end, dataMap);
    }

    private List<AnalyticsDataPoint> getVersionTimeSeries(String pid, String vid, LocalDate start, LocalDate end) {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("projectId").is(pid).and("versionId").is(vid).and("timestamp").gte(start.atStartOfDay()).lte(end.atTime(23, 59, 59))),
                Aggregation.project().and("timestamp").dateAsFormattedString("%Y-%m-%d").as("date"),
                Aggregation.group("date").count().as("count"),
                Aggregation.sort(Sort.Direction.ASC, "_id")
        );

        AggregationResults<Map> results = mongoTemplate.aggregate(agg, "download_logs", Map.class);
        Map<String, Integer> dataMap = new HashMap<>();
        for (Map r : results.getMappedResults()) {
            dataMap.put((String) r.get("_id"), ((Number) r.get("count")).intValue());
        }
        return fillDates(start, end, dataMap);
    }

    private List<AnalyticsDataPoint> fillDates(LocalDate start, LocalDate end, Map<String, Integer> data) {
        List<AnalyticsDataPoint> points = new ArrayList<>();
        LocalDate curr = start;
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        while (!curr.isAfter(end)) {
            String d = curr.format(fmt);
            points.add(new AnalyticsDataPoint(d, data.getOrDefault(d, 0)));
            curr = curr.plusDays(1);
        }
        return points;
    }
}