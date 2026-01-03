package net.modtale.model.resources;

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

@Document(collection = "projects")
@CompoundIndexes({
        @CompoundIndex(name = "class_tags_rating_idx", def = "{'classification': 1, 'tags': 1, 'rating': -1}"),
        @CompoundIndex(name = "class_tags_downloads_idx", def = "{'classification': 1, 'tags': 1, 'downloadCount': -1}"),
        @CompoundIndex(name = "class_updated_idx", def = "{'classification': 1, 'updatedAt': -1}"),
        @CompoundIndex(name = "status_expires_idx", def = "{'status': 1, 'expiresAt': 1}")
})
public class Mod {
    @Id
    private String id;

    @Indexed
    private String title;

    private String about;
    private String description;

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
    private double rating;

    private String category;
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

    @Indexed
    private String status = "PUBLISHED";
    private String expiresAt;

    private List<String> contributors = new ArrayList<>();
    private List<String> pendingInvites = new ArrayList<>();

    private String pendingTransferTo;

    private List<String> galleryImages = new ArrayList<>();
    private List<Review> reviews = new ArrayList<>();
    private List<ModVersion> versions = new ArrayList<>();

    @Transient
    private boolean canEdit;

    @Transient
    private boolean isOwner;

    public Mod() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAbout() { return about; }
    public void setAbout(String about) { this.about = about; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
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
    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

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

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getExpiresAt() { return expiresAt; }
    public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }

    public List<String> getContributors() { return contributors; }
    public void setContributors(List<String> contributors) { this.contributors = contributors; }
    public List<String> getPendingInvites() { return pendingInvites; }
    public void setPendingInvites(List<String> pendingInvites) { this.pendingInvites = pendingInvites; }

    public String getPendingTransferTo() { return pendingTransferTo; }
    public void setPendingTransferTo(String pendingTransferTo) { this.pendingTransferTo = pendingTransferTo; }

    public List<String> getGalleryImages() { return galleryImages; }
    public void setGalleryImages(List<String> galleryImages) { this.galleryImages = galleryImages; }
    public List<Review> getReviews() { return reviews; }
    public void setReviews(List<Review> reviews) { this.reviews = reviews; }
    public List<ModVersion> getVersions() { return versions; }
    public void setVersions(List<ModVersion> versions) { this.versions = versions; }

    public String getLastTrendingNotification() { return lastTrendingNotification; }
    public void setLastTrendingNotification(String lastTrendingNotification) { this.lastTrendingNotification = lastTrendingNotification; }

    public boolean isCanEdit() { return canEdit; }
    public void setCanEdit(boolean canEdit) { this.canEdit = canEdit; }

    public boolean isOwner() { return isOwner; }
    public void setIsOwner(boolean isOwner) { this.isOwner = isOwner; }
}