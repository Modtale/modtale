package net.modtale.model.dto.request.admin;

import jakarta.validation.constraints.NotBlank;

public class RejectReasonRequest {
    @NotBlank(message = "A reason is required before we can complete this moderation action.")
    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
