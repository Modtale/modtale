package net.modtale.model.dto.request.organization;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;
import net.modtale.model.user.ApiKey;

public class OrganizationRoleRequest {

    @Size(max = 40, message = "Organization role names must be 40 characters or fewer.")
    private String name;

    @Pattern(
            regexp = "^#?[0-9a-fA-F]{6}$",
            message = "Role colors must be a valid 6-digit hex code."
    )
    private String color;

    private Set<ApiKey.ApiPermission> permissions;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public Set<ApiKey.ApiPermission> getPermissions() { return permissions; }
    public void setPermissions(Set<ApiKey.ApiPermission> permissions) { this.permissions = permissions; }
}
