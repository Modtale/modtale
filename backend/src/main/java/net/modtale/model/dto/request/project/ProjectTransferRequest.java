package net.modtale.model.dto.request.project;

import jakarta.validation.constraints.NotBlank;

public class ProjectTransferRequest {

    @NotBlank(message = "A new owner is required before we can transfer this project.")
    private String userId;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
}
