package net.modtale.model.dto.response.auth;

public record LauncherAuthIssueResponse(String code, String redirectUri, String state, int expiresIn) {
}
