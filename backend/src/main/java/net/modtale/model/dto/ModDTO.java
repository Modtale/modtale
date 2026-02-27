package net.modtale.model.dto;

import net.modtale.model.resources.Comment;
import net.modtale.model.resources.Mod;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ModDTO {
    private String id;
    private String slug;
    private String title;
    private String about;
    private String description;
    private String authorId;
    private String author;
    private String imageUrl;
    private String bannerUrl;
    private String classification;
    private List<String> categories;
    private List<String> tags;
    private int downloadCount;
    private int favoriteCount;
    private int trendScore;
    private double relevanceScore;
    private double popularScore;
    private String repositoryUrl;
    private String updatedAt;
    private String createdAt;
    private String license;
    private String lastTrendingNotification;
    private Map<String, String> links;
    private List<String> types;
    private List<String> childProjectIds;
    private List<String> modIds;
    private List<String> modjamIds;
    private boolean allowModpacks;
    private boolean allowComments;
    private String status;
    private String expiresAt;
    private List<String> contributors;
    private List<String> galleryImages;
    private List<Comment> comments;
    private List<ModVersionDTO> versions;
    private boolean canEdit;
    private boolean isOwner;

    public static ModDTO fromEntity(Mod mod) {
        if (mod == null) return null;
        ModDTO dto = new ModDTO();
        dto.setId(mod.getId());
        dto.setSlug(mod.getSlug());
        dto.setTitle(mod.getTitle());
        dto.setAbout(mod.getAbout());
        dto.setDescription(mod.getDescription());
        dto.setAuthorId(mod.getAuthorId());
        dto.setAuthor(mod.getAuthor());
        dto.setImageUrl(mod.getImageUrl());
        dto.setBannerUrl(mod.getBannerUrl());
        dto.setClassification(mod.getClassification());
        dto.setCategories(mod.getCategories());
        dto.setTags(mod.getTags());
        dto.setDownloadCount(mod.getDownloadCount());
        dto.setFavoriteCount(mod.getFavoriteCount());
        dto.setTrendScore(mod.getTrendScore());
        dto.setRelevanceScore(mod.getRelevanceScore());
        dto.setPopularScore(mod.getPopularScore());
        dto.setRepositoryUrl(mod.getRepositoryUrl());
        dto.setUpdatedAt(mod.getUpdatedAt());
        dto.setCreatedAt(mod.getCreatedAt());
        dto.setLicense(mod.getLicense());
        dto.setLastTrendingNotification(mod.getLastTrendingNotification());
        dto.setLinks(mod.getLinks());
        dto.setTypes(mod.getTypes());
        dto.setChildProjectIds(mod.getChildProjectIds());
        dto.setModIds(mod.getModIds());
        dto.setModjamIds(mod.getModjamIds());
        dto.setAllowModpacks(mod.isAllowModpacks());
        dto.setAllowComments(mod.isAllowComments());
        dto.setStatus(mod.getStatus());
        dto.setExpiresAt(mod.getExpiresAt());
        dto.setContributors(mod.getContributors());
        dto.setGalleryImages(mod.getGalleryImages());
        dto.setComments(mod.getComments() != null ? mod.getComments() : new ArrayList<>());

        if (mod.getVersions() != null) {
            dto.setVersions(mod.getVersions().stream()
                    .map(ModVersionDTO::fromEntity)
                    .collect(Collectors.toList()));
        } else {
            dto.setVersions(new ArrayList<>());
        }

        dto.setCanEdit(mod.isCanEdit());
        dto.setIsOwner(mod.isOwner());

        return dto;
    }

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
    public String getLastTrendingNotification() { return lastTrendingNotification; }
    public void setLastTrendingNotification(String lastTrendingNotification) { this.lastTrendingNotification = lastTrendingNotification; }
    public Map<String, String> getLinks() { return links; }
    public void setLinks(Map<String, String> links) { this.links = links; }
    public List<String> getTypes() { return types; }
    public void setTypes(List<String> types) { this.types = types; }
    public List<String> getChildProjectIds() { return childProjectIds; }
    public void setChildProjectIds(List<String> childProjectIds) { this.childProjectIds = childProjectIds; }
    public List<String> getModIds() { return modIds; }
    public void setModIds(List<String> modIds) { this.modIds = modIds; }
    public List<String> getModjamIds() { return modjamIds; }
    public void setModjamIds(List<String> modjamIds) { this.modjamIds = modjamIds; }
    public boolean isAllowModpacks() { return allowModpacks; }
    public void setAllowModpacks(boolean allowModpacks) { this.allowModpacks = allowModpacks; }
    public boolean isAllowComments() { return allowComments; }
    public void setAllowComments(boolean allowComments) { this.allowComments = allowComments; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getExpiresAt() { return expiresAt; }
    public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }
    public List<String> getContributors() { return contributors; }
    public void setContributors(List<String> contributors) { this.contributors = contributors; }
    public List<String> getGalleryImages() { return galleryImages; }
    public void setGalleryImages(List<String> galleryImages) { this.galleryImages = galleryImages; }
    public List<Comment> getComments() { return comments; }
    public void setComments(List<Comment> comments) { this.comments = comments; }
    public List<ModVersionDTO> getVersions() { return versions; }
    public void setVersions(List<ModVersionDTO> versions) { this.versions = versions; }
    public boolean isCanEdit() { return canEdit; }
    public void setCanEdit(boolean canEdit) { this.canEdit = canEdit; }
    public boolean isOwner() { return isOwner; }
    public void setIsOwner(boolean isOwner) { this.isOwner = isOwner; }
}