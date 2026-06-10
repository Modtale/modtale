package net.modtale.model.dto.request.organization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import net.modtale.validation.AccountNameRules;

public class CreateOrganizationRequest {

    @NotBlank(message = "An organization name is required before we can create it.")
    @Size(min = AccountNameRules.MIN_HANDLE_LENGTH, max = AccountNameRules.MAX_HANDLE_LENGTH, message = "Organization names must be between 3 and 30 characters.")
    @Pattern(
            regexp = AccountNameRules.HANDLE_REGEX,
            message = "Organization names can only include letters, numbers, periods, underscores, and hyphens."
    )
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
