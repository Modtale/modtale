package net.modtale.model.dto.request.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class BanEmailRequest {
    @NotBlank(message = "An email address is required before it can be banned.")
    @Email(message = "Provide a valid email address.")
    private String email;

    @NotBlank(message = "A reason is required before we can ban an email address.")
    private String reason;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
