package net.modtale.model.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ResetPasswordRequest {
    @NotBlank(message = "A password reset token is required.")
    private String token;

    @NotBlank(message = "A new password is required before we can reset it.")
    @Size(min = 6, message = "New passwords must be at least 6 characters long.")
    private String password;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
