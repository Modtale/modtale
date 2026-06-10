package net.modtale.model.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import net.modtale.model.user.ApiKey;

import java.util.Map;
import java.util.Set;

public class CreateApiKeyRequest {
    @NotBlank(message = "An API key name is required before we can create the key.")
    private String name;
    private Map<String, Set<ApiKey.ApiPermission>> contextPermissions;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Map<String, Set<ApiKey.ApiPermission>> getContextPermissions() { return contextPermissions; }
    public void setContextPermissions(Map<String, Set<ApiKey.ApiPermission>> contextPermissions) { this.contextPermissions = contextPermissions; }
}
