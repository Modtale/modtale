package net.modtale.model.dto.request.project;

import jakarta.validation.constraints.NotNull;

public class ResolveProjectTransferRequest {

    @NotNull(message = "A transfer response is required.")
    private Boolean accept;
    @jakarta.validation.constraints.NotBlank(message = "The transfer request identity is required.")
    @jakarta.validation.constraints.Size(max = 64)
    private String requestId;
    public String getRequestId() { return requestId; }
    public void setRequestId(String value) { requestId = value; }

    public Boolean getAccept() { return accept; }
    public void setAccept(Boolean accept) { this.accept = accept; }
}
