package net.modtale.service.project;

import net.modtale.exception.InvalidProjectRequestException;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectSort;
import net.modtale.model.project.ProjectViewCategory;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
public class ProjectCatalogSearchService {

    private final ProjectRepository projectRepository;
    private final ProjectSearchResultDecorator projectSearchResultDecorator;

    public ProjectCatalogSearchService(
            ProjectRepository projectRepository,
            ProjectSearchResultDecorator projectSearchResultDecorator
    ) {
        this.projectRepository = projectRepository;
        this.projectSearchResultDecorator = projectSearchResultDecorator;
    }

    public Page<Project> searchProjects(
            List<String> tags,
            String search,
            int page,
            int size,
            ProjectSort sortBy,
            String gameVersion,
            ProjectClassification contentType,
            Integer minDownloads,
            Integer minFavorites,
            ProjectViewCategory viewCategory,
            String dateRange,
            String authorId,
            User currentUser
    ) {
        if (viewCategory == ProjectViewCategory.FAVORITES) {
            List<String> likedIds = (currentUser != null && currentUser.getLikedModIds() != null)
                    ? currentUser.getLikedModIds()
                    : new ArrayList<>();
            return projectRepository.findFavorites(
                    likedIds,
                    search != null ? search : "",
                    PageRequest.of(page, size, Sort.by("title"))
            );
        }

        Sort sort = resolveSort(sortBy);
        LocalDate dateCutoff = resolveDateCutoff(dateRange);

        Page<Project> results = projectRepository.searchProjects(
                search,
                tags,
                gameVersion,
                contentType,
                minDownloads,
                minFavorites,
                PageRequest.of(page, size, sort),
                currentUser != null ? currentUser.getId() : null,
                sortBy,
                viewCategory,
                dateCutoff,
                authorId
        );

        return projectSearchResultDecorator.decorateCatalogResults(results);
    }

    public Page<Project> searchDeletedProjects(String query, org.springframework.data.domain.Pageable pageable) {
        return projectRepository.searchDeletedProjects(query, pageable);
    }

    public List<Project> getPublishedProjects() {
        return projectRepository.findAllPublished();
    }

    private Sort resolveSort(ProjectSort sortBy) {
        ProjectSort effectiveSort = sortBy != null ? sortBy : ProjectSort.RELEVANCE;
        return switch (effectiveSort) {
            case DOWNLOADS -> Sort.by("downloadCount").descending();
            case UPDATED -> Sort.by("updatedAt").descending();
            case NEWEST -> Sort.by("createdAt").descending();
            case FAVORITES -> Sort.by("favoriteCount").descending();
            case POPULAR -> Sort.by("popularScore").descending();
            case TRENDING -> Sort.by("trendScore").descending();
            default -> Sort.unsorted();
        };
    }

    private LocalDate resolveDateCutoff(String dateRange) {
        if (dateRange == null || dateRange.equals("all") || dateRange.isEmpty()) {
            return null;
        }

        try {
            return switch (dateRange) {
                case "7d" -> LocalDate.now().minusDays(7);
                case "30d" -> LocalDate.now().minusDays(30);
                case "90d" -> LocalDate.now().minusDays(90);
                case "1y" -> LocalDate.now().minusYears(1);
                default -> LocalDate.parse(dateRange.substring(0, 10));
            };
        } catch (DateTimeParseException | IndexOutOfBoundsException ex) {
            throw new InvalidProjectRequestException("Date ranges must be 7d, 30d, 90d, 1y, all, or a valid ISO-8601 date.");
        }
    }
}
