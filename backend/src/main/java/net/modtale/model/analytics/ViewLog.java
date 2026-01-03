package net.modtale.model.analytics;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "view_logs")
public class ViewLog {
    @Id
    private String id;

    @Indexed
    private String projectId;

    @Indexed
    private String authorId;

    @Indexed
    private LocalDateTime timestamp;

    public ViewLog() {}

    public ViewLog(String projectId, String authorId) {
        this.projectId = projectId;
        this.authorId = authorId;
        this.timestamp = LocalDateTime.now();
    }

    public String getProjectId() { return projectId; }
    public String getAuthorId() { return authorId; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}