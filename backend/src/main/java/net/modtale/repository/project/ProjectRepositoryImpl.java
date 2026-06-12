package net.modtale.repository.project;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectSort;
import net.modtale.model.project.ProjectViewCategory;
import net.modtale.repository.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final MongoTemplate mongoTemplate;
    private final UserRepository userRepository;

    public ProjectRepositoryImpl(MongoTemplate mongoTemplate, UserRepository userRepository) {
        this.mongoTemplate = mongoTemplate;
        this.userRepository = userRepository;
    }

    @Override
    public Page<Project> searchProjects(
            String search, List<String> tags, String gameVersion, ProjectClassification classification,
            Integer minDownloads, Integer minFavorites, Pageable pageable,
            String currentUserId, ProjectSort sortBy,
            ProjectViewCategory viewCategory, LocalDate dateCutoff, String authorId
    ) {
        List<Criteria> criteriaList = new ArrayList<>();

        if (viewCategory == ProjectViewCategory.YOUR_PROJECTS && currentUserId != null) {
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

        if (classification != null) {
            criteriaList.add(Criteria.where("classification").is(classification));
        }

        if (gameVersion != null && !gameVersion.isEmpty())
            criteriaList.add(Criteria.where("versions.gameVersions").is(gameVersion));
        if (minDownloads != null) criteriaList.add(Criteria.where("downloadCount").gte(minDownloads));
        if (minFavorites != null) criteriaList.add(Criteria.where("favoriteCount").gte(minFavorites));

        if (sortBy == ProjectSort.TRENDING || viewCategory == ProjectViewCategory.TRENDING) {
            criteriaList.add(Criteria.where("downloadCount").gte(10));
        }

        boolean isTimeBasedDownloadSort = sortBy == ProjectSort.DOWNLOADS && dateCutoff != null;
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

        if (viewCategory == ProjectViewCategory.HIDDEN_GEMS) {
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

        if (sortBy == ProjectSort.TRENDING || viewCategory == ProjectViewCategory.TRENDING) {
            pipeline.add(Aggregation.sort(Sort.Direction.DESC, "trendScore"));
        } else if (sortBy == ProjectSort.POPULAR || viewCategory == ProjectViewCategory.POPULAR) {
            pipeline.add(Aggregation.addFields()
                    .addField("effectivePopularScore")
                    .withValue(ConditionalOperators.when(new Criteria().andOperator(
                                    Criteria.where("popularScore").lte(0.0),
                                    Criteria.where("downloadCount").gte(10)
                            ))
                            .thenValueOf(
                                    ArithmeticOperators.Add.valueOf("downloadCount")
                                            .add(ArithmeticOperators.Multiply.valueOf("favoriteCount").multiplyBy(10))
                            )
                            .otherwise("$popularScore"))
                    .build());
            pipeline.add(Aggregation.sort(Sort.by(
                    Sort.Order.desc("effectivePopularScore"),
                    Sort.Order.desc("downloadCount"),
                    Sort.Order.desc("favoriteCount")
            )));
        } else if (sortBy == ProjectSort.RELEVANCE) {
            pipeline.add(Aggregation.addFields()
                    .addField("effectiveRelevanceScore")
                    .withValue(ConditionalOperators.when(new Criteria().andOperator(
                                    Criteria.where("relevanceScore").lte(0.0),
                                    Criteria.where("downloadCount").gte(10),
                                    Criteria.where("downloads30d").gt(0)
                            ))
                            .thenValueOf(
                                    ArithmeticOperators.Multiply.valueOf("downloads30d")
                                            .multiplyBy(
                                                    ArithmeticOperators.Add.valueOf(1)
                                                            .add(
                                                                    ArithmeticOperators.Multiply.valueOf(
                                                                                    ArithmeticOperators.Divide.valueOf("favoriteCount")
                                                                                            .divideBy(
                                                                                                    ConditionalOperators.when(Criteria.where("downloadCount").gt(0))
                                                                                                            .then("$downloadCount")
                                                                                                            .otherwise(1)
                                                                                            )
                                                                            )
                                                                            .multiplyBy(5)
                                                            )
                                            )
                            )
                            .otherwise("$relevanceScore"))
                    .build());
            pipeline.add(Aggregation.sort(Sort.by(
                    Sort.Order.desc("effectiveRelevanceScore"),
                    Sort.Order.desc("downloads30d"),
                    Sort.Order.desc("favoriteCount")
            )));
        } else if (viewCategory == ProjectViewCategory.HIDDEN_GEMS) {
            pipeline.add(Aggregation.sort(Sort.Direction.DESC, "gemRatio"));
        } else if (sortBy == ProjectSort.DOWNLOADS && dateCutoff != null) {
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

        long total;
        if (viewCategory == ProjectViewCategory.HIDDEN_GEMS) {
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
