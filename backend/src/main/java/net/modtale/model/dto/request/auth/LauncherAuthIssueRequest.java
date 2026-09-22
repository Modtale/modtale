package net.modtale.model.dto.request.auth;

import jakarta.validation.constraints.NotBlank;

public class LauncherAuthIssueRequest {

    @NotBlank(message = "A launcher callback URL is required.")
    private String redirectUri;

    private String state;

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }
}
