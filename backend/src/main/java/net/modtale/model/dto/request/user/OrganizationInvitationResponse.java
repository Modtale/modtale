package net.modtale.model.dto.request.user;
public record OrganizationInvitationResponse(
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=64) String requestId) {}
