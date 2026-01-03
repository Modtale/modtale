package net.modtale.model.analytics;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "download_logs")
public class DownloadLog {
    @Id
    private String id;

    @Indexed
    private String projectId;

    @Indexed
    private String versionId;

    @Indexed
    private String authorId;

    @Indexed
    private LocalDateTime timestamp;

    public DownloadLog() {}

    public DownloadLog(String projectId, String versionId, String authorId) {
        this.projectId = projectId;
        this.versionId = versionId;
        this.authorId = authorId;
        this.timestamp = LocalDateTime.now();
    }

    public String getProjectId() { return projectId; }
    public String getVersionId() { return versionId; }
    public String getAuthorId() { return authorId; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}