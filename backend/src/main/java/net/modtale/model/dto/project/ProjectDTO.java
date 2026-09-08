package net.modtale.model.dto.project;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProjectDTO {
    private String id;
    private String slug;
    private String title;
    private String about;
    private String description;
    private String authorId;
    private String author;
    private String imageUrl;
    private String bannerUrl;
    private ProjectClassification classification;
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
    private boolean customLicenseOpenSource;
    private String lastTrendingNotification;
    private Map<String, String> links;
    private List<String> types;
    private List<String> childProjectIds;
    private boolean allowModpacks;
    private boolean allowComments;
    private boolean hmWikiEnabled;
    private String hmWikiSlug;
    private boolean galleryCarouselEnabled;
    private ProjectStatus status;
    private String expiresAt;

    private List<Project.ProjectRole> projectRoles;
    private List<Project.ProjectMember> teamMembers;
    private List<Project.ProjectMember> teamInvites;

    private List<String> galleryImages;
    private Map<String, String> galleryImageCaptions;
    private List<ProjectCommentDTO> comments;
    private List<ProjectVersionDTO> versions;
    private boolean canEdit;
    private boolean isOwner;

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
    public boolean isCustomLicenseOpenSource() { return customLicenseOpenSource; }
    public void setCustomLicenseOpenSource(boolean customLicenseOpenSource) { this.customLicenseOpenSource = customLicenseOpenSource; }
    public String getLastTrendingNotification() { return lastTrendingNotification; }
    public void setLastTrendingNotification(String lastTrendingNotification) { this.lastTrendingNotification = lastTrendingNotification; }
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

    public List<Project.ProjectRole> getProjectRoles() { return projectRoles; }
    public void setProjectRoles(List<Project.ProjectRole> projectRoles) { this.projectRoles = projectRoles; }
    public List<Project.ProjectMember> getTeamMembers() { return teamMembers; }
    public void setTeamMembers(List<Project.ProjectMember> teamMembers) { this.teamMembers = teamMembers; }
    public List<Project.ProjectMember> getTeamInvites() { return teamInvites; }
    public void setTeamInvites(List<Project.ProjectMember> teamInvites) { this.teamInvites = teamInvites; }

    public List<String> getGalleryImages() { return galleryImages; }
    public void setGalleryImages(List<String> galleryImages) { this.galleryImages = galleryImages; }
    public Map<String, String> getGalleryImageCaptions() { return galleryImageCaptions; }
    public void setGalleryImageCaptions(Map<String, String> galleryImageCaptions) { this.galleryImageCaptions = galleryImageCaptions; }
    public List<ProjectCommentDTO> getComments() { return comments; }
    public void setComments(List<ProjectCommentDTO> comments) { this.comments = comments; }
    public List<ProjectVersionDTO> getVersions() { return versions; }
    public void setVersions(List<ProjectVersionDTO> versions) { this.versions = versions; }
    public boolean isCanEdit() { return canEdit; }
    public void setCanEdit(boolean canEdit) { this.canEdit = canEdit; }
    public boolean isOwner() { return isOwner; }
    public void setIsOwner(boolean isOwner) { this.isOwner = isOwner; }
}
