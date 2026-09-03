package net.modtale.model.dto.request.project;

import jakarta.validation.constraints.NotBlank;

public class ProjectMemberRequest {

    @NotBlank(message = "A user is required before we can send a project invite.")
    private String userId;

    @NotBlank(message = "A role must be selected before we can send this project invite.")
    private String roleId;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getRoleId() { return roleId; }
    public void setRoleId(String roleId) { this.roleId = roleId; }
}
