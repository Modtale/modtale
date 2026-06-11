package net.modtale.model.dto.request.organization;

import jakarta.validation.constraints.NotBlank;

public class AddOrganizationMemberRequest {

    @NotBlank(message = "A user is required before we can send an organization invite.")
    private String userId;

    @NotBlank(message = "A role must be selected before we can send this organization invite.")
    private String roleId;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }
}
