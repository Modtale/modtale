package net.modtale.repository.project;

import java.time.LocalDate;
import java.util.List;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectSort;
import net.modtale.model.project.ProjectViewCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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
            String authorId,
            Boolean openSource
    );

    Page<Project> searchProjectMarquee(
            String search,
            List<String> tags,
            String gameVersion,
            ProjectClassification classification,
            Integer minDownloads,
            Integer minFavorites,
            Pageable pageable,
            ProjectSort sortBy,
            ProjectViewCategory viewCategory,
            LocalDate dateCutoff,
            String authorId,
            Boolean openSource
    );

    Page<Project> findFavorites(List<String> projectIds, String search, Pageable pageable, Boolean openSource);

    Page<Project> searchDeletedProjects(String search, Pageable pageable);
}
