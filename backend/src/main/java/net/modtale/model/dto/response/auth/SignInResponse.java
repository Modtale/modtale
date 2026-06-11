package net.modtale.model.dto.response.auth;

public record SignInResponse(String status, boolean mfaRequired, String preAuthToken) {
}
