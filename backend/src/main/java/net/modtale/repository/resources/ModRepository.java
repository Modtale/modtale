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

    String CARD_FIELDS = "{ " +
            "'about': 0, " +
            "'comments': 0, " +
            "'galleryImages': 0, " +
            "'contributors': 0, " +
            "'pendingInvites': 0, " +
            "'modIds': 0, " +
            "'childProjectIds': 0, " +
            "'versions.scanResult': 0, " +
            "'versions.changelog': 0, " +
            "'versions.dependencies': 0, " +
            "'versions.rejectionReason': 0, " +
            "'versions.fileUrl': 0 " +
            "}";

    @Query(value = "{ '$or': [ { 'authorId': ?0 }, { 'author': { $regex: '^?0$', $options: 'i' } } ], 'deletedAt': null }", fields = CARD_FIELDS)
    Page<Mod> findByAuthorIdOrAuthorIgnoreCase(String authorIdOrName, Pageable pageable);

    @Query(value = "{ 'authorId': ?0, 'deletedAt': null }", fields = CARD_FIELDS)
    Page<Mod> findByAuthorId(String authorId, Pageable pageable);

    @Query(value = "{ 'authorId': ?0, 'status': { $in: ['PUBLISHED', 'ARCHIVED'] }, 'deletedAt': null }", fields = CARD_FIELDS)
    Page<Mod> findByAuthorIdAndStatus(String authorId, String status, Pageable pageable);

    @Query(value = "{ 'authorId': ?0, 'status': ?1, 'deletedAt': null }", fields = CARD_FIELDS)
    Page<Mod> findByAuthorIdAndStatusExact(String authorId, String status, Pageable pageable);

    @Query(value = "{ 'authorId': ?0, 'deletedAt': null }")
    List<Mod> findByAuthorIdList(String authorId);

    @Query(value = "{ 'author': ?0, 'deletedAt': null }")
    List<Mod> findByAuthor(String author);

    @Query(value = "{ 'authorId': ?0, 'deletedAt': null }", fields = "{ 'title': 1, 'rating': 1, 'downloadCount': 1 }")
    List<Mod> findMetaByAuthorId(String authorId);

    Optional<Mod> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @Query(value = "{ 'authorId': ?0, 'deletedAt': null }", count = true)
    long countByAuthorId(String authorId);

    boolean existsByTitleIgnoreCase(String title);

    @Query(value = "{ 'contributors': ?0, 'deletedAt': null }", fields = CARD_FIELDS)
    Page<Mod> findByContributors(String username, Pageable pageable);

    @Query("{ 'versions.dependencies.modId': ?0 }")
    List<Mod> findByDependency(String modId);

    @Query("{ 'versions.fileUrl': ?0 }")
    Optional<Mod> findByVersionsFileUrl(String fileUrl);

    @Query("{ 'comments._id': ?0 }")
    Optional<Mod> findByCommentsId(String commentId);

    @Query(value = "{ 'status': { $in: ['PUBLISHED', 'ARCHIVED'] }, 'deletedAt': null }", fields = "{ 'tags' : 1 }")
    List<Mod> findAllWithTags();

    @Query(value = "{ 'status': { $in: ['PUBLISHED', 'ARCHIVED'] }, 'deletedAt': null }", fields = CARD_FIELDS)
    List<Mod> findAllPublished();

    @Query(value = "{ 'status': 'PUBLISHED', 'deletedAt': null }", fields = "{ 'id': 1, 'title': 1, 'slug': 1, 'updatedAt': 1, 'classification': 1, 'author': 1, 'authorId': 1 }")
    List<Mod> findAllForSitemap();

    List<Mod> findByDeletedAtBefore(LocalDateTime date);

    void deleteByStatusAndExpiresAtBefore(String status, String date);
}