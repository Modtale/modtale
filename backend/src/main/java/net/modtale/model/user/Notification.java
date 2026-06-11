package net.modtale.model.user;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Document(collection = "notifications")
public class Notification {
    @Id
    private String id;

    @Indexed
    private String userId;

    private String title;
    private String message;
    private String link;
    private String iconUrl;
    private boolean isRead;

    private NotificationType type;
    private Map<String, String> metadata = new HashMap<>();

    @Indexed(expireAfter = "30d")
    private LocalDateTime createdAt;

    public Notification() {}

    public Notification(String userId, String title, String message, URI link, String iconUrl) {
        this(userId, title, message, link, iconUrl, NotificationType.INFO, new HashMap<>());
    }

    public Notification(String userId, String title, String message, URI link, String iconUrl, NotificationType type, Map<String, String> metadata) {
        this.userId = userId;
        this.title = title;
        this.message = message;
        this.link = link.toString();
        this.iconUrl = iconUrl;
        this.type = type != null ? type : NotificationType.INFO;
        this.metadata = metadata != null ? metadata : new HashMap<>();
        this.isRead = false;
        this.createdAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }
    public String getIconUrl() { return iconUrl; }
    public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }
    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public NotificationType getType() { return type; }
    public void setType(NotificationType type) { this.type = type; }
    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
}