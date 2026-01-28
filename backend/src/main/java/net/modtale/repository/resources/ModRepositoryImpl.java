package net.modtale.repository.resources;

import net.modtale.model.resources.Mod;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.AnalyticsService;
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
            Double minRating, Integer minDownloads, Pageable pageable,
            String currentUsername, String sortBy,
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

        if (tags != null && !tags.isEmpty()) criteriaList.add(Criteria.where("tags").all(tags));
        if (classification != null && !classification.isEmpty() && !"All".equals(classification)) criteriaList.add(Criteria.where("classification").is(classification));
        if (gameVersion != null && !gameVersion.isEmpty()) criteriaList.add(Criteria.where("versions.gameVersions").is(gameVersion));
        if (minRating != null) criteriaList.add(Criteria.where("rating").gte(minRating));
        if (minDownloads != null) criteriaList.add(Criteria.where("downloadCount").gte(minDownloads));
        if (dateCutoff != null) criteriaList.add(Criteria.where("updatedAt").gte(dateCutoff.toString()));

        Criteria baseCriteria = criteriaList.isEmpty() ? new Criteria() :
                (criteriaList.size() == 1 ? criteriaList.get(0) : new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));

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

            pipeline.add(Aggregation.match(new Criteria().andOperator(
                    Criteria.where("downloadCount").gt(minDl),
                    Criteria.where("downloadCount").lt(maxDl),
                    Criteria.where("rating").gte(4.5),
                    Criteria.where("reviews.2").exists(true)
            )));
        }

        if ("trending".equals(sortBy) || "trending".equals(viewCategory)) {
            pipeline.add(Aggregation.sort(Sort.Direction.DESC, "trendScore"));
        } else if ("popular".equals(sortBy) || "popular".equals(viewCategory)) {
            pipeline.addAll(AnalyticsService.getPopularAggregationStages());
        } else if ("relevance".equals(sortBy)) {
            pipeline.addAll(AnalyticsService.getRelevanceAggregationStages());
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

        pipeline.add(Aggregation.project()
                .andExclude(
                        "about",
                        "reviews",
                        "galleryImages",
                        "contributors",
                        "pendingInvites",
                        "modIds",
                        "childProjectIds",
                        "versions.scanResult",
                        "versions.rejectionReason",
                        "versions.changelog",
                        "versions.dependencies",
                        "versions.fileUrl",
                        "monthly_stats",
                        "daysArray",
                        "logDate",
                        "popularScore",
                        "relevanceScore",
                        "recentDownloads"
                )
        );

        Aggregation mainAgg = Aggregation.newAggregation(Mod.class, pipeline);
        List<Mod> results = mongoTemplate.aggregate(mainAgg, Mod.class, Mod.class).getMappedResults();

        long total;
        if ("hidden_gems".equals(viewCategory)) {
            List<AggregationOperation> countPipeline = new ArrayList<>(pipeline);
            countPipeline.removeIf(op -> op instanceof SortOperation || op instanceof SkipOperation || op instanceof LimitOperation || op instanceof ProjectionOperation);
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

        query.fields().exclude(
                "about",
                "reviews",
                "galleryImages",
                "contributors",
                "pendingInvites",
                "modIds",
                "childProjectIds",
                "versions.scanResult",
                "versions.rejectionReason",
                "versions.changelog",
                "versions.dependencies",
                "versions.fileUrl"
        );

        query.with(pageable);
        List<Mod> list = mongoTemplate.find(query, Mod.class);
        long count = mongoTemplate.count(Query.of(query).limit(0).skip(0), Mod.class);

        return PageableExecutionUtils.getPage(list, pageable, () -> count);
    }
}