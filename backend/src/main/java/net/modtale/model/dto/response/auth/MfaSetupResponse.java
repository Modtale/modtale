package net.modtale.model.dto.response.auth;

public record MfaSetupResponse(String secret, String qrCode) {
}
