package net.modtale.model.dto.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import net.modtale.model.user.ApiKey;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiKeyDTO(
        String id,
        String name,
        String prefix,
        ApiKey.Tier tier,
        Map<String, Set<ApiKey.ApiPermission>> contextPermissions,
        LocalDateTime lastUsed,
        LocalDateTime createdAt
) {}
