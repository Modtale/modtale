package net.modtale.repository.project;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectStatus;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.analytics.ScoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

@Repository
public class ProjectRepositoryImpl implements ProjectRepositoryCustom {

    private static final Logger logger = LoggerFactory.getLogger(ProjectRepositoryImpl.class);

    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private UserRepository userRepository;

    @Lazy
    @Autowired private ScoringService scoringService;

    @Override
    public Page<Project> searchProjects(
            String search, List<String> tags, String gameVersion, String classification,
            Double minRating, Integer minDownloads, Integer minFavorites, Pageable pageable,
            String currentUserId, String sortBy,
            String viewCategory, LocalDate dateCutoff, String authorId
    ) {
        List<Criteria> criteriaList = new ArrayList<>();

        if ("Your Projects".equals(viewCategory) && currentUserId != null) {
            criteriaList.add(new Criteria().orOperator(
                    Criteria.where("authorId").is(currentUserId),
                    Criteria.where("teamMembers.userId").is(currentUserId)
            ));
        } else {
            criteriaList.add(Criteria.where("status").in(ProjectStatus.PUBLISHED, ProjectStatus.ARCHIVED));
        }

        if (authorId != null && !authorId.trim().isEmpty()) {
            criteriaList.add(Criteria.where("authorId").is(authorId));
        }

        if (search != null && !search.trim().isEmpty()) {
            String regex = Pattern.quote(search);
            criteriaList.add(new Criteria().orOperator(
                    Criteria.where("title").regex(regex, "i"),
                    Criteria.where("description").regex(regex, "i"),
                    Criteria.where("author").regex(regex, "i")
            ));
        }

        if (tags != null && !tags.isEmpty()) criteriaList.add(Criteria.where("tags").in(tags));

        if (classification != null && !classification.isEmpty() && !"All".equals(classification)) {
            try {
                ProjectClassification classEnum = ProjectClassification.valueOf(classification.toUpperCase());
                criteriaList.add(Criteria.where("classification").is(classEnum));
            } catch (IllegalArgumentException ignored) {}
        }

        if (gameVersion != null && !gameVersion.isEmpty())
            criteriaList.add(Criteria.where("versions.gameVersions").is(gameVersion));
        if (minRating != null) criteriaList.add(Criteria.where("rating").gte(minRating));
        if (minDownloads != null) criteriaList.add(Criteria.where("downloadCount").gte(minDownloads));
        if (minFavorites != null) criteriaList.add(Criteria.where("favoriteCount").gte(minFavorites));

        if ("trending".equals(sortBy) || "trending".equals(viewCategory)) {
            criteriaList.add(Criteria.where("downloadCount").gte(10));
        }

        boolean isTimeBasedDownloadSort = "downloads".equals(sortBy) && dateCutoff != null;
        if (dateCutoff != null && !isTimeBasedDownloadSort) {
            criteriaList.add(Criteria.where("updatedAt").gte(dateCutoff.toString()));
        }

        Criteria baseCriteria = criteriaList.isEmpty() ? new Criteria() :
                (criteriaList.size() == 1 ? criteriaList.get(0) : new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));

        List<AggregationOperation> pipeline = new ArrayList<>();
        pipeline.add(Aggregation.match(baseCriteria));

        pipeline.add(LookupOperation.newLookup()
                .from("users")
                .localField("authorId")
                .foreignField("_id")
                .as("authorInfo"));

        pipeline.add(Aggregation.addFields()
                .addField("author")
                .withValue(ConditionalOperators.ifNull(
                        ArrayOperators.ArrayElemAt.arrayOf("authorInfo.username").elementAt(0)
                ).then("$author"))
                .build());

        pipeline.add(Aggregation.project().andExclude("authorInfo"));

        if ("hidden_gems".equals(viewCategory)) {
            Query statsQuery = new Query(baseCriteria);
            statsQuery.addCriteria(Criteria.where("downloads30d").gte(10));

            long totalDocs = mongoTemplate.count(statsQuery, Project.class);
            int p5Index = Math.max(0, (int) (totalDocs * 0.05));
            int p20Index = Math.max(0, (int) (totalDocs * 0.20));

            Query p5Query = new Query(baseCriteria)
                    .addCriteria(Criteria.where("downloads30d").gte(10))
                    .with(Sort.by(Sort.Direction.ASC, "downloads30d"))
                    .skip(p5Index).limit(1);
            Project p5Project = mongoTemplate.findOne(p5Query, Project.class);
            int minDl = p5Project != null ? p5Project.getDownloads30d() : 0;

            Query p20Query = new Query(baseCriteria)
                    .addCriteria(Criteria.where("downloads30d").gte(10))
                    .with(Sort.by(Sort.Direction.ASC, "downloads30d"))
                    .skip(p20Index).limit(1);
            Project p20Project = mongoTemplate.findOne(p20Query, Project.class);
            int maxDl = p20Project != null ? p20Project.getDownloads30d() : Integer.MAX_VALUE;

            if (maxDl <= minDl) maxDl = minDl + 500;

            pipeline.add(Aggregation.match(new Criteria().andOperator(
                    Criteria.where("downloads30d").gt(minDl),
                    Criteria.where("downloads30d").lt(maxDl)
            )));

            pipeline.add(Aggregation.addFields()
                    .addField("gemRatio")
                    .withValue(ArithmeticOperators.Divide.valueOf("favoriteCount")
                            .divideBy(ConditionalOperators.when(Criteria.where("downloads30d").gt(0))
                                    .then("$downloads30d").otherwise(1)))
                    .build());
        }

