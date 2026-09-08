package net.modtale.launcher.model.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SignInResponse(String status, boolean mfaRequired, String preAuthToken) {
}
