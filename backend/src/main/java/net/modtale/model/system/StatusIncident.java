package net.modtale.model.system;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "status_incidents")
public class StatusIncident {
    @Id
    private String id;

    private StatusIncidentKind kind;
    private StatusIncidentState state;
    private SystemStatus impact;
    private String title;
    private List<String> affectedServices = new ArrayList<>();

    @Indexed
    private LocalDateTime scheduledStart;
    private LocalDateTime scheduledEnd;
    private LocalDateTime startedAt;
    private LocalDateTime resolvedAt;

    @Indexed
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String createdByUsername;

    private List<StatusIncidentUpdate> updates = new ArrayList<>();

    public StatusIncident() {}

    public void addUpdate(StatusIncidentUpdate update) {
        if (updates == null) {
            updates = new ArrayList<>();
        }
        updates.add(update);
    }

    public boolean isClosed() {
        return state == StatusIncidentState.RESOLVED || state == StatusIncidentState.CANCELED;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public StatusIncidentKind getKind() { return kind; }
    public void setKind(StatusIncidentKind kind) { this.kind = kind; }
    public StatusIncidentState getState() { return state; }
    public void setState(StatusIncidentState state) { this.state = state; }
    public SystemStatus getImpact() { return impact; }
    public void setImpact(SystemStatus impact) { this.impact = impact; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public List<String> getAffectedServices() { return affectedServices; }
    public void setAffectedServices(List<String> affectedServices) { this.affectedServices = affectedServices; }
    public LocalDateTime getScheduledStart() { return scheduledStart; }
    public void setScheduledStart(LocalDateTime scheduledStart) { this.scheduledStart = scheduledStart; }
    public LocalDateTime getScheduledEnd() { return scheduledEnd; }
    public void setScheduledEnd(LocalDateTime scheduledEnd) { this.scheduledEnd = scheduledEnd; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getCreatedByUsername() { return createdByUsername; }
    public void setCreatedByUsername(String createdByUsername) { this.createdByUsername = createdByUsername; }
    public List<StatusIncidentUpdate> getUpdates() { return updates; }
    public void setUpdates(List<StatusIncidentUpdate> updates) { this.updates = updates; }

    public static class StatusIncidentUpdate {
        private String id = UUID.randomUUID().toString();
        private StatusIncidentState state;
        private SystemStatus impact;
        private String message;
        private LocalDateTime createdAt;
        private String createdBy;
        private String createdByUsername;

        public StatusIncidentUpdate() {}

        public StatusIncidentUpdate(
                StatusIncidentState state,
                SystemStatus impact,
                String message,
                LocalDateTime createdAt,
                String createdBy,
                String createdByUsername
        ) {
            this.state = state;
            this.impact = impact;
            this.message = message;
            this.createdAt = createdAt;
            this.createdBy = createdBy;
            this.createdByUsername = createdByUsername;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public StatusIncidentState getState() { return state; }
        public void setState(StatusIncidentState state) { this.state = state; }
        public SystemStatus getImpact() { return impact; }
        public void setImpact(SystemStatus impact) { this.impact = impact; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
        public String getCreatedByUsername() { return createdByUsername; }
        public void setCreatedByUsername(String createdByUsername) { this.createdByUsername = createdByUsername; }
    }
}
