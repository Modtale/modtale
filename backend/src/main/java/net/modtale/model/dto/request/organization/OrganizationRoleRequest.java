package net.modtale.model.dto.request.organization;

import net.modtale.model.user.ApiKey;

import java.util.Set;

public class OrganizationRoleRequest {
    private String name;
    private String color;
    private Set<ApiKey.ApiPermission> permissions;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public Set<ApiKey.ApiPermission> getPermissions() { return permissions; }
    public void setPermissions(Set<ApiKey.ApiPermission> permissions) { this.permissions = permissions; }
}
