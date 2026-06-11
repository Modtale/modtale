package net.modtale.model.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class MfaLoginRequest {
    @NotBlank(message = "A pre-authentication token is required.")
    private String pre_auth_token;

    @NotBlank(message = "A two-factor authentication code is required.")
    @Pattern(regexp = "\\d{6}", message = "Two-factor authentication codes must be exactly 6 digits.")
    private String code;

    public String getPre_auth_token() {
        return pre_auth_token;
    }

    public void setPre_auth_token(String pre_auth_token) {
        this.pre_auth_token = pre_auth_token;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
