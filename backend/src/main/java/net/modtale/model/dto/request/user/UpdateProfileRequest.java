package net.modtale.model.dto.request.user;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import net.modtale.validation.AccountNameRules;

public class UpdateProfileRequest {
    @Size(max = 300, message = "Your bio cannot exceed 300 characters.")
    private String bio;

    @Size(min = AccountNameRules.MIN_HANDLE_LENGTH, max = AccountNameRules.MAX_HANDLE_LENGTH, message = "Username must be between 3 and 30 characters.")
    @Pattern(
            regexp = AccountNameRules.HANDLE_REGEX,
            message = "Username can only contain letters, numbers, hyphens, underscores, and periods."
    )
    private String username;

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
