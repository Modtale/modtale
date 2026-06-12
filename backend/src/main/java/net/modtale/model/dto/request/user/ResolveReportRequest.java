package net.modtale.model.dto.request.user;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import net.modtale.model.user.Report;

public class ResolveReportRequest {

    @NotNull(message = "A report status is required.")
    private Report.ReportStatus status;

    @Size(max = 5000, message = "Resolution notes must be 5,000 characters or fewer.")
    private String note;

    public Report.ReportStatus getStatus() {
        return status;
    }

    public void setStatus(Report.ReportStatus status) {
        this.status = status;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
