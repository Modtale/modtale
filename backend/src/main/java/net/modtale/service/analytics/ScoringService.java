package net.modtale.service.analytics;

import net.modtale.model.analytics.ProjectMonthlyStats;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
public class ScoringService {

    private static final Logger logger = LoggerFactory.getLogger(ScoringService.class);

    private final MongoTemplate mongoTemplate;

    public ScoringService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Scheduled(cron = "0 30 0 * * ?")
    public void updateProjectScores() {
        LocalDate today = LocalDate.now();
        Date todayStart = Date.from(today.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date week1Start = Date.from(today.minusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date week2Start = Date.from(today.minusDays(14).atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date monthStart = Date.from(today.minusDays(30).atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date quarterStart = Date.from(today.minusDays(90).atStartOfDay(ZoneId.systemDefault()).toInstant());

        long totalPublished = mongoTemplate.count(new Query(Criteria.where("status").is(ProjectStatus.PUBLISHED).and("downloadCount").gte(10)), Project.class);
        double medianDownloads = calculatePercentileDownloads(totalPublished, 0.50);
        double noiseFloor = calculatePercentileDownloads(totalPublished, 0.01);
        double logMedian = Math.log10(Math.max(10, medianDownloads));
        double dampeningK = Math.max(5, noiseFloor);

        int prevYear = today.minusMonths(3).getYear();
        int prevMonth = today.minusMonths(3).getMonthValue();

        List<AggregationOperation> pipeline = new ArrayList<>();
        pipeline.add(Aggregation.match(new Criteria().orOperator(Criteria.where("year").gt(prevYear), Criteria.where("year").is(prevYear).and("month").gte(prevMonth))));
        pipeline.add(Aggregation.addFields().addField("daysArray").withValue(ObjectOperators.ObjectToArray.valueOfToArray("days")).build());
        pipeline.add(Aggregation.unwind("daysArray", true));
        pipeline.add(Aggregation.addFields().addField("logDate").withValue(DateOperators.DateFromParts.dateFromParts().year("$year").month("$month").day(ConvertOperators.ToInt.toInt("$daysArray.k"))).build());
        pipeline.add(Aggregation.match(Criteria.where("logDate").gte(quarterStart).lt(todayStart)));

        pipeline.add(Aggregation.group("projectId")
                .sum(ConditionalOperators.when(new Criteria().andOperator(Criteria.where("logDate").gte(week1Start), Criteria.where("logDate").lt(todayStart))).then("$daysArray.v.d").otherwise(0)).as("currentWeek")
                .sum(ConditionalOperators.when(new Criteria().andOperator(Criteria.where("logDate").gte(week2Start), Criteria.where("logDate").lt(week1Start))).then("$daysArray.v.d").otherwise(0)).as("previousWeek")
                .sum(ConditionalOperators.when(new Criteria().andOperator(Criteria.where("logDate").gte(monthStart), Criteria.where("logDate").lt(todayStart))).then("$daysArray.v.d").otherwise(0)).as("recent")
                .sum(ConditionalOperators.when(new Criteria().andOperator(Criteria.where("logDate").gte(quarterStart), Criteria.where("logDate").lt(todayStart))).then("$daysArray.v.d").otherwise(0)).as("quarter"));

        pipeline.add(LookupOperation.newLookup().from("projects").localField("_id").foreignField("_id").as("projectData"));
        pipeline.add(Aggregation.unwind("projectData", false));
        pipeline.add(Aggregation.project("currentWeek", "previousWeek", "recent", "quarter").and("projectData.downloadCount").as("totalDownloads").and("projectData.favoriteCount").as("favoriteCount"));

        @SuppressWarnings("deprecation")
        Aggregation mainAgg = Aggregation.newAggregation(ProjectMonthlyStats.class, pipeline);
        AggregationResults<Document> results = mongoTemplate.aggregate(mainAgg, ProjectMonthlyStats.class, Document.class);
        Set<String> activeProjectIds = new HashSet<>();
        BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Project.class);
        int counter = 0;

        for (Document doc : results) {
            String projectId = doc.getString("_id");
            if (projectId == null) continue;
            activeProjectIds.add(projectId);

            int currentWeek = doc.getInteger("currentWeek", 0);
            int previousWeek = doc.getInteger("previousWeek", 0);
            int recent = doc.getInteger("recent", 0);
            int quarter = doc.getInteger("quarter", 0);
            int totalDownloads = doc.getInteger("totalDownloads", 0);
            int favoriteCount = doc.getInteger("favoriteCount", 0);

            int trendScore = 0; double popularScore = 0.0; double relevanceScore = 0.0;

            if (totalDownloads >= 10) {
                if (currentWeek > previousWeek) {
                    double dynamicGrowthRatio = (double) (currentWeek + dampeningK) / (previousWeek + dampeningK);
                    double growthDelta = Math.sqrt(currentWeek - previousWeek);
                    double sizeWeight = Math.exp(-Math.pow(Math.log10(Math.max(10, totalDownloads)) - logMedian, 2) / 2.2);
                    trendScore = (int) (dynamicGrowthRatio * growthDelta * sizeWeight * 1000);
                }
                popularScore = totalDownloads + (favoriteCount * 10.0);
                double engagementRatio = totalDownloads < dampeningK * 2 ? 0 : (double) favoriteCount / Math.max(1, totalDownloads);
                relevanceScore = recent * (1.0 + (engagementRatio * 5.0));
            }

            bulkOps.updateOne(new Query(Criteria.where("_id").is(projectId)), new Update().set("trendScore", trendScore).set("relevanceScore", relevanceScore).set("popularScore", popularScore).set("downloads7d", currentWeek).set("downloads30d", recent).set("downloads90d", quarter));
            if (++counter >= 1000) { bulkOps.execute(); bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Project.class); counter = 0; }
        }

        Query decayQuery = new Query(new Criteria().orOperator(
                Criteria.where("trendScore").ne(0),
                Criteria.where("relevanceScore").gt(0.0),
                Criteria.where("downloads7d").gt(0),
                Criteria.where("downloads30d").gt(0)
        ));
        decayQuery.fields().include("_id");

        List<Project> decaying = mongoTemplate.find(decayQuery, Project.class);
        for (Project p : decaying) {
            if (!activeProjectIds.contains(p.getId())) {
                bulkOps.updateOne(new Query(Criteria.where("_id").is(p.getId())), new Update().set("trendScore", 0).set("relevanceScore", 0.0).set("downloads7d", 0).set("downloads30d", 0).set("downloads90d", 0));
                if (++counter >= 1000) { bulkOps.execute(); bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Project.class); counter = 0; }
            }
        }
        if (counter > 0) bulkOps.execute();
    }

    private double calculatePercentileDownloads(long totalCount, double percentile) {
        if (totalCount == 0) return 10.0;
        Project p = mongoTemplate.findOne(new Query(Criteria.where("status").is(ProjectStatus.PUBLISHED).and("downloadCount").gte(10)).with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "downloadCount")).skip((long) (totalCount * percentile)).limit(1), Project.class);
        return p != null ? p.getDownloadCount() : 10.0;
    }

    public void ensureScores(List<Project> projects) {
        if (projects == null || projects.isEmpty()) return;
        List<Project> missing = projects.stream().filter(p -> p.getDownloadCount() > 0 && (p.getPopularScore() == 0.0 || p.getRelevanceScore() == 0.0 || p.getTrendScore() == 0)).toList();
        if (!missing.isEmpty()) updateProjectScores();
    }
}
