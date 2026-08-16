package net.modtale.model.project;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.modtale.model.user.ApiKey;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "projects")
@JsonInclude(JsonInclude.Include.NON_NULL)
@CompoundIndexes({
        @CompoundIndex(name = "status_class_downloads_idx", def = "{'status': 1, 'classification': 1, 'downloadCount': -1}"),
        @CompoundIndex(name = "status_class_favorites_idx", def = "{'status': 1, 'classification': 1, 'favoriteCount': -1}"),
        @CompoundIndex(name = "status_class_updated_idx", def = "{'status': 1, 'classification': 1, 'updatedAt': -1}"),
        @CompoundIndex(name = "status_class_created_idx", def = "{'status': 1, 'classification': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "status_trending_rank_idx", def = "{'status': 1, 'trendingRank': 1}"),
        @CompoundIndex(name = "status_popular_rank_idx", def = "{'status': 1, 'popularRank': 1}"),
        @CompoundIndex(name = "status_relevance_rank_idx", def = "{'status': 1, 'relevanceRank': 1}"),
        @CompoundIndex(name = "status_trending_score_idx", def = "{'status': 1, 'trendScore': -1}"),
        @CompoundIndex(name = "status_popular_score_idx", def = "{'status': 1, 'popularScore': -1}"),
        @CompoundIndex(name = "status_relevance_score_idx", def = "{'status': 1, 'relevanceScore': -1}"),
        @CompoundIndex(name = "status_class_trending_rank_idx", def = "{'status': 1, 'classification': 1, 'trendingRank': 1}"),
        @CompoundIndex(name = "status_class_popular_rank_idx", def = "{'status': 1, 'classification': 1, 'popularRank': 1}"),
        @CompoundIndex(name = "status_class_relevance_rank_idx", def = "{'status': 1, 'classification': 1, 'relevanceRank': 1}"),
        @CompoundIndex(name = "status_downloads_7d_idx", def = "{'status': 1, 'downloads7d': -1}"),
        @CompoundIndex(name = "status_downloads_30d_idx", def = "{'status': 1, 'downloads30d': -1}"),
        @CompoundIndex(name = "status_downloads_90d_idx", def = "{'status': 1, 'downloads90d': -1}"),
        @CompoundIndex(name = "status_class_downloads_7d_idx", def = "{'status': 1, 'classification': 1, 'downloads7d': -1}"),
        @CompoundIndex(name = "status_class_downloads_30d_idx", def = "{'status': 1, 'classification': 1, 'downloads30d': -1}"),
        @CompoundIndex(name = "status_class_downloads_90d_idx", def = "{'status': 1, 'classification': 1, 'downloads90d': -1}"),
        @CompoundIndex(name = "ranking_dirty_idx", def = "{'rankingDirty': 1}"),
        @CompoundIndex(name = "status_tags_downloads_idx", def = "{'status': 1, 'tags': 1, 'downloadCount': -1}"),
        @CompoundIndex(name = "status_tags_relevance_rank_idx", def = "{'status': 1, 'tags': 1, 'relevanceRank': 1}"),
        @CompoundIndex(name = "status_tags_updated_idx", def = "{'status': 1, 'tags': 1, 'updatedAt': -1}"),
        @CompoundIndex(name = "status_author_relevance_rank_idx", def = "{'status': 1, 'authorId': 1, 'relevanceRank': 1}"),
        @CompoundIndex(name = "status_author_updated_idx", def = "{'status': 1, 'authorId': 1, 'updatedAt': -1}"),
        @CompoundIndex(name = "status_game_version_relevance_rank_idx", def = "{'status': 1, 'versions.gameVersions': 1, 'relevanceRank': 1}"),
        @CompoundIndex(name = "status_game_version_downloads_idx", def = "{'status': 1, 'versions.gameVersions': 1, 'downloadCount': -1}"),
        @CompoundIndex(name = "status_game_version_updated_idx", def = "{'status': 1, 'versions.gameVersions': 1, 'updatedAt': -1}"),
        @CompoundIndex(name = "status_class_game_version_relevance_rank_idx", def = "{'status': 1, 'classification': 1, 'versions.gameVersions': 1, 'relevanceRank': 1}"),
        @CompoundIndex(name = "status_expires_idx", def = "{'status': 1, 'expiresAt': 1}"),
        @CompoundIndex(name = "deleted_at_idx", def = "{'deletedAt': 1}"),
        @CompoundIndex(name = "trend_score_idx", def = "{'trendScore': -1}"),
        @CompoundIndex(name = "relevance_score_idx", def = "{'relevanceScore': -1}"),
        @CompoundIndex(name = "popular_score_idx", def = "{'popularScore': -1}")
})
public class Project {

    public static class ProjectRole {
        private String id;
        private String name;
        private String color;
        private Set<ApiKey.ApiPermission> permissions;

        public ProjectRole() {}
        public ProjectRole(String id, String name, String color, Set<ApiKey.ApiPermission> permissions) {
            this.id = id;
            this.name = name;
            this.color = color;
            this.permissions = copyPermissions(permissions);
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }
        public Set<ApiKey.ApiPermission> getPermissions() { return permissions; }
        public void setPermissions(Set<ApiKey.ApiPermission> permissions) { this.permissions = copyPermissions(permissions); }

        private static Set<ApiKey.ApiPermission> copyPermissions(Set<ApiKey.ApiPermission> permissions) {
            if (permissions == null) {
                return null;
            }
            if (permissions.isEmpty()) {
                return EnumSet.noneOf(ApiKey.ApiPermission.class);
            }
            return EnumSet.copyOf(permissions);
        }
    }

    public static class ProjectMember {
        private String userId;
        private String roleId;

        @Transient private String username;
        @Transient private String avatarUrl;

        public ProjectMember() {}
        public ProjectMember(String userId, String roleId) {
            this.userId = userId; this.roleId = roleId;
        }

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getRoleId() { return roleId; }
        public void setRoleId(String roleId) { this.roleId = roleId; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getAvatarUrl() { return avatarUrl; }
        public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    }

    @Id
    private String id;

    @Indexed(unique = true, sparse = true)
    private String slug;

    @Indexed
    private String title;

    private String about;
    private String description;

    @Indexed
    private String authorId;

    @Indexed
    private String author;

    private String imageUrl;
    private String bannerUrl;

    @Indexed
    private ProjectClassification classification;

    private List<String> categories;

    @Indexed
    private List<String> tags;

    private int downloadCount;
    private int favoriteCount;

    private int downloads7d;
    private int downloads30d;
    private int downloads90d;

    private int trendScore;
    private double relevanceScore;
    private double popularScore;

    private long trendingRank;
    private long popularRank;
    private long relevanceRank;
    private boolean rankingDirty;

    private String repositoryUrl;
    private String updatedAt;
    private String createdAt;
    private String license;
    private boolean customLicenseOpenSource;

    private String lastTrendingNotification;

    private Map<String, String> links = new HashMap<>();

    private List<String> types;
    private List<String> childProjectIds;
    private boolean allowModpacks = true;
    private boolean allowComments = true;

    private boolean hmWikiEnabled = false;
    private String hmWikiSlug;
    private boolean galleryCarouselEnabled = false;

    @Indexed
    private ProjectStatus status = ProjectStatus.PUBLISHED;
    private String expiresAt;

    private LocalDateTime deletedAt;

    private String approvedBy;

    private List<ProjectRole> projectRoles = new ArrayList<>();
    private List<ProjectMember> teamMembers = new ArrayList<>();
    private List<ProjectMember> teamInvites = new ArrayList<>();

    private String pendingTransferTo;

    private List<String> galleryImages = new ArrayList<>();
    private Map<String, String> galleryImageCaptions = new HashMap<>();
    private List<Comment> comments = new ArrayList<>();
    private List<ProjectVersion> versions = new ArrayList<>();

    @Transient
    private boolean canEdit;

    @Transient
    private boolean isOwner;

    public Project() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAbout() { return about; }
    public void setAbout(String about) { this.about = about; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getBannerUrl() { return bannerUrl; }
    public void setBannerUrl(String bannerUrl) { this.bannerUrl = bannerUrl; }
    public ProjectClassification getClassification() { return classification; }
    public void setClassification(ProjectClassification classification) { this.classification = classification; }
    public List<String> getCategories() { return categories; }
    public void setCategories(List<String> categories) { this.categories = categories; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public int getDownloadCount() { return downloadCount; }
    public void setDownloadCount(int downloadCount) { this.downloadCount = downloadCount; }
    public int getFavoriteCount() { return favoriteCount; }
    public void setFavoriteCount(int favoriteCount) { this.favoriteCount = favoriteCount; }
    public int getDownloads7d() { return downloads7d; }
    public void setDownloads7d(int downloads7d) { this.downloads7d = downloads7d; }
    public int getDownloads30d() { return downloads30d; }
    public void setDownloads30d(int downloads30d) { this.downloads30d = downloads30d; }
    public int getDownloads90d() { return downloads90d; }
    public void setDownloads90d(int downloads90d) { this.downloads90d = downloads90d; }
    public int getTrendScore() { return trendScore; }
    public void setTrendScore(int trendScore) { this.trendScore = trendScore; }
    public double getRelevanceScore() { return relevanceScore; }
    public void setRelevanceScore(double relevanceScore) { this.relevanceScore = relevanceScore; }
    public double getPopularScore() { return popularScore; }
    public void setPopularScore(double popularScore) { this.popularScore = popularScore; }
    public long getTrendingRank() { return trendingRank; }
    public void setTrendingRank(long trendingRank) { this.trendingRank = trendingRank; }
    public long getPopularRank() { return popularRank; }
    public void setPopularRank(long popularRank) { this.popularRank = popularRank; }
    public long getRelevanceRank() { return relevanceRank; }
    public void setRelevanceRank(long relevanceRank) { this.relevanceRank = relevanceRank; }
    public boolean isRankingDirty() { return rankingDirty; }
    public void setRankingDirty(boolean rankingDirty) { this.rankingDirty = rankingDirty; }
    public String getRepositoryUrl() { return repositoryUrl; }
    public void setRepositoryUrl(String repositoryUrl) { this.repositoryUrl = repositoryUrl; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getLicense() { return license; }
    public void setLicense(String license) { this.license = license; }
    public boolean isCustomLicenseOpenSource() { return customLicenseOpenSource; }
    public void setCustomLicenseOpenSource(boolean customLicenseOpenSource) { this.customLicenseOpenSource = customLicenseOpenSource; }
    public Map<String, String> getLinks() { return links; }
    public void setLinks(Map<String, String> links) { this.links = links; }
    public List<String> getTypes() { return types; }
    public void setTypes(List<String> types) { this.types = types; }
    public List<String> getChildProjectIds() { return childProjectIds; }
    public void setChildProjectIds(List<String> childProjectIds) { this.childProjectIds = childProjectIds; }
    public boolean isAllowModpacks() { return allowModpacks; }
    public void setAllowModpacks(boolean allowModpacks) { this.allowModpacks = allowModpacks; }
    public boolean isAllowComments() { return allowComments; }
    public void setAllowComments(boolean allowComments) { this.allowComments = allowComments; }
    public boolean isHmWikiEnabled() { return hmWikiEnabled; }
    public void setHmWikiEnabled(boolean hmWikiEnabled) { this.hmWikiEnabled = hmWikiEnabled; }
    public String getHmWikiSlug() { return hmWikiSlug; }
    public void setHmWikiSlug(String hmWikiSlug) { this.hmWikiSlug = hmWikiSlug; }
    public boolean isGalleryCarouselEnabled() { return galleryCarouselEnabled; }
    public void setGalleryCarouselEnabled(boolean galleryCarouselEnabled) { this.galleryCarouselEnabled = galleryCarouselEnabled; }
    public ProjectStatus getStatus() { return status; }
    public void setStatus(ProjectStatus status) { this.status = status; }
    public String getExpiresAt() { return expiresAt; }
    public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    public List<ProjectRole> getProjectRoles() { return projectRoles; }
    public void setProjectRoles(List<ProjectRole> projectRoles) { this.projectRoles = projectRoles; }
    public List<ProjectMember> getTeamMembers() { return teamMembers; }
    public void setTeamMembers(List<ProjectMember> teamMembers) { this.teamMembers = teamMembers; }
    public List<ProjectMember> getTeamInvites() { return teamInvites; }
    public void setTeamInvites(List<ProjectMember> teamInvites) { this.teamInvites = teamInvites; }

    public String getPendingTransferTo() { return pendingTransferTo; }
    public void setPendingTransferTo(String pendingTransferTo) { this.pendingTransferTo = pendingTransferTo; }
    public List<String> getGalleryImages() { return galleryImages; }
    public void setGalleryImages(List<String> galleryImages) { this.galleryImages = galleryImages; }
    public Map<String, String> getGalleryImageCaptions() { return galleryImageCaptions; }
    public void setGalleryImageCaptions(Map<String, String> galleryImageCaptions) { this.galleryImageCaptions = galleryImageCaptions; }
    public List<Comment> getComments() { return comments; }
    public void setComments(List<Comment> comments) { this.comments = comments; }
    public List<ProjectVersion> getVersions() { return versions; }
    public void setVersions(List<ProjectVersion> versions) { this.versions = versions; }
    public String getLastTrendingNotification() { return lastTrendingNotification; }
    public void setLastTrendingNotification(String lastTrendingNotification) { this.lastTrendingNotification = lastTrendingNotification; }
    public boolean isCanEdit() { return canEdit; }
    public void setCanEdit(boolean canEdit) { this.canEdit = canEdit; }
    public boolean isOwner() { return isOwner; }
    public void setIsOwner(boolean isOwner) { this.isOwner = isOwner; }
}
