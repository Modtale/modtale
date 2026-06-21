package net.modtale.model.dto.request.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import net.modtale.model.user.Report;

public class CreateReportRequest {

    @NotBlank(message = "A report target is required.")
    private String targetId;

    @NotNull(message = "A report target type is required.")
    private Report.TargetType targetType;

    @NotBlank(message = "A report reason is required.")
    @Size(max = 200, message = "Report reasons must be 200 characters or fewer.")
    private String reason;

    @Size(max = 5000, message = "Report descriptions must be 5,000 characters or fewer.")
    private String description;

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public Report.TargetType getTargetType() {
        return targetType;
    }

    public void setTargetType(Report.TargetType targetType) {
        this.targetType = targetType;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
