package net.modtale.model.admin;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "banned_emails")
public class BannedEmail {
    @Id
    private String id;

    @Indexed(unique = true)
    private String email;

    private String reason;
    private String bannedBy;
    private LocalDateTime bannedAt;

    public BannedEmail() {}

    public BannedEmail(String email, String reason, String bannedBy) {
        this.email = email;
        this.reason = reason;
        this.bannedBy = bannedBy;
        this.bannedAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getBannedBy() { return bannedBy; }
    public void setBannedBy(String bannedBy) { this.bannedBy = bannedBy; }
    public LocalDateTime getBannedAt() { return bannedAt; }
    public void setBannedAt(LocalDateTime bannedAt) { this.bannedAt = bannedAt; }
}