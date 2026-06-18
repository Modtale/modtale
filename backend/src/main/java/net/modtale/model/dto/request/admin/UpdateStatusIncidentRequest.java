package net.modtale.model.dto.request.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import net.modtale.model.system.StatusIncidentState;
import net.modtale.model.system.SystemStatus;

public class UpdateStatusIncidentRequest {
    @NotNull(message = "Incident state is required.")
    private StatusIncidentState state;

    @NotNull(message = "Incident impact is required.")
    private SystemStatus impact;

    @NotBlank(message = "Update message is required.")
    private String message;

    private LocalDateTime scheduledEnd;

    public StatusIncidentState getState() { return state; }
    public void setState(StatusIncidentState state) { this.state = state; }
    public SystemStatus getImpact() { return impact; }
    public void setImpact(SystemStatus impact) { this.impact = impact; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public LocalDateTime getScheduledEnd() { return scheduledEnd; }
    public void setScheduledEnd(LocalDateTime scheduledEnd) { this.scheduledEnd = scheduledEnd; }
}
