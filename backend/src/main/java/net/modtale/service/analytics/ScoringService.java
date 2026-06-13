package net.modtale.service.analytics;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import net.modtale.model.analytics.ProjectMonthlyStats;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.aggregation.ConvertOperators;
import org.springframework.data.mongodb.core.aggregation.DateOperators;
import org.springframework.data.mongodb.core.aggregation.LookupOperation;
import org.springframework.data.mongodb.core.aggregation.ObjectOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ScoringService {

    private static final int MIN_DOWNLOADS_FOR_SCORED_RANKING = 10;
    private static final int BULK_BATCH_SIZE = 1000;
    private static final long UNRANKED = 0L;

    private final MongoTemplate mongoTemplate;

    public ScoringService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Scheduled(cron = "${app.analytics.score-refresh.cron:0 30 0 * * ?}")
    public void updateProjectScores() {
        LocalDate today = LocalDate.now();
        Set<String> dirtyProjectIds = findDirtyProjectIds(today);
        BulkWriter bulkWriter = new BulkWriter(mongoTemplate);

        if (!dirtyProjectIds.isEmpty()) {
            Map<String, ScoreRefresh> refreshedScores = refreshDirtyProjectScores(today, dirtyProjectIds);
            for (ScoreRefresh score : refreshedScores.values()) {
                bulkWriter.update(score.projectId(), new Update()
                        .set("trendScore", score.trendScore())
                        .set("relevanceScore", score.relevanceScore())
                        .set("popularScore", score.popularScore())
                        .set("downloads7d", score.downloads7d())
                        .set("downloads30d", score.downloads30d())
                        .set("downloads90d", score.downloads90d())
                        .set("rankingDirty", false));
            }

            dirtyProjectIds.stream()
                    .filter(projectId -> !refreshedScores.containsKey(projectId))
                    .forEach(projectId -> bulkWriter.update(projectId, resetScores()));
            bulkWriter.flush();
        }

        List<RankableProject> rankableProjects = loadRankableProjects();
        Map<String, RankSnapshot> ranks = calculateRanks(rankableProjects);
        applyRankChanges(rankableProjects, ranks, bulkWriter);
        bulkWriter.flush();
    }

    public void ensureScores(List<Project> projects) {
        // Score refreshes are scheduled daily so read requests never trigger
        // ad-hoc recalculations while analytics are only bucketed by day.
    }

    public void markProjectRankingDirty(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return;
        }
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(projectId)),
                new Update().set("rankingDirty", true),
                Project.class
        );
    }

    public void markProjectRankingDirty(Project project) {
        if (project != null) {
            project.setRankingDirty(true);
        }
    }

    private Set<String> findDirtyProjectIds(LocalDate today) {
        Set<String> dirtyProjectIds = new HashSet<>();

        Query markerQuery = new Query(Criteria.where("rankingDirty").is(true));
        markerQuery.fields().include("_id");
        mongoTemplate.find(markerQuery, Project.class).forEach(project -> dirtyProjectIds.add(project.getId()));

        List<Criteria> boundaryCriteria = List.of(
                statsDateCriteria(today.minusDays(1)),
                statsDateCriteria(today.minusDays(8)),
                statsDateCriteria(today.minusDays(15)),
                statsDateCriteria(today.minusDays(31)),
                statsDateCriteria(today.minusDays(91))
        );
        Query boundaryQuery = new Query(new Criteria().orOperator(boundaryCriteria.toArray(new Criteria[0])));
        boundaryQuery.fields().include("projectId");
        for (ProjectMonthlyStats stats : mongoTemplate.find(boundaryQuery, ProjectMonthlyStats.class)) {
            if (stats.getProjectId() != null) {
                dirtyProjectIds.add(stats.getProjectId());
            }
        }

        return dirtyProjectIds;
    }

    private Criteria statsDateCriteria(LocalDate date) {
        return new Criteria().andOperator(
                Criteria.where("year").is(date.getYear()),
                Criteria.where("month").is(date.getMonthValue()),
                Criteria.where("days." + date.getDayOfMonth() + ".d").gt(0)
        );
    }

    private Map<String, ScoreRefresh> refreshDirtyProjectScores(LocalDate today, Set<String> dirtyProjectIds) {
        if (dirtyProjectIds.isEmpty()) {
            return Map.of();
        }

        Date todayStart = Date.from(today.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date week1Start = Date.from(today.minusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date week2Start = Date.from(today.minusDays(14).atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date monthStart = Date.from(today.minusDays(30).atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date quarterStart = Date.from(today.minusDays(90).atStartOfDay(ZoneId.systemDefault()).toInstant());

        long totalPublished = mongoTemplate.count(
                new Query(Criteria.where("status").is(ProjectStatus.PUBLISHED)
                        .and("downloadCount").gte(MIN_DOWNLOADS_FOR_SCORED_RANKING)),
                Project.class
        );
        double medianDownloads = calculatePercentileDownloads(totalPublished, 0.50);
        double noiseFloor = calculatePercentileDownloads(totalPublished, 0.01);
        double logMedian = Math.log10(Math.max(10, medianDownloads));
        double dampeningK = Math.max(5, noiseFloor);

        int prevYear = today.minusMonths(3).getYear();
        int prevMonth = today.minusMonths(3).getMonthValue();

        List<AggregationOperation> pipeline = new ArrayList<>();
        pipeline.add(Aggregation.match(Criteria.where("projectId").in(dirtyProjectIds)));
        pipeline.add(Aggregation.match(new Criteria().orOperator(
                Criteria.where("year").gt(prevYear),
                Criteria.where("year").is(prevYear).and("month").gte(prevMonth)
        )));
        pipeline.add(Aggregation.addFields()
                .addField("daysArray")
                .withValue(ObjectOperators.ObjectToArray.valueOfToArray("days"))
                .build());
        pipeline.add(Aggregation.unwind("daysArray", true));
        pipeline.add(Aggregation.addFields()
                .addField("logDate")
                .withValue(DateOperators.DateFromParts.dateFromParts()
                        .year("$year")
                        .month("$month")
                        .day(ConvertOperators.ToInt.toInt("$daysArray.k")))
                .build());
        pipeline.add(Aggregation.match(Criteria.where("logDate").gte(quarterStart).lt(todayStart)));
        pipeline.add(Aggregation.group("projectId")
                .sum(ConditionalOperators.when(new Criteria().andOperator(
                        Criteria.where("logDate").gte(week1Start),
                        Criteria.where("logDate").lt(todayStart)
                )).then("$daysArray.v.d").otherwise(0)).as("currentWeek")
                .sum(ConditionalOperators.when(new Criteria().andOperator(
                        Criteria.where("logDate").gte(week2Start),
                        Criteria.where("logDate").lt(week1Start)
                )).then("$daysArray.v.d").otherwise(0)).as("previousWeek")
                .sum(ConditionalOperators.when(new Criteria().andOperator(
                        Criteria.where("logDate").gte(monthStart),
                        Criteria.where("logDate").lt(todayStart)
                )).then("$daysArray.v.d").otherwise(0)).as("recent")
                .sum(ConditionalOperators.when(new Criteria().andOperator(
                        Criteria.where("logDate").gte(quarterStart),
                        Criteria.where("logDate").lt(todayStart)
                )).then("$daysArray.v.d").otherwise(0)).as("quarter"));
        pipeline.add(LookupOperation.newLookup()
                .from("projects")
                .localField("_id")
                .foreignField("_id")
                .as("projectData"));
        pipeline.add(Aggregation.unwind("projectData", false));
        pipeline.add(Aggregation.project("currentWeek", "previousWeek", "recent", "quarter")
                .and("projectData.downloadCount").as("totalDownloads")
                .and("projectData.favoriteCount").as("favoriteCount"));

        @SuppressWarnings("deprecation")
        Aggregation aggregation = Aggregation.newAggregation(ProjectMonthlyStats.class, pipeline);
        AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, ProjectMonthlyStats.class, Document.class);
        Map<String, ScoreRefresh> refreshedScores = new HashMap<>();

        for (Document doc : results) {
            String projectId = doc.getString("_id");
            if (projectId == null) {
                continue;
            }

            int downloads7d = doc.getInteger("currentWeek", 0);
            int previousWeek = doc.getInteger("previousWeek", 0);
            int downloads30d = doc.getInteger("recent", 0);
            int downloads90d = doc.getInteger("quarter", 0);
            int downloadCount = doc.getInteger("totalDownloads", 0);
            int favoriteCount = doc.getInteger("favoriteCount", 0);

            int trendScore = 0;
            double popularScore = 0.0;
            double relevanceScore = 0.0;

            if (downloadCount >= MIN_DOWNLOADS_FOR_SCORED_RANKING) {
                if (downloads7d > previousWeek) {
                    double dynamicGrowthRatio = (double) (downloads7d + dampeningK) / (previousWeek + dampeningK);
                    double growthDelta = Math.sqrt(downloads7d - previousWeek);
                    double sizeWeight = Math.exp(-Math.pow(Math.log10(Math.max(10, downloadCount)) - logMedian, 2) / 2.2);
                    trendScore = (int) (dynamicGrowthRatio * growthDelta * sizeWeight * 1000);
                }
                popularScore = downloadCount + (favoriteCount * 10.0);
                double engagementRatio = downloadCount < dampeningK * 2 ? 0 : (double) favoriteCount / Math.max(1, downloadCount);
                relevanceScore = downloads30d * (1.0 + (engagementRatio * 5.0));
            }

            refreshedScores.put(projectId, new ScoreRefresh(
                    projectId,
                    downloads7d,
                    downloads30d,
                    downloads90d,
                    trendScore,
                    relevanceScore,
                    popularScore
            ));
        }

        return refreshedScores;
    }

    private List<RankableProject> loadRankableProjects() {
        Query query = new Query(Criteria.where("status").in(ProjectStatus.PUBLISHED, ProjectStatus.ARCHIVED));
        query.fields()
                .include("_id")
                .include("downloadCount")
                .include("favoriteCount")
                .include("downloads30d")
                .include("trendScore")
                .include("relevanceScore")
                .include("popularScore")
                .include("trendingRank")
                .include("popularRank")
                .include("relevanceRank");

        return mongoTemplate.find(query, Project.class).stream()
                .map(project -> new RankableProject(
                        project.getId(),
                        project.getDownloadCount(),
                        project.getFavoriteCount(),
                        project.getDownloads30d(),
                        project.getTrendScore(),
                        project.getRelevanceScore(),
                        project.getPopularScore(),
                        project.getTrendingRank(),
                        project.getPopularRank(),
                        project.getRelevanceRank()
                ))
                .toList();
    }

    private Map<String, RankSnapshot> calculateRanks(List<RankableProject> projects) {
        Map<String, RankSnapshotBuilder> builders = projects.stream()
                .collect(Collectors.toMap(RankableProject::projectId, ignored -> new RankSnapshotBuilder()));

        assignTrendingRanks(projects, builders);
        assignPopularRanks(projects, builders);
        assignRelevanceRanks(projects, builders);

        return builders.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().build()));
    }

    private void assignTrendingRanks(List<RankableProject> projects, Map<String, RankSnapshotBuilder> builders) {
        List<RankableProject> ranked = projects.stream()
                .filter(project -> project.downloadCount() >= MIN_DOWNLOADS_FOR_SCORED_RANKING)
                .sorted(Comparator
                        .comparingInt(RankableProject::trendScore).reversed()
                        .thenComparing(RankableProject::projectId))
                .toList();

        assignRanks(ranked, builders, RankSnapshotBuilder::setTrendingRank);
    }

    private void assignPopularRanks(List<RankableProject> projects, Map<String, RankSnapshotBuilder> builders) {
        List<RankableProject> ranked = projects.stream()
                .sorted(Comparator
                        .comparingDouble(this::effectivePopularScore).reversed()
                        .thenComparing(RankableProject::downloadCount, Comparator.reverseOrder())
                        .thenComparing(RankableProject::favoriteCount, Comparator.reverseOrder())
                        .thenComparing(RankableProject::projectId))
                .toList();

        assignRanks(ranked, builders, RankSnapshotBuilder::setPopularRank);
    }

    private void assignRelevanceRanks(List<RankableProject> projects, Map<String, RankSnapshotBuilder> builders) {
        List<RankableProject> ranked = projects.stream()
                .sorted(Comparator
                        .comparingDouble(this::effectiveRelevanceScore).reversed()
                        .thenComparing(RankableProject::downloads30d, Comparator.reverseOrder())
                        .thenComparing(RankableProject::favoriteCount, Comparator.reverseOrder())
                        .thenComparing(RankableProject::projectId))
                .toList();

        assignRanks(ranked, builders, RankSnapshotBuilder::setRelevanceRank);
    }

    private void assignRanks(
            List<RankableProject> ranked,
            Map<String, RankSnapshotBuilder> builders,
            BiConsumer<RankSnapshotBuilder, Long> rankSetter
    ) {
        for (int i = 0; i < ranked.size(); i++) {
            RankSnapshotBuilder builder = builders.get(ranked.get(i).projectId());
            if (builder != null) {
                rankSetter.accept(builder, i + 1L);
            }
        }
    }

    private void applyRankChanges(
            List<RankableProject> projects,
            Map<String, RankSnapshot> ranks,
            BulkWriter bulkWriter
    ) {
        for (RankableProject project : projects) {
            RankSnapshot rank = ranks.getOrDefault(project.projectId(), RankSnapshot.UNRANKED);
            if (project.trendingRank() == rank.trendingRank()
                    && project.popularRank() == rank.popularRank()
                    && project.relevanceRank() == rank.relevanceRank()) {
                continue;
            }

            bulkWriter.update(project.projectId(), new Update()
                    .set("trendingRank", rank.trendingRank())
                    .set("popularRank", rank.popularRank())
                    .set("relevanceRank", rank.relevanceRank()));
        }

        Query staleRankQuery = new Query(new Criteria().andOperator(
                Criteria.where("status").nin(ProjectStatus.PUBLISHED, ProjectStatus.ARCHIVED),
                new Criteria().orOperator(
                        Criteria.where("trendingRank").gt(0),
                        Criteria.where("popularRank").gt(0),
                        Criteria.where("relevanceRank").gt(0),
                        Criteria.where("rankingDirty").is(true)
                )
        ));
        staleRankQuery.fields().include("_id");
        for (Project project : mongoTemplate.find(staleRankQuery, Project.class)) {
            bulkWriter.update(project.getId(), resetScores());
        }
    }

    private double effectivePopularScore(RankableProject project) {
        if (project.popularScore() > 0) {
            return project.popularScore();
        }
        if (project.downloadCount() >= MIN_DOWNLOADS_FOR_SCORED_RANKING) {
            return project.downloadCount() + (project.favoriteCount() * 10.0);
        }
        return project.popularScore();
    }

    private double effectiveRelevanceScore(RankableProject project) {
        if (project.relevanceScore() > 0) {
            return project.relevanceScore();
        }
        if (project.downloadCount() >= MIN_DOWNLOADS_FOR_SCORED_RANKING && project.downloads30d() > 0) {
            return project.downloads30d() * (1.0 + (((double) project.favoriteCount() / project.downloadCount()) * 5.0));
        }
        return project.relevanceScore();
    }

    private double calculatePercentileDownloads(long totalCount, double percentile) {
        if (totalCount == 0) {
            return 10.0;
        }

        Query query = new Query(Criteria.where("status").is(ProjectStatus.PUBLISHED)
                .and("downloadCount").gte(MIN_DOWNLOADS_FOR_SCORED_RANKING))
                .with(Sort.by(Sort.Direction.ASC, "downloadCount"))
                .skip((long) (totalCount * percentile))
                .limit(1);
        query.fields().include("downloadCount");

        Project project = mongoTemplate.findOne(query, Project.class);
        return project != null ? project.getDownloadCount() : 10.0;
    }

    private Update resetScores() {
        return new Update()
                .set("trendScore", 0)
                .set("relevanceScore", 0.0)
                .set("popularScore", 0.0)
                .set("trendingRank", UNRANKED)
                .set("popularRank", UNRANKED)
                .set("relevanceRank", UNRANKED)
                .set("downloads7d", 0)
                .set("downloads30d", 0)
                .set("downloads90d", 0)
                .set("rankingDirty", false);
    }

    private record ScoreRefresh(
            String projectId,
            int downloads7d,
            int downloads30d,
            int downloads90d,
            int trendScore,
            double relevanceScore,
            double popularScore
    ) {}

    private record RankableProject(
            String projectId,
            int downloadCount,
            int favoriteCount,
            int downloads30d,
            int trendScore,
            double relevanceScore,
            double popularScore,
            long trendingRank,
            long popularRank,
            long relevanceRank
    ) {}

    private record RankSnapshot(
            long trendingRank,
            long popularRank,
            long relevanceRank
    ) {
        private static final RankSnapshot UNRANKED = new RankSnapshot(
                ScoringService.UNRANKED,
                ScoringService.UNRANKED,
                ScoringService.UNRANKED
        );
    }

    private static final class RankSnapshotBuilder {
        private long trendingRank = UNRANKED;
        private long popularRank = UNRANKED;
        private long relevanceRank = UNRANKED;

        private void setTrendingRank(long trendingRank) {
            this.trendingRank = trendingRank;
        }

        private void setPopularRank(long popularRank) {
            this.popularRank = popularRank;
        }

        private void setRelevanceRank(long relevanceRank) {
            this.relevanceRank = relevanceRank;
        }

        private RankSnapshot build() {
            return new RankSnapshot(trendingRank, popularRank, relevanceRank);
        }
    }

    private static final class BulkWriter {
        private final MongoTemplate mongoTemplate;
        private BulkOperations bulkOps;
        private int counter;

        private BulkWriter(MongoTemplate mongoTemplate) {
            this.mongoTemplate = mongoTemplate;
            this.bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Project.class);
        }

        private void update(String projectId, Update update) {
            bulkOps.updateOne(new Query(Criteria.where("_id").is(projectId)), update);
            counter++;
            if (counter >= BULK_BATCH_SIZE) {
                flush();
            }
        }

        private void flush() {
            if (counter == 0) {
                return;
            }
            bulkOps.execute();
            bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Project.class);
            counter = 0;
        }
    }
}
