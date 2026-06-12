package net.modtale.service.analytics;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.modtale.model.analytics.PlatformAnalyticsSummary;
import net.modtale.model.analytics.PlatformMonthlyStats;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Service
public class PlatformAnalyticsQueryService {

    private final MongoTemplate mongoTemplate;
    private final AnalyticsQuerySupportService analyticsQuerySupportService;

    public PlatformAnalyticsQueryService(
            MongoTemplate mongoTemplate,
            AnalyticsQuerySupportService analyticsQuerySupportService
    ) {
        this.mongoTemplate = mongoTemplate;
        this.analyticsQuerySupportService = analyticsQuerySupportService;
    }

    public PlatformAnalyticsSummary getPlatformAnalytics(String range) {
        AnalyticsQuerySupportService.DateWindow window = analyticsQuerySupportService.buildDateWindow(range);

        List<PlatformMonthlyStats> stats = mongoTemplate.find(Query.query(new Criteria().orOperator(
                Criteria.where("year").gt(window.fetchStart().getYear()),
                Criteria.where("year").is(window.fetchStart().getYear()).and("month").gte(window.fetchStart().getMonthValue())
        )), PlatformMonthlyStats.class);

        PlatformAnalyticsSummary summary = new PlatformAnalyticsSummary();
        Map<LocalDate, Integer> downloadSeries = new HashMap<>();
        Map<LocalDate, Integer> apiSeries = new HashMap<>();
        Map<LocalDate, Integer> viewSeries = new HashMap<>();
        Map<LocalDate, Integer> projectSeries = new HashMap<>();
        Map<LocalDate, Integer> userSeries = new HashMap<>();
        Map<LocalDate, Integer> orgSeries = new HashMap<>();

        long currentDownloads = 0;
        long previousDownloads = 0;
        long currentViews = 0;
        long previousViews = 0;
        long currentApiDownloads = 0;
        long previousApiDownloads = 0;
        long currentFrontendDownloads = 0;
        long previousFrontendDownloads = 0;
        long currentProjects = 0;
        long previousProjects = 0;
        long currentUsers = 0;
        long previousUsers = 0;
        long currentOrgs = 0;
        long previousOrgs = 0;

        for (PlatformMonthlyStats stat : stats) {
            YearMonth month = YearMonth.of(stat.getYear(), stat.getMonth());
            if (stat.getDays() == null) {
                continue;
            }

            for (Map.Entry<String, PlatformMonthlyStats.DayStats> entry : stat.getDays().entrySet()) {
                LocalDate date = analyticsQuerySupportService.parseStatDate(month, entry.getKey(), "platform analytics");
                if (date == null) {
                    continue;
                }

                PlatformMonthlyStats.DayStats dayStats = entry.getValue();
                if (!date.isBefore(window.chartStart()) && !date.isAfter(window.end())) {
                    downloadSeries.put(date, dayStats.getD());
                    apiSeries.put(date, dayStats.getA());
                    viewSeries.put(date, dayStats.getV());
                    projectSeries.put(date, dayStats.getN());
                    userSeries.put(date, dayStats.getU());
                    orgSeries.put(date, dayStats.getO());
                }

                if (!date.isBefore(window.start()) && !date.isAfter(window.end())) {
                    currentDownloads += dayStats.getD();
                    currentViews += dayStats.getV();
                    currentApiDownloads += dayStats.getA();
                    currentFrontendDownloads += dayStats.getF();
                    currentProjects += dayStats.getN();
                    currentUsers += dayStats.getU();
                    currentOrgs += dayStats.getO();
                } else if (!date.isBefore(window.comparisonStart()) && !date.isAfter(window.comparisonEnd())) {
                    previousDownloads += dayStats.getD();
                    previousViews += dayStats.getV();
                    previousApiDownloads += dayStats.getA();
                    previousFrontendDownloads += dayStats.getF();
                    previousProjects += dayStats.getN();
                    previousUsers += dayStats.getU();
                    previousOrgs += dayStats.getO();
                }
            }
        }

        summary.setTotalDownloads(currentDownloads);
        summary.setPreviousTotalDownloads(previousDownloads);
        summary.setTotalViews(currentViews);
        summary.setPreviousTotalViews(previousViews);
        summary.setApiDownloads(currentApiDownloads);
        summary.setPreviousApiDownloads(previousApiDownloads);
        summary.setFrontendDownloads(currentFrontendDownloads);
        summary.setPreviousFrontendDownloads(previousFrontendDownloads);
        summary.setTotalNewProjects(currentProjects);
        summary.setPreviousTotalNewProjects(previousProjects);
        summary.setTotalNewUsers(currentUsers);
        summary.setPreviousTotalNewUsers(previousUsers);
        summary.setTotalNewOrgs(currentOrgs);
        summary.setPreviousTotalNewOrgs(previousOrgs);

        summary.setDownloadsChart(analyticsQuerySupportService.fillDates(window.chartStart(), window.end(), downloadSeries));
        summary.setApiDownloadsChart(analyticsQuerySupportService.fillDates(window.chartStart(), window.end(), apiSeries));
        summary.setViewsChart(analyticsQuerySupportService.fillDates(window.chartStart(), window.end(), viewSeries));
        summary.setNewProjectsChart(analyticsQuerySupportService.fillDates(window.chartStart(), window.end(), projectSeries));
        summary.setNewUsersChart(analyticsQuerySupportService.fillDates(window.chartStart(), window.end(), userSeries));
        summary.setNewOrgsChart(analyticsQuerySupportService.fillDates(window.chartStart(), window.end(), orgSeries));
        return summary;
    }
}
