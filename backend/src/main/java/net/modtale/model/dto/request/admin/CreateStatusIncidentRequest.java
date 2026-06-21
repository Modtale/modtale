package net.modtale.model.dto.request.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import net.modtale.model.system.StatusIncidentKind;
import net.modtale.model.system.StatusIncidentState;
import net.modtale.model.system.SystemStatus;

public class CreateStatusIncidentRequest {
    @NotNull(message = "Incident kind is required.")
    private StatusIncidentKind kind;

    @NotBlank(message = "Incident title is required.")
    private String title;

    @NotNull(message = "Incident impact is required.")
    private SystemStatus impact;

    private StatusIncidentState state;
    private List<String> affectedServices;
    private LocalDateTime scheduledStart;
    private LocalDateTime scheduledEnd;

    @NotBlank(message = "Initial update is required.")
    private String message;

    public StatusIncidentKind getKind() { return kind; }
    public void setKind(StatusIncidentKind kind) { this.kind = kind; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public SystemStatus getImpact() { return impact; }
    public void setImpact(SystemStatus impact) { this.impact = impact; }
    public StatusIncidentState getState() { return state; }
    public void setState(StatusIncidentState state) { this.state = state; }
    public List<String> getAffectedServices() { return affectedServices; }
    public void setAffectedServices(List<String> affectedServices) { this.affectedServices = affectedServices; }
    public LocalDateTime getScheduledStart() { return scheduledStart; }
    public void setScheduledStart(LocalDateTime scheduledStart) { this.scheduledStart = scheduledStart; }
    public LocalDateTime getScheduledEnd() { return scheduledEnd; }
    public void setScheduledEnd(LocalDateTime scheduledEnd) { this.scheduledEnd = scheduledEnd; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
