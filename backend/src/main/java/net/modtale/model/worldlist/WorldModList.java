package net.modtale.model.worldlist;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectDependency;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Document(collection = "world_mod_lists")
@CompoundIndex(name = "world_mod_lists_expires_idx", def = "{'expiresAt': 1}")
public class WorldModList {

    @MongoId(FieldType.STRING)
    private String id = UUID.randomUUID().toString();

    @Indexed
    private String ownerId;

    private String ownerUsername;
    private String title;
    private String worldName;
    private String gameVersion;
    private Instant createdAt;
    private Instant lastViewedAt;
    private Instant expiresAt;
    private int viewCount;
    private int downloadCount;
    private List<Item> mods = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public String getOwnerUsername() { return ownerUsername; }
    public void setOwnerUsername(String ownerUsername) { this.ownerUsername = ownerUsername; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getWorldName() { return worldName; }
    public void setWorldName(String worldName) { this.worldName = worldName; }

    public String getGameVersion() { return gameVersion; }
    public void setGameVersion(String gameVersion) { this.gameVersion = gameVersion; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getLastViewedAt() { return lastViewedAt; }
    public void setLastViewedAt(Instant lastViewedAt) { this.lastViewedAt = lastViewedAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public int getViewCount() { return viewCount; }
    public void setViewCount(int viewCount) { this.viewCount = Math.max(0, viewCount); }

    public int getDownloadCount() { return downloadCount; }
    public void setDownloadCount(int downloadCount) { this.downloadCount = Math.max(0, downloadCount); }

    public List<Item> getMods() { return mods; }
    public void setMods(List<Item> mods) { this.mods = mods == null ? new ArrayList<>() : new ArrayList<>(mods); }

    public static class Item {
        private String id = UUID.randomUUID().toString();
        private String modId;
        private String projectId;
        private String slug;
        private String title;
        private String authorId;
        private String author;
        private String description;
        private String versionNumber;
        private ProjectClassification classification;
        private ProjectDependency.Source source = ProjectDependency.Source.MODTALE;
        private String externalId;
        private String externalUrl;
        private String icon;
        private String bannerUrl;
        private int downloadCount;
        private int favoriteCount;
        private String updatedAt;
        private String fileUrl;
        private boolean downloadable;
        private String unavailableReason;

        public String getId() { return id; }
        public void setId(String id) { this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id; }

        public String getModId() { return modId; }
        public void setModId(String modId) { this.modId = modId; }

        public String getProjectId() { return projectId; }
        public void setProjectId(String projectId) { this.projectId = projectId; }

        public String getSlug() { return slug; }
        public void setSlug(String slug) { this.slug = slug; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getAuthorId() { return authorId; }
        public void setAuthorId(String authorId) { this.authorId = authorId; }

        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getVersionNumber() { return versionNumber; }
        public void setVersionNumber(String versionNumber) { this.versionNumber = versionNumber; }

        public ProjectClassification getClassification() { return classification; }
        public void setClassification(ProjectClassification classification) { this.classification = classification; }

        public ProjectDependency.Source getSource() { return source == null ? ProjectDependency.Source.MODTALE : source; }
        public void setSource(ProjectDependency.Source source) { this.source = source == null ? ProjectDependency.Source.MODTALE : source; }

        public String getExternalId() { return externalId; }
        public void setExternalId(String externalId) { this.externalId = externalId; }

        public String getExternalUrl() { return externalUrl; }
        public void setExternalUrl(String externalUrl) { this.externalUrl = externalUrl; }

        public String getIcon() { return icon; }
        public void setIcon(String icon) { this.icon = icon; }

        public String getBannerUrl() { return bannerUrl; }
        public void setBannerUrl(String bannerUrl) { this.bannerUrl = bannerUrl; }

        public int getDownloadCount() { return downloadCount; }
        public void setDownloadCount(int downloadCount) { this.downloadCount = Math.max(0, downloadCount); }

        public int getFavoriteCount() { return favoriteCount; }
        public void setFavoriteCount(int favoriteCount) { this.favoriteCount = Math.max(0, favoriteCount); }

        public String getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

        public String getFileUrl() { return fileUrl; }
        public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

        public boolean isDownloadable() { return downloadable; }
        public void setDownloadable(boolean downloadable) { this.downloadable = downloadable; }

        public String getUnavailableReason() { return unavailableReason; }
        public void setUnavailableReason(String unavailableReason) { this.unavailableReason = unavailableReason; }
    }
}