        if ("trending".equals(sortBy) || "trending".equals(viewCategory)) {
            pipeline.add(Aggregation.sort(Sort.Direction.DESC, "trendScore"));
        } else if ("popular".equals(sortBy) || "popular".equals(viewCategory)) {
            pipeline.add(Aggregation.sort(Sort.Direction.DESC, "popularScore"));
        } else if ("relevance".equals(sortBy)) {
            pipeline.add(Aggregation.sort(Sort.Direction.DESC, "relevanceScore"));
        } else if ("hidden_gems".equals(viewCategory)) {
            pipeline.add(Aggregation.sort(Sort.Direction.DESC, "gemRatio"));
        } else if ("downloads".equals(sortBy) && dateCutoff != null) {
            long daysDiff = ChronoUnit.DAYS.between(dateCutoff, LocalDate.now());
            if (daysDiff <= 7) {
                pipeline.add(Aggregation.sort(Sort.Direction.DESC, "downloads7d"));
            } else if (daysDiff <= 31) {
                pipeline.add(Aggregation.sort(Sort.Direction.DESC, "downloads30d"));
            } else if (daysDiff <= 92) {
                pipeline.add(Aggregation.sort(Sort.Direction.DESC, "downloads90d"));
            } else {
                pipeline.add(Aggregation.sort(Sort.Direction.DESC, "downloadCount"));
            }
        } else if (pageable.getSort().isSorted()) {
            pipeline.add(Aggregation.sort(pageable.getSort()));
        } else {
            pipeline.add(Aggregation.sort(Sort.Direction.DESC, "updatedAt"));
        }

        pipeline.add(Aggregation.skip((long) pageable.getPageNumber() * pageable.getPageSize()));
        pipeline.add(Aggregation.limit(pageable.getPageSize()));

        @SuppressWarnings("deprecation")
        Aggregation mainAgg = Aggregation.newAggregation(Project.class, pipeline);
        List<Project> results = mongoTemplate.aggregate(mainAgg, Project.class, Project.class).getMappedResults();

        scoringService.ensureScores(results);

        long total;
        if ("hidden_gems".equals(viewCategory)) {
            List<AggregationOperation> countPipeline = new ArrayList<>(pipeline);
            countPipeline.removeIf(op -> op instanceof SortOperation || op instanceof SkipOperation || op instanceof LimitOperation || op instanceof ProjectionOperation || op instanceof AddFieldsOperation || op instanceof LookupOperation);
            countPipeline.add(Aggregation.count().as("total"));

            @SuppressWarnings("deprecation")
            Aggregation countAgg = Aggregation.newAggregation(Project.class, countPipeline);
            @SuppressWarnings("rawtypes")
            AggregationResults<HashMap> countRes = mongoTemplate.aggregate(countAgg, Project.class, HashMap.class);
            total = countRes.getUniqueMappedResult() != null ? ((Number) countRes.getUniqueMappedResult().get("total")).longValue() : 0;
        } else {
            total = mongoTemplate.count(new Query(baseCriteria), Project.class);
        }

        return new PageImpl<>(results, pageable, total);
    }

    @Override
    public Page<Project> findFavorites(List<String> projectIds, String search, Pageable pageable) {
        Query query = new Query();
        query.addCriteria(Criteria.where("id").in(projectIds));
        query.addCriteria(Criteria.where("status").in(ProjectStatus.PUBLISHED, ProjectStatus.ARCHIVED));

        if (search != null && !search.trim().isEmpty()) {
            String regex = Pattern.quote(search);
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("title").regex(regex, "i"),
                    Criteria.where("description").regex(regex, "i")
            ));
        }

        query.with(pageable);
        List<Project> list = mongoTemplate.find(query, Project.class);

        for (Project p : list) {
            if (p.getAuthorId() != null && p.getAuthor() == null) {
                userRepository.findById(p.getAuthorId()).ifPresent(u -> p.setAuthor(u.getUsername()));
            }
        }

        long count = mongoTemplate.count(Query.of(query).limit(0).skip(0), Project.class);
        return PageableExecutionUtils.getPage(list, pageable, () -> count);
    }

    @Override
    public Page<Project> searchDeletedProjects(String search, Pageable pageable) {
        Query query = new Query();
        query.addCriteria(Criteria.where("status").is(ProjectStatus.DELETED));
        query.addCriteria(Criteria.where("deletedAt").ne(null));

        if (search != null && !search.trim().isEmpty()) {
            String regex = Pattern.quote(search);
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("title").regex(regex, "i"),
                    Criteria.where("author").regex(regex, "i"),
                    Criteria.where("_id").is(search)
            ));
        }

        long total = mongoTemplate.count(query, Project.class);
        query.with(pageable);
        List<Project> results = mongoTemplate.find(query, Project.class);

        for (Project p : results) {
            if (p.getAuthorId() != null && p.getAuthor() == null) {
                userRepository.findById(p.getAuthorId()).ifPresent(u -> p.setAuthor(u.getUsername()));
            }
        }

        return new PageImpl<>(results, pageable, total);
    }
}
