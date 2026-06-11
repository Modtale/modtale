package net.modtale.repository.project;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectSort;
import net.modtale.model.project.ProjectViewCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

public interface ProjectRepositoryCustom {
    Page<Project> searchProjects(
            String search,
            List<String> tags,
            String gameVersion,
            ProjectClassification classification,
            Integer minDownloads,
            Integer minFavorites,
            Pageable pageable,
            String currentUserId,
            ProjectSort sortBy,
            ProjectViewCategory viewCategory,
            LocalDate dateCutoff,
            String authorId
    );

    Page<Project> findFavorites(List<String> projectIds, String search, Pageable pageable);

    Page<Project> searchDeletedProjects(String search, Pageable pageable);
}
