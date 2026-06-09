package net.modtale.mapper;

import net.modtale.model.dto.auth.ApiKeyDTO;
import net.modtale.model.user.ApiKey;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AuthMapperTest {

    @Test
    void toApiKeyDTOMapsAllVisibleFields() {
        ApiKey key = new ApiKey();
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime lastUsed = LocalDateTime.of(2026, 1, 5, 12, 0);

        key.setId("key-1");
        key.setName("CLI");
        key.setPrefix("mt_123");
        key.setTier(ApiKey.Tier.ENTERPRISE);
        key.setContextPermissions(Map.of("PERSONAL", Set.of(ApiKey.ApiPermission.PROJECT_READ)));
        key.setCreatedAt(createdAt);
        key.setLastUsed(lastUsed);

        ApiKeyDTO dto = AuthMapper.toApiKeyDTO(key);

        assertEquals("key-1", dto.id());
        assertEquals("CLI", dto.name());
        assertEquals("mt_123", dto.prefix());
        assertEquals(ApiKey.Tier.ENTERPRISE, dto.tier());
        assertEquals(Set.of(ApiKey.ApiPermission.PROJECT_READ), dto.contextPermissions().get("PERSONAL"));
        assertEquals(lastUsed, dto.lastUsed());
        assertEquals(createdAt, dto.createdAt());
        assertNull(AuthMapper.toApiKeyDTO(null));
    }
}
