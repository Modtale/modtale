package net.modtale.repository.project;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectSort;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectViewCategory;
import net.modtale.service.project.query.ProjectSearchResultDecorator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

@Repository
public class ProjectRepositoryImpl implements ProjectRepositoryCustom {
    private static final List<String> OPEN_SOURCE_LICENSES = List.of(
            "MIT",
            "Apache-2.0",
            "GPL-3.0",
            "LGPL-3.0",
            "AGPL-3.0",
            "MPL-2.0",
            "BSD-2-Clause",
            "BSD-3-Clause",
            "ISC",
            "Zlib",
            "Unlicense",
            "CC0-1.0",
            "CC-BY-4.0",
            "CC-BY-SA-4.0"
    );

    private final MongoTemplate mongoTemplate;
    private final ProjectSearchResultDecorator projectSearchResultDecorator;

    public ProjectRepositoryImpl(MongoTemplate mongoTemplate, ProjectSearchResultDecorator projectSearchResultDecorator) {
        this.mongoTemplate = mongoTemplate;
        this.projectSearchResultDecorator = projectSearchResultDecorator;
    }

    @Override
    public Page<Project> searchProjects(
            String search, List<String> tags, String gameVersion, ProjectClassification classification,
            Integer minDownloads, Integer minFavorites, Pageable pageable,
            String currentUserId, ProjectSort sortBy,
            ProjectViewCategory viewCategory, LocalDate dateCutoff, String authorId, Boolean openSource
    ) {
        return searchProjectsInternal(
                search,
                tags,
                gameVersion,
                classification,
                minDownloads,
                minFavorites,
                pageable,
                currentUserId,
                sortBy,
                viewCategory,
                dateCutoff,
                authorId,
                openSource,
                SearchProjection.CATALOG
        );
    }

    @Override
    public Page<Project> searchProjectMarquee(
            String search, List<String> tags, String gameVersion, ProjectClassification classification,
            Integer minDownloads, Integer minFavorites, Pageable pageable,
            ProjectSort sortBy,
            ProjectViewCategory viewCategory, LocalDate dateCutoff, String authorId, Boolean openSource
    ) {
        return searchProjectsInternal(
                search,
                tags,
                gameVersion,
                classification,
                minDownloads,
                minFavorites,
                pageable,
                null,
                sortBy,
                viewCategory,
                dateCutoff,
                authorId,
                openSource,
                SearchProjection.MARQUEE
        );
    }

    private Page<Project> searchProjectsInternal(
            String search, List<String> tags, String gameVersion, ProjectClassification classification,
            Integer minDownloads, Integer minFavorites, Pageable pageable,
            String currentUserId, ProjectSort sortBy,
            ProjectViewCategory viewCategory, LocalDate dateCutoff, String authorId,
            Boolean openSource,
            SearchProjection projection
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

        List<String> gameVersions = parseGameVersions(gameVersion);
        if (!gameVersions.isEmpty())
            criteriaList.add(Criteria.where("versions.gameVersions").in(gameVersions));
        if (minDownloads != null) criteriaList.add(Criteria.where("downloadCount").gte(minDownloads));
        if (minFavorites != null) criteriaList.add(Criteria.where("favoriteCount").gte(minFavorites));
        if (Boolean.TRUE.equals(openSource)) criteriaList.add(Criteria.where("license").in(OPEN_SOURCE_LICENSES));

        boolean isTimeBasedDownloadSort = sortBy == ProjectSort.DOWNLOADS && dateCutoff != null;
        if (dateCutoff != null && !isTimeBasedDownloadSort) {
            criteriaList.add(Criteria.where("updatedAt").gte(dateCutoff.toString()));
        }

        Criteria baseCriteria = criteriaList.isEmpty() ? new Criteria() :
                (criteriaList.size() == 1 ? criteriaList.get(0) : new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        Sort sort = resolveSort(sortBy, viewCategory, dateCutoff, pageable);
        Query query = new Query(baseCriteria).with(sort);
        if (projection == SearchProjection.MARQUEE) {
            applyMarqueeProjection(query);
        } else {
            applyCatalogSummaryProjection(query);
        }
        query.skip((long) pageable.getPageNumber() * pageable.getPageSize());
        query.limit(pageable.getPageSize());

        List<Project> results = mongoTemplate.find(query, Project.class);
        return PageableExecutionUtils.getPage(
                results,
                pageable,
                () -> mongoTemplate.count(new Query(baseCriteria), Project.class)
        );
    }

    private enum SearchProjection {
        CATALOG,
        MARQUEE
    }

    private Sort resolveSort(ProjectSort sortBy, ProjectViewCategory viewCategory, LocalDate dateCutoff, Pageable pageable) {
        if (sortBy == ProjectSort.TRENDING) {
            return Sort.by(
                    Sort.Order.asc("trendingRank"),
                    Sort.Order.desc("trendScore"),
                    Sort.Order.desc("downloads7d"),
                    Sort.Order.desc("downloadCount"),
                    Sort.Order.desc("updatedAt")
            );
        }

        if (sortBy == ProjectSort.POPULAR) {
            return Sort.by(
                    Sort.Order.asc("popularRank"),
                    Sort.Order.desc("popularScore"),
                    Sort.Order.desc("downloadCount"),
                    Sort.Order.desc("favoriteCount"),
                    Sort.Order.desc("updatedAt")
            );
        }

        if (sortBy == ProjectSort.DOWNLOADS && dateCutoff != null) {
            long daysDiff = ChronoUnit.DAYS.between(dateCutoff, LocalDate.now());
            if (daysDiff <= 7) {
                return Sort.by(Sort.Order.desc("downloads7d"), Sort.Order.desc("downloadCount"));
            }
            if (daysDiff <= 31) {
                return Sort.by(Sort.Order.desc("downloads30d"), Sort.Order.desc("downloadCount"));
            }
            if (daysDiff <= 92) {
                return Sort.by(Sort.Order.desc("downloads90d"), Sort.Order.desc("downloadCount"));
            }
        }

        if (sortBy == ProjectSort.RELEVANCE) {
            return Sort.by(
                    Sort.Order.asc("relevanceRank"),
                    Sort.Order.desc("relevanceScore"),
                    Sort.Order.desc("downloads30d"),
                    Sort.Order.desc("updatedAt")
            );
        }

        if (sortBy == ProjectSort.DOWNLOADS) {
            return Sort.by(Sort.Order.desc("downloadCount"), Sort.Order.desc("updatedAt"));
        }

        if (sortBy == ProjectSort.FAVORITES) {
            return Sort.by(Sort.Order.desc("favoriteCount"), Sort.Order.desc("updatedAt"));
        }

        if (sortBy == ProjectSort.NEWEST) {
            return Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("updatedAt"));
        }

        if (sortBy == ProjectSort.UPDATED) {
            return Sort.by(Sort.Order.desc("updatedAt"));
        }

        if (pageable.getSort().isSorted()) {
            return pageable.getSort();
        }

        return Sort.by(Sort.Order.desc("updatedAt"));
    }

