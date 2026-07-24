package net.modtale.model.dto.request.admin;

import java.util.Set;
import net.modtale.model.user.AdminPermission;

public class UpdateAdminPermissionsRequest {
    private Set<AdminPermission> permissions;

    public Set<AdminPermission> getPermissions() {
        return permissions;
    }

    public void setPermissions(Set<AdminPermission> permissions) {
        this.permissions = permissions;
    }
}
