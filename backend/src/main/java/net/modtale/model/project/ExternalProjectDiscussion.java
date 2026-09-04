package net.modtale.model.project;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "external_project_discussions")
@CompoundIndex(name = "provider_project_unique", def = "{'provider': 1, 'externalProjectId': 1}", unique = true)
public class ExternalProjectDiscussion {
    @Id
    private String id;
    private String provider;
    private String externalProjectId;
    private List<Comment> comments = new ArrayList<>();
    private LocalDateTime updatedAt;

    public ExternalProjectDiscussion() {}

    public ExternalProjectDiscussion(String provider, String externalProjectId) {
        this.id = provider + ":" + externalProjectId;
        this.provider = provider;
        this.externalProjectId = externalProjectId;
        this.updatedAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getExternalProjectId() { return externalProjectId; }
    public void setExternalProjectId(String externalProjectId) { this.externalProjectId = externalProjectId; }
    public List<Comment> getComments() { return comments; }
    public void setComments(List<Comment> comments) { this.comments = comments; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
