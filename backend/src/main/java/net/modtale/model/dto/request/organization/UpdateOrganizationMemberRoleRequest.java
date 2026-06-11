package net.modtale.model.dto.request.organization;

import jakarta.validation.constraints.NotBlank;

public class UpdateOrganizationMemberRoleRequest {

    @NotBlank(message = "A replacement role is required before we can update this member.")
    private String roleId;

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }
}
