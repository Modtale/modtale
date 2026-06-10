package net.modtale.service.project;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProjectListingQueryService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;
    private final ProjectSearchResultDecorator projectSearchResultDecorator;

    public ProjectListingQueryService(
            ProjectRepository projectRepository,
            UserRepository userRepository,
            MongoTemplate mongoTemplate,
            ProjectSearchResultDecorator projectSearchResultDecorator
    ) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.mongoTemplate = mongoTemplate;
        this.projectSearchResultDecorator = projectSearchResultDecorator;
    }

    public Page<Project> getCreatorProjects(String userId, Pageable pageable) {
        User creator = userRepository.findById(userId).orElse(null);
        if (creator == null) {
            return Page.empty();
        }

        Page<Project> results = projectRepository.findByAuthorIdAndStatusExact(
                creator.getId(),
                ProjectStatus.PUBLISHED,
                pageable
        );
        return projectSearchResultDecorator.decorateCreatorResults(results, creator, true);
    }

    public Page<Project> getPrivilegedCreatorProjects(String userId, Pageable pageable) {
        User creator = userRepository.findById(userId).orElse(null);
        if (creator == null) {
            return Page.empty();
        }

        Page<Project> results = projectRepository.findByAuthorId(creator.getId(), pageable);
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
}
