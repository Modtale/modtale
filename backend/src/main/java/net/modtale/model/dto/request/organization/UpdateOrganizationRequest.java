package net.modtale.model.dto.request.organization;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import net.modtale.validation.AccountNameRules;

public class UpdateOrganizationRequest {

    @Size(max = AccountNameRules.MAX_HANDLE_LENGTH, message = "Organization names must be 30 characters or fewer.")
    private String displayName;

    @Size(max = AccountNameRules.MAX_HANDLE_LENGTH, message = "Organization names must be 30 characters or fewer.")
    @Pattern(
            regexp = "^$|" + AccountNameRules.HANDLE_REGEX,
            message = "Organization names can only include letters, numbers, periods, underscores, and hyphens."
    )
    private String name;

    @Size(max = 5000, message = "Organization bios must be 5,000 characters or fewer.")
    private String bio;

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }
}
