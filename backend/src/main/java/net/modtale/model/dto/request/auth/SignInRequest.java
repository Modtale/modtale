package net.modtale.model.dto.request.auth;

import jakarta.validation.constraints.NotBlank;

public class SignInRequest {
    @NotBlank(message = "A username or email address is required before we can sign you in.")
    private String username;

    @NotBlank(message = "A password is required before we can sign you in.")
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
