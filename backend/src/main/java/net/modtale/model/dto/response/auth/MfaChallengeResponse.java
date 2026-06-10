package net.modtale.model.dto.response.auth;

public record MfaChallengeResponse(boolean mfaRequired, String preAuthToken) {
}
