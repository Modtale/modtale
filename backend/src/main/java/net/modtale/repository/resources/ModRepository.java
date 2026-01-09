package net.modtale.repository.resources;

import net.modtale.model.resources.Mod;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.util.List;
import java.util.Optional;

public interface ModRepository extends MongoRepository<Mod, String>, ModRepositoryCustom {

    Page<Mod> findByAuthorIgnoreCase(String author, Pageable pageable);

    @Query(value = "{ 'author': ?0, 'status': { $in: ['PUBLISHED', 'ARCHIVED'] } }")
    Page<Mod> findByAuthor(String author, Pageable pageable);

    Page<Mod> findByAuthorAndStatus(String author, String status, Pageable pageable);

    List<Mod> findByAuthor(String author);

    Optional<Mod> findBySlug(String slug);

    boolean existsBySlug(String slug);

    long countByAuthor(String author);

    boolean existsByTitleIgnoreCase(String title);

    @Query("{ 'contributors': ?0 }")
    Page<Mod> findByContributors(String username, Pageable pageable);

    @Query("{ 'versions.dependencies.modId': ?0 }")
    List<Mod> findByDependency(String modId);

    @Query("{ 'versions.fileUrl': ?0 }")
    Optional<Mod> findByVersionsFileUrl(String fileUrl);

    @Query(value = "{ 'status': { $in: ['PUBLISHED', 'ARCHIVED'] } }", fields = "{ 'tags' : 1 }")
    List<Mod> findAllWithTags();

    @Query(value = "{ 'status': { $in: ['PUBLISHED', 'ARCHIVED'] } }")
    List<Mod> findAllPublished();

    @Query(value = "{ 'status': 'PUBLISHED' }", fields = "{ 'id': 1, 'title': 1, 'slug': 1, 'updatedAt': 1, 'classification': 1, 'author': 1 }")
    List<Mod> findAllForSitemap();

    void deleteByStatusAndExpiresAtBefore(String status, String date);
}