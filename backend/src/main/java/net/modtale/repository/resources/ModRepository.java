package net.modtale.repository.resources;

import net.modtale.model.resources.Mod;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ModRepository extends MongoRepository<Mod, String>, ModRepositoryCustom {

    @Query(value = "{ 'author': {$regex: '^?0$', $options: 'i'}, 'deletedAt': null }", fields = "{ 'about': 0, 'reviews': 0, 'galleryImages': 0 }")
    Page<Mod> findByAuthorIgnoreCase(String author, Pageable pageable);

    @Query(value = "{ 'author': ?0, 'status': { $in: ['PUBLISHED', 'ARCHIVED'] }, 'deletedAt': null }", fields = "{ 'about': 0, 'reviews': 0, 'galleryImages': 0 }")
    Page<Mod> findByAuthor(String author, Pageable pageable);

    @Query(value = "{ 'author': ?0, 'status': ?1, 'deletedAt': null }", fields = "{ 'about': 0, 'reviews': 0, 'galleryImages': 0 }")
    Page<Mod> findByAuthorAndStatus(String author, String status, Pageable pageable);

    @Query(value = "{ 'author': ?0, 'deletedAt': null }")
    List<Mod> findByAuthor(String author);

    @Query(value = "{ 'author': ?0, 'deletedAt': null }", fields = "{ 'title': 1, 'rating': 1, 'downloadCount': 1 }")
    List<Mod> findMetaByAuthor(String author);

    Optional<Mod> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @Query(value = "{ 'author': ?0, 'deletedAt': null }", count = true)
    long countByAuthor(String author);

    boolean existsByTitleIgnoreCase(String title);

    @Query(value = "{ 'contributors': ?0, 'deletedAt': null }", fields = "{ 'about': 0, 'reviews': 0, 'galleryImages': 0 }")
    Page<Mod> findByContributors(String username, Pageable pageable);

    @Query("{ 'versions.dependencies.modId': ?0 }")
    List<Mod> findByDependency(String modId);

    @Query("{ 'versions.fileUrl': ?0 }")
    Optional<Mod> findByVersionsFileUrl(String fileUrl);

    @Query(value = "{ 'status': { $in: ['PUBLISHED', 'ARCHIVED'] }, 'deletedAt': null }", fields = "{ 'tags' : 1 }")
    List<Mod> findAllWithTags();

    @Query(value = "{ 'status': { $in: ['PUBLISHED', 'ARCHIVED'] }, 'deletedAt': null }")
    List<Mod> findAllPublished();

    @Query(value = "{ 'status': 'PUBLISHED', 'deletedAt': null }", fields = "{ 'id': 1, 'title': 1, 'slug': 1, 'updatedAt': 1, 'classification': 1, 'author': 1 }")
    List<Mod> findAllForSitemap();

    List<Mod> findByDeletedAtBefore(LocalDateTime date);

    void deleteByStatusAndExpiresAtBefore(String status, String date);
}