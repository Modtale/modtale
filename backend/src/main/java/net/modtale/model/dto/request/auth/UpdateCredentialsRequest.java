package net.modtale.model.dto.request.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdateCredentialsRequest {
    @NotBlank(message = "An email address is required before we can update your credentials.")
    @Email(message = "Provide a valid email address.")
    private String email;

    @NotBlank(message = "A password is required before we can update your credentials.")
    @Size(min = 6, message = "Passwords must be at least 6 characters long.")
    private String password;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