    private List<String> parseGameVersions(String gameVersion) {
        if (gameVersion == null || gameVersion.trim().isEmpty()) {
            return List.of();
        }

        return Arrays.stream(gameVersion.split(","))
                .map(String::trim)
                .filter(version -> !version.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    private void applyCatalogSummaryProjection(Query query) {
        query.fields()
                .include("_id")
                .include("slug")
                .include("title")
                .include("description")
                .include("authorId")
                .include("author")
                .include("imageUrl")
                .include("bannerUrl")
                .include("classification")
                .include("downloadCount")
                .include("favoriteCount")
                .include("updatedAt")
                .include("childProjectIds");
    }

    private void applyMarqueeProjection(Query query) {
        query.fields()
                .include("_id")
                .include("slug")
                .include("title")
                .include("authorId")
                .include("author")
                .include("imageUrl")
                .include("bannerUrl")
                .include("classification")
                .include("downloadCount");
    }

    @Override
    public Page<Project> findFavorites(List<String> projectIds, String search, Pageable pageable, Boolean openSource) {
        if (projectIds == null || projectIds.isEmpty()) {
            return Page.empty(pageable);
        }

        Query query = new Query();
        query.addCriteria(Criteria.where("id").in(projectIds));
        query.addCriteria(Criteria.where("status").in(ProjectStatus.PUBLISHED, ProjectStatus.ARCHIVED));
        if (Boolean.TRUE.equals(openSource)) {
            query.addCriteria(Criteria.where("license").in(OPEN_SOURCE_LICENSES));
        }

        if (search != null && !search.trim().isEmpty()) {
            String regex = Pattern.quote(search);
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("title").regex(regex, "i"),
                    Criteria.where("description").regex(regex, "i")
            ));
        }

        Query countQuery = Query.of(query).limit(0).skip(0);
        applyCatalogSummaryProjection(query);
        query.with(pageable);
        List<Project> list = mongoTemplate.find(query, Project.class);
        return projectSearchResultDecorator.decorateCatalogResults(PageableExecutionUtils.getPage(
                list,
                pageable,
                () -> mongoTemplate.count(countQuery, Project.class)
        ));
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

        Query countQuery = Query.of(query).limit(0).skip(0);
        query.with(pageable);
        List<Project> results = mongoTemplate.find(query, Project.class);
        return projectSearchResultDecorator.decorateCatalogResults(PageableExecutionUtils.getPage(
                results,
                pageable,
                () -> mongoTemplate.count(countQuery, Project.class)
        ));
    }
}
