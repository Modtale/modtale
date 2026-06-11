package net.modtale.service.project;

import net.modtale.model.project.Project;
import net.modtale.model.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SearchService {

    private final ProjectCatalogSearchService projectCatalogSearchService;
    private final ProjectListingQueryService projectListingQueryService;

    public SearchService(
            ProjectCatalogSearchService projectCatalogSearchService,
            ProjectListingQueryService projectListingQueryService
    ) {
        this.projectCatalogSearchService = projectCatalogSearchService;
        this.projectListingQueryService = projectListingQueryService;
    }

    public Page<Project> searchProjects(
            List<String> tags,
            String search,
            int page,
            int size,
            String sortBy,
            String gameVersion,
            String contentType,
            Integer minDownloads,
            Integer minFavorites,
            String viewCategory,
            String dateRange,
            String authorId,
            User currentUser
    ) {
        return projectCatalogSearchService.searchProjects(
                tags,
                search,
                page,
                size,
                sortBy,
                gameVersion,
                contentType,
                minDownloads,
                minFavorites,
                viewCategory,
                dateRange,
                authorId,
                currentUser
        );
    }

    public Page<Project> searchDeletedProjects(String query, Pageable pageable) {
        return projectCatalogSearchService.searchDeletedProjects(query, pageable);
    }

    public Page<Project> getCreatorProjects(String userId, Pageable pageable) {
        return projectListingQueryService.getCreatorProjects(userId, pageable);
    }

    public Page<Project> getPrivilegedCreatorProjects(String userId, Pageable pageable) {
        return projectListingQueryService.getPrivilegedCreatorProjects(userId, pageable);
    }

    public Page<Project> getContributedProjects(String userId, Pageable pageable) {
        return projectListingQueryService.getContributedProjects(userId, pageable);
    }

    public List<Project> getPublishedProjects() {
        return projectCatalogSearchService.getPublishedProjects();
    }
}
