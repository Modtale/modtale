package net.modtale.repository.resources;

import net.modtale.model.resources.Mod;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.AnalyticsService;
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
public class ModRepositoryImpl implements ModRepositoryCustom {

    private static final Logger logger = LoggerFactory.getLogger(ModRepositoryImpl.class);

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private UserRepository userRepository;

    @Lazy
    @Autowired
    private AnalyticsService analyticsService;

    @Override
    public Page<Mod> searchMods(
            String search, List<String> tags, String gameVersion, String classification,
            Double minRating, Integer minDownloads, Integer minFavorites, Pageable pageable,
            String currentUsername, String sortBy,
            String viewCategory, LocalDate dateCutoff, String authorId
    ) {
        List<Criteria> criteriaList = new ArrayList<>();

        if ("Your Projects".equals(viewCategory) && currentUsername != null) {
            criteriaList.add(new Criteria().orOperator(
                    authorId != null ? Criteria.where("authorId").is(authorId) : new Criteria(),
                    Criteria.where("author").is(currentUsername),
                    Criteria.where("contributors").is(currentUsername)
            ));
        } else {
            criteriaList.add(Criteria.where("status").in("PUBLISHED", "ARCHIVED"));
        }

        if (authorId != null && !authorId.trim().isEmpty()) {
            criteriaList.add(new Criteria().orOperator(
                    Criteria.where("authorId").is(authorId),
                    Criteria.where("author").is(authorId)
            ));
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
        if (classification != null && !classification.isEmpty() && !"All".equals(classification))
            criteriaList.add(Criteria.where("classification").is(classification));
        if (gameVersion != null && !gameVersion.isEmpty())
            criteriaList.add(Criteria.where("versions.gameVersions").is(gameVersion));
        if (minRating != null) criteriaList.add(Criteria.where("rating").gte(minRating));
        if (minDownloads != null) criteriaList.add(Criteria.where("downloadCount").gte(minDownloads));
        if (minFavorites != null) criteriaList.add(Criteria.where("favoriteCount").gte(minFavorites));

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
            long totalDocs = mongoTemplate.count(new Query(baseCriteria), Mod.class);
            int p5Index = Math.max(0, (int) (totalDocs * 0.05));
            int p20Index = Math.max(0, (int) (totalDocs * 0.20));

            Query p5Query = new Query(baseCriteria).with(Sort.by(Sort.Direction.ASC, "downloadCount")).skip(p5Index).limit(1);
            Mod p5Mod = mongoTemplate.findOne(p5Query, Mod.class);
            int minDl = p5Mod != null ? p5Mod.getDownloadCount() : 0;

            Query p20Query = new Query(baseCriteria).with(Sort.by(Sort.Direction.ASC, "downloadCount")).skip(p20Index).limit(1);
            Mod p20Mod = mongoTemplate.findOne(p20Query, Mod.class);
            int maxDl = p20Mod != null ? p20Mod.getDownloadCount() : Integer.MAX_VALUE;

            if (maxDl <= minDl) maxDl = minDl + 500;

            pipeline.add(Aggregation.match(new Criteria().andOperator(
                    Criteria.where("downloadCount").gt(minDl),
                    Criteria.where("downloadCount").lt(maxDl)
            )));

            pipeline.add(Aggregation.addFields()
                    .addField("gemRatio")
                    .withValue(ArithmeticOperators.Divide.valueOf("favoriteCount")
                            .divideBy(ConditionalOperators.when(Criteria.where("downloadCount").gt(0))
                                    .then("$downloadCount").otherwise(1)))
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

        pipeline.add(Aggregation.addFields()
                .addField("versions")
                .withValue(ArrayOperators.Slice.sliceArrayOf("versions").itemCount(1))
                .build());

        pipeline.add(Aggregation.project()
                .andExclude(
                        "about",
                        "comments",
                        "galleryImages",
                        "contributors",
                        "pendingInvites",
                        "modIds",
                        "childProjectIds"
                )
        );

        Aggregation mainAgg = Aggregation.newAggregation(Mod.class, pipeline);
        List<Mod> results = mongoTemplate.aggregate(mainAgg, Mod.class, Mod.class).getMappedResults();

        analyticsService.ensureScores(results);

        long total;
        if ("hidden_gems".equals(viewCategory)) {
            List<AggregationOperation> countPipeline = new ArrayList<>(pipeline);
            countPipeline.removeIf(op -> op instanceof SortOperation || op instanceof SkipOperation || op instanceof LimitOperation || op instanceof ProjectionOperation || op instanceof AddFieldsOperation || op instanceof LookupOperation);
            countPipeline.add(Aggregation.count().as("total"));

            Aggregation countAgg = Aggregation.newAggregation(Mod.class, countPipeline);
            AggregationResults<HashMap> countRes = mongoTemplate.aggregate(countAgg, Mod.class, HashMap.class);
            total = countRes.getUniqueMappedResult() != null ? ((Number) countRes.getUniqueMappedResult().get("total")).longValue() : 0;
        } else {
            total = mongoTemplate.count(new Query(baseCriteria), Mod.class);
        }

        return new PageImpl<>(results, pageable, total);
    }

    @Override
    public Page<Mod> findFavorites(List<String> modIds, String search, Pageable pageable) {
        Query query = new Query();
        query.addCriteria(Criteria.where("id").in(modIds));
        query.addCriteria(Criteria.where("status").in("PUBLISHED", "ARCHIVED"));

        if (search != null && !search.trim().isEmpty()) {
            String regex = Pattern.quote(search);
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("title").regex(regex, "i"),
                    Criteria.where("description").regex(regex, "i")
            ));
        }

        query.fields()
                .slice("versions", 1)
                .exclude(
                        "about",
                        "comments",
                        "galleryImages",
                        "contributors",
                        "pendingInvites",
                        "modIds",
                        "childProjectIds"
                );

        query.with(pageable);
        List<Mod> list = mongoTemplate.find(query, Mod.class);

        for (Mod m : list) {
            if (m.getAuthorId() != null && m.getAuthor() == null) {
                userRepository.findById(m.getAuthorId()).ifPresent(u -> m.setAuthor(u.getUsername()));
            }
        }

        long count = mongoTemplate.count(Query.of(query).limit(0).skip(0), Mod.class);

        return PageableExecutionUtils.getPage(list, pageable, () -> count);
    }
}