package net.modtale.model.dto.request.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ResolveReportRequest {

    @NotBlank(message = "A report status is required.")
    @Pattern(
            regexp = "(?i)OPEN|RESOLVED|DISMISSED",
            message = "Report statuses must be OPEN, RESOLVED, or DISMISSED."
    )
    private String status;

    @Size(max = 5000, message = "Resolution notes must be 5,000 characters or fewer.")
    private String note;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
