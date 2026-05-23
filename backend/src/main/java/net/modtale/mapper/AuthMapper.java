package net.modtale.mapper;

import net.modtale.model.dto.auth.ApiKeyDTO;
import net.modtale.model.user.ApiKey;

public class AuthMapper {
    public static ApiKeyDTO toApiKeyDTO(ApiKey key) {
        if (key == null) return null;
        return new ApiKeyDTO(
                key.getId(),
                key.getName(),
                key.getPrefix(),
                key.getTier(),
                key.getContextPermissions(),
                key.getLastUsed(),
                key.getCreatedAt()
        );
    }
}
