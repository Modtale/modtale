package net.modtale.model.dto.request.project;

import jakarta.validation.constraints.NotBlank;

public class UpdateProjectMemberRoleRequest {

    @NotBlank(message = "A replacement role is required before we can update this contributor.")
    private String roleId;

    public String getRoleId() { return roleId; }
    public void setRoleId(String roleId) { this.roleId = roleId; }
}
