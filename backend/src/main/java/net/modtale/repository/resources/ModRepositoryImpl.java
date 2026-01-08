package net.modtale.repository.resources;

import net.modtale.model.resources.Mod;
import net.modtale.repository.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.time.LocalDateTime;
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

    @Override
    public Page<Mod> searchMods(
            String search, List<String> tags, String gameVersion, String classification,
            Double minRating, Integer minDownloads, Pageable pageable, boolean demoMode,
            String currentUsername, List<String> seededAuthors, String sortBy,
            String viewCategory, LocalDate dateCutoff, String author
    ) {
        List<Criteria> criteriaList = new ArrayList<>();

        if ("Your Projects".equals(viewCategory) && currentUsername != null) {
            criteriaList.add(new Criteria().orOperator(
                    Criteria.where("author").is(currentUsername),
                    Criteria.where("contributors").is(currentUsername)
            ));
        } else {
            criteriaList.add(Criteria.where("status").in("PUBLISHED", "ARCHIVED"));
        }

        if (author != null && !author.trim().isEmpty()) {
            criteriaList.add(Criteria.where("author").regex("^" + Pattern.quote(author) + "$", "i"));
        }

        if (search != null && !search.trim().isEmpty()) {
            String regex = Pattern.quote(search);
            criteriaList.add(new Criteria().orOperator(
                    Criteria.where("title").regex(regex, "i"),
                    Criteria.where("description").regex(regex, "i"),
                    Criteria.where("author").regex(regex, "i")
            ));
        }

        if (tags != null && !tags.isEmpty()) {
            criteriaList.add(Criteria.where("tags").all(tags));
        }

        if (classification != null && !classification.isEmpty() && !"All".equals(classification)) {
            criteriaList.add(Criteria.where("classification").is(classification));
        }

        if (gameVersion != null && !gameVersion.isEmpty()) {
            criteriaList.add(Criteria.where("versions.gameVersions").is(gameVersion));
        }

        if (minRating != null) {
            criteriaList.add(Criteria.where("rating").gte(minRating));
        }

        if (minDownloads != null) {
            criteriaList.add(Criteria.where("downloadCount").gte(minDownloads));
        }

        if (dateCutoff != null) {
            criteriaList.add(Criteria.where("updatedAt").gte(dateCutoff.toString()));
        }

        if (demoMode && seededAuthors != null && !seededAuthors.isEmpty()) {
            if (currentUsername == null) {
                criteriaList.add(Criteria.where("author").in(seededAuthors));
            } else {
                criteriaList.add(new Criteria().orOperator(
                        Criteria.where("author").in(seededAuthors),
                        Criteria.where("author").is(currentUsername)
                ));
            }
        }

        Criteria baseCriteria;
        if (criteriaList.isEmpty()) {
            baseCriteria = new Criteria();
        } else if (criteriaList.size() == 1) {
            baseCriteria = criteriaList.get(0);
        } else {
            baseCriteria = new Criteria().andOperator(criteriaList.toArray(new Criteria[0]));
        }

        List<AggregationOperation> pipeline = new ArrayList<>();
        pipeline.add(Aggregation.match(baseCriteria));

        if ("hidden_gems".equals(viewCategory)) {
            long totalDocs = mongoTemplate.count(new Query(baseCriteria), Mod.class);
            int p5Index = Math.max(0, (int) (totalDocs * 0.05));
            int p90Index = Math.max(0, (int) (totalDocs * 0.90));

            Query p5Query = new Query(baseCriteria).with(Sort.by(Sort.Direction.ASC, "downloadCount")).skip(p5Index).limit(1);
            Mod p5Mod = mongoTemplate.findOne(p5Query, Mod.class);
            int minDl = p5Mod != null ? p5Mod.getDownloadCount() : 0;

            Query p90Query = new Query(baseCriteria).with(Sort.by(Sort.Direction.ASC, "downloadCount")).skip(p90Index).limit(1);
            Mod p90Mod = mongoTemplate.findOne(p90Query, Mod.class);
            int maxDl = p90Mod != null ? p90Mod.getDownloadCount() : Integer.MAX_VALUE;

            if (maxDl <= minDl) maxDl = minDl + 500;

            pipeline.add(Aggregation.match(
                    new Criteria().andOperator(
                            Criteria.where("downloadCount").gt(minDl),
                            Criteria.where("downloadCount").lt(maxDl),
                            Criteria.where("rating").gte(4.5),
                            Criteria.where("reviews.2").exists(true) // At least 3 reviews
                    )
            ));
        }

        if ("popular".equals(sortBy) || "popular".equals(viewCategory)) {
            pipeline.add(Aggregation.addFields().addField("popularScore")
                    .withValue(
                            ArithmeticOperators.Multiply.valueOf("downloadCount")
                                    .multiplyBy(
                                            ConditionalOperators.when(Criteria.where("rating").gte(4.5))
                                                    .then(1.0)
                                                    .otherwise(
                                                            ConditionalOperators.when(Criteria.where("rating").gt(0))
                                                                    .then(ArithmeticOperators.Divide.valueOf("rating").divideBy(4.5))
                                                                    .otherwise(0.5)
                                                    )
                                    )
                    ).build());
            pipeline.add(Aggregation.sort(Sort.Direction.DESC, "popularScore"));

        } else if ("trending".equals(sortBy) || "trending".equals(viewCategory)) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime sevenDaysAgo = now.minusDays(7);
            LocalDateTime fourteenDaysAgo = now.minusDays(14);

            LookupOperation lookup = LookupOperation.newLookup()
                    .from("download_logs")
                    .localField("_id")
                    .foreignField("projectId")
                    .pipeline(
                            Aggregation.match(Criteria.where("timestamp").gte(fourteenDaysAgo)),
                            Aggregation.project("timestamp")
                    )
                    .as("recent_logs");

            pipeline.add(lookup);

            pipeline.add(Aggregation.addFields()
                    .addField("currentWeek").withValue(
                            ConditionalOperators.ifNull(
                                    ArrayOperators.Size.lengthOfArray(
                                            ArrayOperators.Filter.filter("recent_logs").as("log")
                                                    .by(ComparisonOperators.Gte.valueOf("log.timestamp").greaterThanEqualToValue(sevenDaysAgo))
                                    )
                            ).then(0)
                    )
                    .addField("prevWeek").withValue(
                            ConditionalOperators.ifNull(
                                    ArrayOperators.Size.lengthOfArray(
                                            ArrayOperators.Filter.filter("recent_logs").as("log")
                                                    .by(ComparisonOperators.Lt.valueOf("log.timestamp").lessThanValue(sevenDaysAgo))
                                    )
                            ).then(0)
                    )
                    .build());

            pipeline.add(Aggregation.addFields()
                    .addField("trendScore").withValue(
                            ArithmeticOperators.Subtract.valueOf("currentWeek").subtract("prevWeek")
                    ).build());

            pipeline.add(Aggregation.sort(Sort.Direction.DESC, "trendScore"));

        } else if ("relevance".equals(sortBy)) {
            LookupOperation lookup = LookupOperation.newLookup()
                    .from("download_logs")
                    .localField("_id")
                    .foreignField("projectId")
                    .pipeline(
                            Aggregation.match(Criteria.where("timestamp").gte(LocalDateTime.now().minusDays(30))),
                            Aggregation.project("_id")
                    )
                    .as("recent_downloads_30d");
            pipeline.add(lookup);

            pipeline.add(Aggregation.addFields()
                    .addField("recentCount").withValue(ArrayOperators.Size.lengthOfArray("recent_downloads_30d"))
                    .build());

            pipeline.add(Aggregation.addFields().addField("relevanceScore")
                    .withValue(
                            ArithmeticOperators.Multiply.valueOf("recentCount")
                                    .multiplyBy(
                                            ConditionalOperators.when(Criteria.where("rating").gte(4.5))
                                                    .then(1.0)
                                                    .otherwise(
                                                            ConditionalOperators.when(Criteria.where("rating").gt(0))
                                                                    .then(ArithmeticOperators.Divide.valueOf("rating").divideBy(4.5))
                                                                    .otherwise(0.5)
                                                    )
                                    )
                    ).build());
            pipeline.add(Aggregation.sort(Sort.Direction.DESC, "relevanceScore"));

        } else if (pageable.getSort().isSorted()) {
            pipeline.add(Aggregation.sort(pageable.getSort()));
        } else {
            if ("hidden_gems".equals(viewCategory)) {
                pipeline.add(Aggregation.sort(Sort.Direction.DESC, "rating"));
            } else {
                pipeline.add(Aggregation.sort(Sort.Direction.DESC, "updatedAt"));
            }
        }

        pipeline.add(Aggregation.skip((long) pageable.getPageNumber() * pageable.getPageSize()));
        pipeline.add(Aggregation.limit(pageable.getPageSize()));

        Aggregation mainAgg = Aggregation.newAggregation(Mod.class, pipeline);
        List<Mod> results = mongoTemplate.aggregate(mainAgg, Mod.class, Mod.class).getMappedResults();

        long total;
        if ("hidden_gems".equals(viewCategory)) {
            List<AggregationOperation> countPipeline = new ArrayList<>(pipeline);
            countPipeline.removeIf(op -> op instanceof SortOperation || op instanceof SkipOperation || op instanceof LimitOperation);
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

        query.with(pageable);
        List<Mod> list = mongoTemplate.find(query, Mod.class);
        long count = mongoTemplate.count(Query.of(query).limit(0).skip(0), Mod.class);

        return PageableExecutionUtils.getPage(list, pageable, () -> count);
    }
}