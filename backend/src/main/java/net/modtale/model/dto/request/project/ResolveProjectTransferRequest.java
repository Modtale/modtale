package net.modtale.model.dto.request.project;

import jakarta.validation.constraints.NotNull;

public class ResolveProjectTransferRequest {

    @NotNull(message = "A transfer response is required.")
    private Boolean accept;

    public Boolean getAccept() { return accept; }
    public void setAccept(Boolean accept) { this.accept = accept; }
}
