package net.modtale.model.dto.request.project;

public record ProjectInvitationResponse(
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=64) String requestId) {}
