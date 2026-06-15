package net.modtale.repository.project;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface ProjectRepository extends MongoRepository<Project, String>, ProjectRepositoryCustom {

    @Query(value = "{ 'authorId': ?0, 'deletedAt': null }")
    Page<Project> findByAuthorId(String authorId, Pageable pageable);

    @Query(value = "{ 'authorId': ?0, 'status': { $in: ['PUBLISHED', 'ARCHIVED'] }, 'deletedAt': null }")
    Page<Project> findByAuthorIdAndStatus(String authorId, String status, Pageable pageable);

    @Query(value = "{ 'authorId': ?0, 'status': ?1, 'deletedAt': null }")
    Page<Project> findByAuthorIdAndStatusExact(String authorId, ProjectStatus status, Pageable pageable);

    @Query(value = "{ 'authorId': ?0, 'deletedAt': null }")
    List<Project> findByAuthorIdList(String authorId);

    @Query(value = "{ 'authorId': ?0, 'deletedAt': null }", fields = "{ 'title': 1, 'downloadCount': 1, 'updatedAt': 1 }")
    List<Project> findMetaByAuthorId(String authorId);

    boolean existsBySlug(String slug);

    Optional<Project> findBySlug(String slug);

    @Query(value = "{ '_id': ?0 }", fields = "{ 'slug': 1, 'status': 1, 'authorId': 1, 'teamMembers': 1, 'projectRoles': 1, 'deletedAt': 1 }")
    Optional<Project> findPermissionSnapshotById(String id);

    @Query(value = "{ 'slug': ?0 }", fields = "{ 'slug': 1, 'status': 1, 'authorId': 1, 'teamMembers': 1, 'projectRoles': 1, 'deletedAt': 1 }")
    Optional<Project> findPermissionSnapshotBySlug(String slug);

    @Query(
            value = "{ '_id': ?0, 'deletedAt': null }",
            fields = "{ '_id': 1, 'slug': 1, 'title': 1, 'about': 1, 'description': 1, 'authorId': 1, 'author': 1, 'imageUrl': 1, 'bannerUrl': 1, 'classification': 1, 'categories': 1, 'tags': 1, 'downloadCount': 1, 'favoriteCount': 1, 'trendScore': 1, 'relevanceScore': 1, 'popularScore': 1, 'repositoryUrl': 1, 'updatedAt': 1, 'createdAt': 1, 'license': 1, 'lastTrendingNotification': 1, 'links': 1, 'types': 1, 'childProjectIds': 1, 'modIds': 1, 'allowModpacks': 1, 'allowComments': 1, 'hmWikiEnabled': 1, 'hmWikiSlug': 1, 'galleryCarouselEnabled': 1, 'status': 1, 'expiresAt': 1, 'deletedAt': 1, 'projectRoles': 1, 'teamMembers': 1, 'teamInvites': 1, 'galleryImages': 1, 'galleryImageCaptions': 1, 'comments': 1, 'versions._id': 1, 'versions.versionNumber': 1, 'versions.gameVersions': 1, 'versions.fileUrl': 1, 'versions.downloadCount': 1, 'versions.releaseDate': 1, 'versions.changelog': 1, 'versions.dependencies': 1, 'versions.incompatibleProjectIds': 1, 'versions.channel': 1, 'versions.reviewStatus': 1 }"
    )
    Optional<Project> findViewerDetailById(String id);

    @Query(
            value = "{ 'slug': ?0, 'deletedAt': null }",
            fields = "{ '_id': 1, 'slug': 1, 'title': 1, 'about': 1, 'description': 1, 'authorId': 1, 'author': 1, 'imageUrl': 1, 'bannerUrl': 1, 'classification': 1, 'categories': 1, 'tags': 1, 'downloadCount': 1, 'favoriteCount': 1, 'trendScore': 1, 'relevanceScore': 1, 'popularScore': 1, 'repositoryUrl': 1, 'updatedAt': 1, 'createdAt': 1, 'license': 1, 'lastTrendingNotification': 1, 'links': 1, 'types': 1, 'childProjectIds': 1, 'modIds': 1, 'allowModpacks': 1, 'allowComments': 1, 'hmWikiEnabled': 1, 'hmWikiSlug': 1, 'galleryCarouselEnabled': 1, 'status': 1, 'expiresAt': 1, 'deletedAt': 1, 'projectRoles': 1, 'teamMembers': 1, 'teamInvites': 1, 'galleryImages': 1, 'galleryImageCaptions': 1, 'comments': 1, 'versions._id': 1, 'versions.versionNumber': 1, 'versions.gameVersions': 1, 'versions.fileUrl': 1, 'versions.downloadCount': 1, 'versions.releaseDate': 1, 'versions.changelog': 1, 'versions.dependencies': 1, 'versions.incompatibleProjectIds': 1, 'versions.channel': 1, 'versions.reviewStatus': 1 }"
    )
    Optional<Project> findViewerDetailBySlug(String slug);

    @Query(
            value = "{ '_id': ?0, 'deletedAt': null }",
            fields = "{ '_id': 1, 'slug': 1, 'title': 1, 'about': 1, 'description': 1, 'authorId': 1, 'author': 1, 'imageUrl': 1, 'bannerUrl': 1, 'classification': 1, 'categories': 1, 'tags': 1, 'downloadCount': 1, 'favoriteCount': 1, 'trendScore': 1, 'relevanceScore': 1, 'popularScore': 1, 'repositoryUrl': 1, 'updatedAt': 1, 'createdAt': 1, 'license': 1, 'lastTrendingNotification': 1, 'links': 1, 'types': 1, 'childProjectIds': 1, 'modIds': 1, 'allowModpacks': 1, 'allowComments': 1, 'hmWikiEnabled': 1, 'hmWikiSlug': 1, 'galleryCarouselEnabled': 1, 'status': 1, 'expiresAt': 1, 'deletedAt': 1, 'projectRoles': 1, 'teamMembers': 1, 'teamInvites': 1, 'galleryImages': 1, 'galleryImageCaptions': 1, 'comments': 1, 'versions._id': 1, 'versions.versionNumber': 1, 'versions.gameVersions': 1, 'versions.fileUrl': 1, 'versions.downloadCount': 1, 'versions.releaseDate': 1, 'versions.dependencies': 1, 'versions.incompatibleProjectIds': 1, 'versions.channel': 1, 'versions.reviewStatus': 1 }"
    )
    Optional<Project> findViewerDetailsPayloadById(String id);

    @Query(
            value = "{ 'slug': ?0, 'deletedAt': null }",
            fields = "{ '_id': 1, 'slug': 1, 'title': 1, 'about': 1, 'description': 1, 'authorId': 1, 'author': 1, 'imageUrl': 1, 'bannerUrl': 1, 'classification': 1, 'categories': 1, 'tags': 1, 'downloadCount': 1, 'favoriteCount': 1, 'trendScore': 1, 'relevanceScore': 1, 'popularScore': 1, 'repositoryUrl': 1, 'updatedAt': 1, 'createdAt': 1, 'license': 1, 'lastTrendingNotification': 1, 'links': 1, 'types': 1, 'childProjectIds': 1, 'modIds': 1, 'allowModpacks': 1, 'allowComments': 1, 'hmWikiEnabled': 1, 'hmWikiSlug': 1, 'galleryCarouselEnabled': 1, 'status': 1, 'expiresAt': 1, 'deletedAt': 1, 'projectRoles': 1, 'teamMembers': 1, 'teamInvites': 1, 'galleryImages': 1, 'galleryImageCaptions': 1, 'comments': 1, 'versions._id': 1, 'versions.versionNumber': 1, 'versions.gameVersions': 1, 'versions.fileUrl': 1, 'versions.downloadCount': 1, 'versions.releaseDate': 1, 'versions.dependencies': 1, 'versions.incompatibleProjectIds': 1, 'versions.channel': 1, 'versions.reviewStatus': 1 }"
    )
    Optional<Project> findViewerDetailsPayloadBySlug(String slug);

    @Query(
            value = "{ '_id': ?0, 'status': { $in: ['PUBLISHED', 'UNLISTED', 'ARCHIVED'] }, 'deletedAt': null }",
            fields = "{ '_id': 1, 'slug': 1, 'title': 1, 'about': 1, 'description': 1, 'authorId': 1, 'author': 1, 'imageUrl': 1, 'bannerUrl': 1, 'classification': 1, 'categories': 1, 'tags': 1, 'downloadCount': 1, 'favoriteCount': 1, 'trendScore': 1, 'relevanceScore': 1, 'popularScore': 1, 'repositoryUrl': 1, 'updatedAt': 1, 'createdAt': 1, 'license': 1, 'lastTrendingNotification': 1, 'links': 1, 'types': 1, 'childProjectIds': 1, 'modIds': 1, 'allowModpacks': 1, 'allowComments': 1, 'hmWikiEnabled': 1, 'hmWikiSlug': 1, 'galleryCarouselEnabled': 1, 'status': 1, 'expiresAt': 1, 'deletedAt': 1, 'projectRoles': 1, 'teamMembers': 1, 'galleryImages': 1, 'galleryImageCaptions': 1, 'comments': 1, 'versions._id': 1, 'versions.versionNumber': 1, 'versions.gameVersions': 1, 'versions.fileUrl': 1, 'versions.downloadCount': 1, 'versions.releaseDate': 1, 'versions.changelog': 1, 'versions.dependencies': 1, 'versions.incompatibleProjectIds': 1, 'versions.channel': 1, 'versions.reviewStatus': 1 }"
    )
    Optional<Project> findPublicDetailById(String id);

    @Query(
            value = "{ 'slug': ?0, 'status': { $in: ['PUBLISHED', 'UNLISTED', 'ARCHIVED'] }, 'deletedAt': null }",
            fields = "{ '_id': 1, 'slug': 1, 'title': 1, 'about': 1, 'description': 1, 'authorId': 1, 'author': 1, 'imageUrl': 1, 'bannerUrl': 1, 'classification': 1, 'categories': 1, 'tags': 1, 'downloadCount': 1, 'favoriteCount': 1, 'trendScore': 1, 'relevanceScore': 1, 'popularScore': 1, 'repositoryUrl': 1, 'updatedAt': 1, 'createdAt': 1, 'license': 1, 'lastTrendingNotification': 1, 'links': 1, 'types': 1, 'childProjectIds': 1, 'modIds': 1, 'allowModpacks': 1, 'allowComments': 1, 'hmWikiEnabled': 1, 'hmWikiSlug': 1, 'galleryCarouselEnabled': 1, 'status': 1, 'expiresAt': 1, 'deletedAt': 1, 'projectRoles': 1, 'teamMembers': 1, 'galleryImages': 1, 'galleryImageCaptions': 1, 'comments': 1, 'versions._id': 1, 'versions.versionNumber': 1, 'versions.gameVersions': 1, 'versions.fileUrl': 1, 'versions.downloadCount': 1, 'versions.releaseDate': 1, 'versions.changelog': 1, 'versions.dependencies': 1, 'versions.incompatibleProjectIds': 1, 'versions.channel': 1, 'versions.reviewStatus': 1 }"
    )
    Optional<Project> findPublicDetailBySlug(String slug);

    @Query(
            value = "{ '_id': ?0, 'status': { $in: ['PUBLISHED', 'UNLISTED', 'ARCHIVED'] }, 'deletedAt': null }",
            fields = "{ '_id': 1, 'slug': 1, 'title': 1, 'about': 1, 'description': 1, 'authorId': 1, 'author': 1, 'imageUrl': 1, 'bannerUrl': 1, 'classification': 1, 'categories': 1, 'tags': 1, 'downloadCount': 1, 'favoriteCount': 1, 'trendScore': 1, 'relevanceScore': 1, 'popularScore': 1, 'repositoryUrl': 1, 'updatedAt': 1, 'createdAt': 1, 'license': 1, 'lastTrendingNotification': 1, 'links': 1, 'types': 1, 'childProjectIds': 1, 'modIds': 1, 'allowModpacks': 1, 'allowComments': 1, 'hmWikiEnabled': 1, 'hmWikiSlug': 1, 'galleryCarouselEnabled': 1, 'status': 1, 'expiresAt': 1, 'deletedAt': 1, 'projectRoles': 1, 'teamMembers': 1, 'galleryImages': 1, 'galleryImageCaptions': 1, 'comments': 1, 'versions._id': 1, 'versions.versionNumber': 1, 'versions.gameVersions': 1, 'versions.fileUrl': 1, 'versions.downloadCount': 1, 'versions.releaseDate': 1, 'versions.dependencies': 1, 'versions.incompatibleProjectIds': 1, 'versions.channel': 1, 'versions.reviewStatus': 1 }"
    )
    Optional<Project> findPublicDetailsPayloadById(String id);

    @Query(
            value = "{ 'slug': ?0, 'status': { $in: ['PUBLISHED', 'UNLISTED', 'ARCHIVED'] }, 'deletedAt': null }",
            fields = "{ '_id': 1, 'slug': 1, 'title': 1, 'about': 1, 'description': 1, 'authorId': 1, 'author': 1, 'imageUrl': 1, 'bannerUrl': 1, 'classification': 1, 'categories': 1, 'tags': 1, 'downloadCount': 1, 'favoriteCount': 1, 'trendScore': 1, 'relevanceScore': 1, 'popularScore': 1, 'repositoryUrl': 1, 'updatedAt': 1, 'createdAt': 1, 'license': 1, 'lastTrendingNotification': 1, 'links': 1, 'types': 1, 'childProjectIds': 1, 'modIds': 1, 'allowModpacks': 1, 'allowComments': 1, 'hmWikiEnabled': 1, 'hmWikiSlug': 1, 'galleryCarouselEnabled': 1, 'status': 1, 'expiresAt': 1, 'deletedAt': 1, 'projectRoles': 1, 'teamMembers': 1, 'galleryImages': 1, 'galleryImageCaptions': 1, 'comments': 1, 'versions._id': 1, 'versions.versionNumber': 1, 'versions.gameVersions': 1, 'versions.fileUrl': 1, 'versions.downloadCount': 1, 'versions.releaseDate': 1, 'versions.dependencies': 1, 'versions.incompatibleProjectIds': 1, 'versions.channel': 1, 'versions.reviewStatus': 1 }"
    )
    Optional<Project> findPublicDetailsPayloadBySlug(String slug);

    @Query(
            value = "{ '_id': ?0, 'status': { $in: ['PUBLISHED', 'UNLISTED', 'ARCHIVED'] }, 'deletedAt': null }",
            fields = "{ '_id': 1, 'slug': 1, 'title': 1, 'about': 1, 'description': 1, 'authorId': 1, 'author': 1, 'imageUrl': 1, 'bannerUrl': 1, 'classification': 1, 'tags': 1, 'downloadCount': 1, 'favoriteCount': 1, 'repositoryUrl': 1, 'updatedAt': 1, 'createdAt': 1, 'license': 1, 'links': 1, 'allowModpacks': 1, 'allowComments': 1, 'hmWikiEnabled': 1, 'hmWikiSlug': 1, 'galleryCarouselEnabled': 1, 'status': 1, 'expiresAt': 1, 'deletedAt': 1 }"
    )
    Optional<Project> findPublicPageShellById(String id);

    @Query(
            value = "{ 'slug': ?0, 'status': { $in: ['PUBLISHED', 'UNLISTED', 'ARCHIVED'] }, 'deletedAt': null }",
            fields = "{ '_id': 1, 'slug': 1, 'title': 1, 'about': 1, 'description': 1, 'authorId': 1, 'author': 1, 'imageUrl': 1, 'bannerUrl': 1, 'classification': 1, 'tags': 1, 'downloadCount': 1, 'favoriteCount': 1, 'repositoryUrl': 1, 'updatedAt': 1, 'createdAt': 1, 'license': 1, 'links': 1, 'allowModpacks': 1, 'allowComments': 1, 'hmWikiEnabled': 1, 'hmWikiSlug': 1, 'galleryCarouselEnabled': 1, 'status': 1, 'expiresAt': 1, 'deletedAt': 1 }"
    )
    Optional<Project> findPublicPageShellBySlug(String slug);

    @Query(
            value = "{ '_id': ?0, 'deletedAt': null }",
            fields = "{ '_id': 1, 'slug': 1, 'title': 1, 'about': 1, 'description': 1, 'authorId': 1, 'author': 1, 'imageUrl': 1, 'bannerUrl': 1, 'classification': 1, 'tags': 1, 'downloadCount': 1, 'favoriteCount': 1, 'repositoryUrl': 1, 'updatedAt': 1, 'createdAt': 1, 'license': 1, 'links': 1, 'allowModpacks': 1, 'allowComments': 1, 'hmWikiEnabled': 1, 'hmWikiSlug': 1, 'galleryCarouselEnabled': 1, 'status': 1, 'expiresAt': 1, 'deletedAt': 1, 'projectRoles': 1, 'teamMembers': 1 }"
    )
    Optional<Project> findViewerPageShellById(String id);

    @Query(
            value = "{ 'slug': ?0, 'deletedAt': null }",
            fields = "{ '_id': 1, 'slug': 1, 'title': 1, 'about': 1, 'description': 1, 'authorId': 1, 'author': 1, 'imageUrl': 1, 'bannerUrl': 1, 'classification': 1, 'tags': 1, 'downloadCount': 1, 'favoriteCount': 1, 'repositoryUrl': 1, 'updatedAt': 1, 'createdAt': 1, 'license': 1, 'links': 1, 'allowModpacks': 1, 'allowComments': 1, 'hmWikiEnabled': 1, 'hmWikiSlug': 1, 'galleryCarouselEnabled': 1, 'status': 1, 'expiresAt': 1, 'deletedAt': 1, 'projectRoles': 1, 'teamMembers': 1 }"
    )
    Optional<Project> findViewerPageShellBySlug(String slug);

    @Query(
            value = "{ '_id': ?0, 'status': { $in: ['PUBLISHED', 'UNLISTED', 'ARCHIVED'] }, 'deletedAt': null }",
            fields = "{ '_id': 1, 'slug': 1, 'authorId': 1, 'status': 1, 'deletedAt': 1, 'versions._id': 1, 'versions.versionNumber': 1, 'versions.gameVersions': 1, 'versions.fileUrl': 1, 'versions.downloadCount': 1, 'versions.releaseDate': 1, 'versions.dependencies': 1, 'versions.incompatibleProjectIds': 1, 'versions.channel': 1, 'versions.reviewStatus': 1 }"
    )
    Optional<Project> findPublicVersionsById(String id);

    @Query(
            value = "{ 'slug': ?0, 'status': { $in: ['PUBLISHED', 'UNLISTED', 'ARCHIVED'] }, 'deletedAt': null }",
            fields = "{ '_id': 1, 'slug': 1, 'authorId': 1, 'status': 1, 'deletedAt': 1, 'versions._id': 1, 'versions.versionNumber': 1, 'versions.gameVersions': 1, 'versions.fileUrl': 1, 'versions.downloadCount': 1, 'versions.releaseDate': 1, 'versions.dependencies': 1, 'versions.incompatibleProjectIds': 1, 'versions.channel': 1, 'versions.reviewStatus': 1 }"
    )
    Optional<Project> findPublicVersionsBySlug(String slug);

    @Query(
            value = "{ '_id': ?0, 'deletedAt': null }",
            fields = "{ '_id': 1, 'slug': 1, 'authorId': 1, 'status': 1, 'deletedAt': 1, 'projectRoles': 1, 'teamMembers': 1, 'versions._id': 1, 'versions.versionNumber': 1, 'versions.gameVersions': 1, 'versions.fileUrl': 1, 'versions.downloadCount': 1, 'versions.releaseDate': 1, 'versions.dependencies': 1, 'versions.incompatibleProjectIds': 1, 'versions.channel': 1, 'versions.reviewStatus': 1 }"
    )
    Optional<Project> findViewerVersionsById(String id);

    @Query(
            value = "{ 'slug': ?0, 'deletedAt': null }",
            fields = "{ '_id': 1, 'slug': 1, 'authorId': 1, 'status': 1, 'deletedAt': 1, 'projectRoles': 1, 'teamMembers': 1, 'versions._id': 1, 'versions.versionNumber': 1, 'versions.gameVersions': 1, 'versions.fileUrl': 1, 'versions.downloadCount': 1, 'versions.releaseDate': 1, 'versions.dependencies': 1, 'versions.incompatibleProjectIds': 1, 'versions.channel': 1, 'versions.reviewStatus': 1 }"
    )
    Optional<Project> findViewerVersionsBySlug(String slug);

    @Query(
            value = "{ '_id': ?0, 'status': { $in: ['PUBLISHED', 'UNLISTED', 'ARCHIVED'] }, 'deletedAt': null }",
            fields = "{ '_id': 1, 'slug': 1, 'authorId': 1, 'status': 1, 'deletedAt': 1, 'allowComments': 1, 'comments': 1 }"
    )
    Optional<Project> findPublicCommentsById(String id);

    @Query(
            value = "{ 'slug': ?0, 'status': { $in: ['PUBLISHED', 'UNLISTED', 'ARCHIVED'] }, 'deletedAt': null }",
            fields = "{ '_id': 1, 'slug': 1, 'authorId': 1, 'status': 1, 'deletedAt': 1, 'allowComments': 1, 'comments': 1 }"
    )
    Optional<Project> findPublicCommentsBySlug(String slug);

    @Query(
            value = "{ '_id': ?0, 'deletedAt': null }",
            fields = "{ '_id': 1, 'slug': 1, 'authorId': 1, 'status': 1, 'deletedAt': 1, 'allowComments': 1, 'projectRoles': 1, 'teamMembers': 1, 'comments': 1 }"
    )
    Optional<Project> findViewerCommentsById(String id);

    @Query(
            value = "{ 'slug': ?0, 'deletedAt': null }",
            fields = "{ '_id': 1, 'slug': 1, 'authorId': 1, 'status': 1, 'deletedAt': 1, 'allowComments': 1, 'projectRoles': 1, 'teamMembers': 1, 'comments': 1 }"
    )
    Optional<Project> findViewerCommentsBySlug(String slug);

    @Query(
            value = "{ '_id': ?0, 'status': { $in: ['PUBLISHED', 'UNLISTED', 'ARCHIVED'] }, 'deletedAt': null }",
            fields = "{ '_id': 1, 'slug': 1, 'authorId': 1, 'status': 1, 'deletedAt': 1, 'galleryImages': 1, 'galleryImageCaptions': 1 }"
    )
    Optional<Project> findPublicGalleryById(String id);

    @Query(
            value = "{ 'slug': ?0, 'status': { $in: ['PUBLISHED', 'UNLISTED', 'ARCHIVED'] }, 'deletedAt': null }",
            fields = "{ '_id': 1, 'slug': 1, 'authorId': 1, 'status': 1, 'deletedAt': 1, 'galleryImages': 1, 'galleryImageCaptions': 1 }"
    )
    Optional<Project> findPublicGalleryBySlug(String slug);

    @Query(
            value = "{ '_id': ?0, 'deletedAt': null }",
            fields = "{ '_id': 1, 'slug': 1, 'authorId': 1, 'status': 1, 'deletedAt': 1, 'projectRoles': 1, 'teamMembers': 1, 'galleryImages': 1, 'galleryImageCaptions': 1 }"
    )
    Optional<Project> findViewerGalleryById(String id);

    @Query(
            value = "{ 'slug': ?0, 'deletedAt': null }",
            fields = "{ '_id': 1, 'slug': 1, 'authorId': 1, 'status': 1, 'deletedAt': 1, 'projectRoles': 1, 'teamMembers': 1, 'galleryImages': 1, 'galleryImageCaptions': 1 }"
    )
    Optional<Project> findViewerGalleryBySlug(String slug);

    @Query(
            value = "{ '_id': ?0, 'status': { $in: ['PUBLISHED', 'UNLISTED', 'ARCHIVED'] }, 'deletedAt': null }",
            fields = "{ '_id': 1, 'slug': 1, 'authorId': 1, 'status': 1, 'deletedAt': 1, 'projectRoles': 1, 'teamMembers': 1 }"
    )
    Optional<Project> findPublicTeamById(String id);

    @Query(
            value = "{ 'slug': ?0, 'status': { $in: ['PUBLISHED', 'UNLISTED', 'ARCHIVED'] }, 'deletedAt': null }",
            fields = "{ '_id': 1, 'slug': 1, 'authorId': 1, 'status': 1, 'deletedAt': 1, 'projectRoles': 1, 'teamMembers': 1 }"
    )
    Optional<Project> findPublicTeamBySlug(String slug);

    @Query(
            value = "{ '_id': ?0, 'deletedAt': null }",
            fields = "{ '_id': 1, 'slug': 1, 'authorId': 1, 'status': 1, 'deletedAt': 1, 'projectRoles': 1, 'teamMembers': 1, 'teamInvites': 1 }"
    )
    Optional<Project> findViewerTeamById(String id);

    @Query(
            value = "{ 'slug': ?0, 'deletedAt': null }",
            fields = "{ '_id': 1, 'slug': 1, 'authorId': 1, 'status': 1, 'deletedAt': 1, 'projectRoles': 1, 'teamMembers': 1, 'teamInvites': 1 }"
    )
    Optional<Project> findViewerTeamBySlug(String slug);

    @Query(
            value = "{ 'slug': ?0, 'status': { $in: ['PUBLISHED', 'UNLISTED', 'ARCHIVED'] }, 'deletedAt': null }",
            fields = "{ '_id': 1, 'slug': 1, 'status': 1, 'deletedAt': 1 }"
    )
    Optional<Project> findPublicRouteBySlug(String slug);

    @Query(
            value = "{ '_id': ?0, 'deletedAt': null }",
            fields = "{ '_id': 1, 'slug': 1, 'authorId': 1, 'status': 1, 'deletedAt': 1, 'projectRoles': 1, 'teamMembers': 1, 'versions._id': 1, 'versions.versionNumber': 1, 'versions.changelog': 1, 'versions.reviewStatus': 1 }"
    )
    Optional<Project> findChangelogsById(String id);

    @Query(
            value = "{ 'slug': ?0, 'deletedAt': null }",
            fields = "{ '_id': 1, 'slug': 1, 'authorId': 1, 'status': 1, 'deletedAt': 1, 'projectRoles': 1, 'teamMembers': 1, 'versions._id': 1, 'versions.versionNumber': 1, 'versions.changelog': 1, 'versions.reviewStatus': 1 }"
    )
    Optional<Project> findChangelogsBySlug(String slug);

    @Query(
            value = "{ '_id': ?0, 'status': { $in: ['PUBLISHED', 'UNLISTED', 'ARCHIVED'] }, 'deletedAt': null }",
            fields = "{ 'slug': 1, 'title': 1, 'description': 1, 'imageUrl': 1, 'author': 1, 'classification': 1, 'downloadCount': 1, 'repositoryUrl': 1, 'status': 1 }"
    )
    Optional<Project> findPublicMetaById(String id);

    @Query(
            value = "{ 'slug': ?0, 'status': { $in: ['PUBLISHED', 'UNLISTED', 'ARCHIVED'] }, 'deletedAt': null }",
            fields = "{ 'slug': 1, 'title': 1, 'description': 1, 'imageUrl': 1, 'author': 1, 'classification': 1, 'downloadCount': 1, 'repositoryUrl': 1, 'status': 1 }"
    )
    Optional<Project> findPublicMetaBySlug(String slug);

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
