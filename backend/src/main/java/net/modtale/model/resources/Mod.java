package net.modtale.model.resources;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

@Document(collection = "projects")
@JsonInclude(JsonInclude.Include.NON_NULL)
@CompoundIndexes({
        @CompoundIndex(name = "status_class_downloads_idx", def = "{'status': 1, 'classification': 1, 'downloadCount': -1}"),
        @CompoundIndex(name = "status_class_favorites_idx", def = "{'status': 1, 'classification': 1, 'favoriteCount': -1}"),
        @CompoundIndex(name = "status_class_updated_idx", def = "{'status': 1, 'classification': 1, 'updatedAt': -1}"),
        @CompoundIndex(name = "status_class_created_idx", def = "{'status': 1, 'classification': 1, 'createdAt': -1}"),

        @CompoundIndex(name = "status_tags_downloads_idx", def = "{'status': 1, 'tags': 1, 'downloadCount': -1}"),

        @CompoundIndex(name = "status_expires_idx", def = "{'status': 1, 'expiresAt': 1}"),
        @CompoundIndex(name = "deleted_at_idx", def = "{'deletedAt': 1}"),

        @CompoundIndex(name = "trend_score_idx", def = "{'trendScore': -1}"),
        @CompoundIndex(name = "relevance_score_idx", def = "{'relevanceScore': -1}"),
        @CompoundIndex(name = "popular_score_idx", def = "{'popularScore': -1}")
})
public class Mod {
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
    private String classification;

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

    private String repositoryUrl;
    private String updatedAt;
    private String createdAt;
    private String license;

    private String lastTrendingNotification;

    private Map<String, String> links = new HashMap<>();

    private List<String> types;
    private List<String> childProjectIds;

    private List<String> modIds;
    private boolean allowModpacks = true;
    private boolean allowComments = true;

    @Indexed
    private String status = "PUBLISHED";
    private String expiresAt;

    private LocalDateTime deletedAt;

    private String approvedBy;

    private List<String> contributors = new ArrayList<>();
    private List<String> pendingInvites = new ArrayList<>();

    private String pendingTransferTo;

    private List<String> galleryImages = new ArrayList<>();

    private List<Comment> comments = new ArrayList<>();

    private List<ModVersion> versions = new ArrayList<>();

    @Transient
    private boolean canEdit;

    @Transient
    private boolean isOwner;

    public Mod() {}

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

    public String getClassification() { return classification; }
    public void setClassification(String classification) { this.classification = classification; }
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

    public String getRepositoryUrl() { return repositoryUrl; }
    public void setRepositoryUrl(String repositoryUrl) { this.repositoryUrl = repositoryUrl; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getLicense() { return license; }
    public void setLicense(String license) { this.license = license; }

    public Map<String, String> getLinks() { return links; }
    public void setLinks(Map<String, String> links) { this.links = links; }

    public List<String> getTypes() { return types; }
    public void setTypes(List<String> types) { this.types = types; }
    public List<String> getChildProjectIds() { return childProjectIds; }
    public void setChildProjectIds(List<String> childProjectIds) { this.childProjectIds = childProjectIds; }

    public List<String> getModIds() { return modIds; }
    public void setModIds(List<String> modIds) { this.modIds = modIds; }

    public boolean isAllowModpacks() { return allowModpacks; }
    public void setAllowModpacks(boolean allowModpacks) { this.allowModpacks = allowModpacks; }

    public boolean isAllowComments() { return allowComments; }
    public void setAllowComments(boolean allowComments) { this.allowComments = allowComments; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getExpiresAt() { return expiresAt; }
    public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    public List<String> getContributors() { return contributors; }
    public void setContributors(List<String> contributors) { this.contributors = contributors; }
    public List<String> getPendingInvites() { return pendingInvites; }
    public void setPendingInvites(List<String> pendingInvites) { this.pendingInvites = pendingInvites; }

    public String getPendingTransferTo() { return pendingTransferTo; }
    public void setPendingTransferTo(String pendingTransferTo) { this.pendingTransferTo = pendingTransferTo; }

    public List<String> getGalleryImages() { return galleryImages; }
    public void setGalleryImages(List<String> galleryImages) { this.galleryImages = galleryImages; }

    public List<Comment> getComments() { return comments; }
    public void setComments(List<Comment> comments) { this.comments = comments; }

    public List<ModVersion> getVersions() { return versions; }
    public void setVersions(List<ModVersion> versions) { this.versions = versions; }

    public String getLastTrendingNotification() { return lastTrendingNotification; }
    public void setLastTrendingNotification(String lastTrendingNotification) { this.lastTrendingNotification = lastTrendingNotification; }

    public boolean isCanEdit() { return canEdit; }
    public void setCanEdit(boolean canEdit) { this.canEdit = canEdit; }

    public boolean isOwner() { return isOwner; }
    public void setIsOwner(boolean isOwner) { this.isOwner = isOwner; }
}