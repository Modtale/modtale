package net.modtale.model.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class VerifyMfaRequest {
    @NotBlank(message = "A two-factor authentication code is required.")
    @Pattern(regexp = "\\d{6}", message = "Two-factor authentication codes must be exactly 6 digits.")
    private String code;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
