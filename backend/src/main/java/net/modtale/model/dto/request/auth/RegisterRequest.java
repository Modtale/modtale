package net.modtale.model.dto.request.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import net.modtale.validation.AccountNameRules;

public class RegisterRequest {
    @NotBlank(message = "A username is required before we can create an account.")
    @Size(min = AccountNameRules.MIN_HANDLE_LENGTH, max = AccountNameRules.MAX_HANDLE_LENGTH, message = "Usernames must be between 3 and 30 characters.")
    @Pattern(
            regexp = AccountNameRules.HANDLE_REGEX,
            message = "Username can only contain letters, numbers, hyphens, underscores, and periods."
    )
    private String username;

    @NotBlank(message = "An email address is required before we can create an account.")
    @Email(message = "Provide a valid email address.")
    private String email;

    @NotBlank(message = "A password is required before we can create an account.")
    @Size(min = 6, message = "Passwords must be at least 6 characters long.")
    private String password;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
