package net.modtale.service.project;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProjectListingQueryService {

    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;
    private final ProjectSearchResultDecorator projectSearchResultDecorator;

    public ProjectListingQueryService(
            UserRepository userRepository,
            MongoTemplate mongoTemplate,
            ProjectSearchResultDecorator projectSearchResultDecorator
    ) {
        this.userRepository = userRepository;
        this.mongoTemplate = mongoTemplate;
        this.projectSearchResultDecorator = projectSearchResultDecorator;
    }

    public Page<Project> getCreatorProjects(String userId, Pageable pageable) {
        User creator = userRepository.findById(userId).orElse(null);
        if (creator == null) {
            return Page.empty();
        }

        Page<Project> results = findCreatorProjects(
                Criteria.where("authorId").is(creator.getId())
                        .and("status").is(ProjectStatus.PUBLISHED)
                        .and("deletedAt").is(null),
                pageable,
                false
        );
        return projectSearchResultDecorator.decorateCreatorResults(results, creator, true);
    }

    public Page<Project> getPrivilegedCreatorProjects(String userId, Pageable pageable) {
        User creator = userRepository.findById(userId).orElse(null);
        if (creator == null) {
            return Page.empty();
        }

        Page<Project> results = findCreatorProjects(
                Criteria.where("authorId").is(creator.getId())
                        .and("deletedAt").is(null),
                pageable,
                true
        );
        return projectSearchResultDecorator.decorateCreatorResults(results, creator, false);
    }

    public Page<Project> getContributedProjects(String userId, Pageable pageable) {
        Query query = new Query(Criteria.where("teamMembers.userId").is(userId));
        long count = mongoTemplate.count(query, Project.class);
        List<Project> projects = mongoTemplate.find(query.with(pageable), Project.class);
        return new PageImpl<>(
                projectSearchResultDecorator.decorateContributedProjects(projects),
                pageable,
                count
        );
    }

    private Page<Project> findCreatorProjects(Criteria criteria, Pageable pageable, boolean includeManagementFields) {
        Query query = new Query(criteria);
        applyCreatorSummaryProjection(query, includeManagementFields);
        List<Project> projects = mongoTemplate.find(query.with(pageable), Project.class);
        return PageableExecutionUtils.getPage(
                projects,
                pageable,
                () -> mongoTemplate.count(new Query(criteria), Project.class)
        );
    }

    private void applyCreatorSummaryProjection(Query query, boolean includeManagementFields) {
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
                .include("childProjectIds")
                .include("projectRoles")
                .include("teamMembers");

        if (includeManagementFields) {
            query.fields()
                    .include("status")
                    .include("versions._id")
                    .include("versions.versionNumber")
                    .include("versions.gameVersions")
                    .include("versions.downloadCount")
                    .include("versions.releaseDate")
                    .include("versions.channel")
                    .include("versions.reviewStatus")
                    .include("versions.rejectionReason");
        }
    }
}
