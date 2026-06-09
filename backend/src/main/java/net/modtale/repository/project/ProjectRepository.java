package net.modtale.repository.project;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends MongoRepository<Project, String>, ProjectRepositoryCustom {

    @Query(value = "{ 'authorId': ?0, 'deletedAt': null }")
    Page<Project> findByAuthorId(String authorId, Pageable pageable);

    @Query(value = "{ 'authorId': ?0, 'status': { $in: ['PUBLISHED', 'ARCHIVED'] }, 'deletedAt': null }")
    Page<Project> findByAuthorIdAndStatus(String authorId, String status, Pageable pageable);

    @Query(value = "{ 'authorId': ?0, 'status': ?1, 'deletedAt': null }")
    Page<Project> findByAuthorIdAndStatusExact(String authorId, ProjectStatus status, Pageable pageable);

    @Query(value = "{ 'authorId': ?0, 'deletedAt': null }")
    List<Project> findByAuthorIdList(String authorId);

    @Query(value = "{ 'authorId': ?0, 'deletedAt': null }", fields = "{ 'title': 1, 'rating': 1, 'downloadCount': 1 }")
    List<Project> findMetaByAuthorId(String authorId);

    Optional<Project> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @Query(value = "{ 'authorId': ?0, 'deletedAt': null }", count = true)
    long countByAuthorId(String authorId);

    boolean existsByTitleIgnoreCase(String title);

    @Query("{ 'versions.dependencies.modId': ?0 }")
    List<Project> findByDependency(String modId);

    @Query("{ 'versions.fileUrl': ?0 }")
    Optional<Project> findByVersionsFileUrl(String fileUrl);

    @Query("{ 'comments._id': ?0 }")
    Optional<Project> findByCommentsId(String commentId);

    @Query(value = "{ 'status': { $in: ['PUBLISHED', 'ARCHIVED'] }, 'deletedAt': null }", fields = "{ 'tags' : 1 }")
    List<Project> findAllWithTags();

    @Query(value = "{ 'status': { $in: ['PUBLISHED', 'ARCHIVED'] }, 'deletedAt': null }")
    List<Project> findAllPublished();

    @Query(value = "{ 'status': 'PUBLISHED', 'deletedAt': null }", fields = "{ 'id': 1, 'title': 1, 'slug': 1, 'updatedAt': 1, 'classification': 1, 'author': 1, 'authorId': 1 }")
    List<Project> findAllForSitemap();

    List<Project> findByDeletedAtBefore(LocalDateTime date);

    void deleteByStatusAndExpiresAtBefore(String status, String date);
}
