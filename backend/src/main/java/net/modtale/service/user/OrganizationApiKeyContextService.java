package net.modtale.service.user;

import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.repository.user.ApiKeyRepository;
import net.modtale.repository.user.UserRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class OrganizationApiKeyContextService {

    private final UserRepository userRepository;
    private final ApiKeyRepository apiKeyRepository;

    public OrganizationApiKeyContextService(UserRepository userRepository, ApiKeyRepository apiKeyRepository) {
        this.userRepository = userRepository;
        this.apiKeyRepository = apiKeyRepository;
    }

    public List<User> getUserOrganizations(String userId) {
        return userRepository.findOrganizationsByMemberId(userId).stream()
                .filter(org -> !org.isDeleted())
                .toList();
    }

    public void syncUserOrgPermissions(String userId, String orgId, Set<ApiKey.ApiPermission> allowedPerms) {
        List<ApiKey> keys = apiKeyRepository.findByUserId(userId);

        for (ApiKey key : keys) {
            if (key.getContextPermissions() != null && key.getContextPermissions().containsKey(orgId)) {
                Set<ApiKey.ApiPermission> currentPerms = new HashSet<>(key.getContextPermissions().get(orgId));
                int originalSize = currentPerms.size();

                if (allowedPerms == null || allowedPerms.isEmpty()) {
                    key.getContextPermissions().remove(orgId);
                    apiKeyRepository.save(key);
                } else {
                    currentPerms.retainAll(allowedPerms);
                    if (currentPerms.isEmpty()) {
                        key.getContextPermissions().remove(orgId);
                        apiKeyRepository.save(key);
                    } else if (currentPerms.size() != originalSize) {
                        key.getContextPermissions().put(orgId, currentPerms);
                        apiKeyRepository.save(key);
                    }
                }
            }
        }
    }
}
