package net.modtale.service.finance;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import net.modtale.model.finance.AdSettlementStage.Activity;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/** Existing analytics are provisional, debounced inputs, not verified ad eligibility or bot-free traffic. */
@Service
public class AdSettlementActivityService {
    public static final String RULE = "provisional-pageviews-plus-launcher-downloads-v1";
    private final MongoTemplate mongo;
    private final ProjectRepository projects;
    private final UserRepository users;
    public AdSettlementActivityService(MongoTemplate mongo, ProjectRepository projects, UserRepository users) {
        this.mongo = mongo; this.projects = projects; this.users = users;
    }

    public List<Activity> snapshot(LocalDate from, LocalDate through) {
        List<Criteria> months = new ArrayList<>();
        for (YearMonth month = YearMonth.from(from); !month.isAfter(YearMonth.from(through)); month = month.plusMonths(1)) {
            months.add(Criteria.where("year").is(month.getYear()).and("month").is(month.getMonthValue()));
        }
        Query query = Query.query(new Criteria().orOperator(months)).limit(13001);
        query.fields().include("projectId").include("authorId").include("year").include("month").include("days");
        List<Document> sources = mongo.find(query, Document.class, "project_monthly_stats");
        if (sources.size() > 13000) throw new IllegalArgumentException("The activity window exceeds the bounded review size.");
        Map<String, long[]> totals = new TreeMap<>();
        Map<String, Set<String>> sourceOwners = new HashMap<>();
        for (Document source : sources) {
            String projectId = source.getString("projectId");
            if (projectId == null || !(source.get("days") instanceof Map<?, ?> days)) continue;
            YearMonth month = YearMonth.of(((Number) source.get("year")).intValue(), ((Number) source.get("month")).intValue());
            for (int day = 1; day <= month.lengthOfMonth(); day++) {
                LocalDate date = month.atDay(day);
                if (date.isBefore(from) || date.isAfter(through) || !(days.get(String.valueOf(day)) instanceof Map<?, ?> counts)) continue;
                long[] row = totals.computeIfAbsent(projectId, key -> new long[4]);
                String[] fields = {"v", "l", "f", "a"};
                for (int i = 0; i < fields.length; i++) row[i] = Math.addExact(row[i], count(counts.get(fields[i])));
            }
            sourceOwners.computeIfAbsent(projectId, key -> new java.util.HashSet<>()).add(source.getString("authorId"));
        }
        if (totals.size() > 1000) throw new IllegalArgumentException("More than 1000 projects requires a separately reviewed reporting batch.");
        Map<String, Project> byId = new HashMap<>();
        projects.findAllById(totals.keySet()).forEach(project -> byId.put(project.getId(), project));
        Set<String> ownerIds = byId.values().stream().map(Project::getAuthorId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Set<String> activeOwners = new java.util.HashSet<>();
        users.findAllById(ownerIds).forEach(user -> { if (!user.isDeleted()) activeOwners.add(user.getId()); });
        List<Activity> result = new ArrayList<>();
        totals.forEach((projectId, counts) -> {
            Project project = byId.get(projectId);
            String note = "Provisional: fraud filtering and historical opt-in still require validation.";
            boolean eligible = true;
            if (project == null || project.getDeletedAt() != null || project.getStatus() == null || !Set.of(ProjectStatus.PUBLISHED, ProjectStatus.UNLISTED).contains(project.getStatus())) {
                eligible = false; note = "Excluded: project is unavailable or not published.";
            } else if (!project.isAdsEnabled()) { eligible = false; note = "Excluded: creator has not opted into ads."; }
            else if (!activeOwners.contains(project.getAuthorId())) { eligible = false; note = "Excluded: creator account is unavailable."; }
            else if (sourceOwners.get(projectId).size() != 1 || !sourceOwners.get(projectId).contains(project.getAuthorId())) {
                eligible = false; note = "Excluded: historical owner identity needs review.";
            }
            result.add(new Activity(projectId, project == null ? null : project.getAuthorId(), project == null ? "Unavailable project" : project.getTitle(),
                    counts[0], counts[1], counts[2], counts[3], eligible ? Math.addExact(counts[0], counts[1]) : 0, 0, note));
        });
        return List.copyOf(result);
    }
    private static long count(Object value) {
        if (value == null) return 0;
        if (!(value instanceof Number)) throw new IllegalArgumentException("Analytics contains an invalid count.");
        long count;
        try { count = new java.math.BigDecimal(value.toString()).longValueExact(); }
        catch (ArithmeticException invalid) { throw new IllegalArgumentException("Analytics contains an invalid count."); }
        if (count < 0) throw new IllegalArgumentException("Analytics contains a negative count.");
        return count;
    }
}
