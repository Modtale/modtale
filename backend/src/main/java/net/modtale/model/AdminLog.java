package net.modtale.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "admin_logs")
public class AdminLog {
    @Id
    private String id;
    private String adminUsername;
    private String action;
    private String targetId;
    private String targetType;
    private String details;
    private LocalDateTime timestamp;

    public AdminLog() {}

    public AdminLog(String adminUsername, String action, String targetId, String targetType, String details) {
        this.adminUsername = adminUsername;
        this.action = action;
        this.targetId = targetId;
        this.targetType = targetType;
        this.details = details;
        this.timestamp = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAdminUsername() { return adminUsername; }
    public void setAdminUsername(String adminUsername) { this.adminUsername = adminUsername; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}