package net.modtale.service.auth;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import net.modtale.config.properties.AppLimitProperties;
import net.modtale.exception.InvalidApiKeyRequestException;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.ApiKeyRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.security.access.AccessControlService;
import net.modtale.service.user.organization.OrganizationApiKeyContextService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class ApiKeyIssuanceService {

    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;
    private final ApiKeyRequestedContextAuthorizationService requestedContextAuthorizationService;
    private final ApiKeyStaleContextPruningService staleContextPruningService;
    private final int maxApiKeys;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyIssuanceService(
            ApiKeyRepository apiKeyRepository,
            UserRepository userRepository,
            ProjectRepository projectRepository,
            AccessControlService accessControlService,
            OrganizationApiKeyContextService organizationApiKeyContextService,
            @Qualifier("taskExecutor") Executor taskExecutor,
            AppLimitProperties limitProperties
    ) {
        this.apiKeyRepository = apiKeyRepository;
        this.userRepository = userRepository;
        ApiKeyContextPermissionService permissionService = new ApiKeyContextPermissionService(accessControlService);
        this.requestedContextAuthorizationService = new ApiKeyRequestedContextAuthorizationService(
                apiKeyRepository,
                projectRepository,
                organizationApiKeyContextService,
                permissionService
        );
        this.staleContextPruningService = new ApiKeyStaleContextPruningService(
                apiKeyRepository,
                userRepository,
                projectRepository,
                permissionService,
                taskExecutor
        );
        this.maxApiKeys = limitProperties.maxApiKeysPerUser();
    }

    public String createApiKey(String userId, String name, Map<String, Set<ApiKey.ApiPermission>> requestedContexts) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("We couldn't find that user."));

        List<ApiKey> existingKeys = apiKeyRepository.findByUserId(userId);
        validateCreateRequest(name, requestedContexts, existingKeys);

        Map<String, Set<ApiKey.ApiPermission>> validatedContexts =
                requestedContextAuthorizationService.validateRequestedContexts(user, requestedContexts);
        String plainKey = mintPlainKey();
        String prefix = plainKey.substring(0, 10);

        ApiKey apiKey = new ApiKey(userId, name, encoder.encode(plainKey), prefix);
        apiKey.setTier(user.getTier());
        apiKey.setContextPermissions(validatedContexts);

        apiKeyRepository.save(apiKey);
        return plainKey;
    }

    public void syncUserProjectPermissions(String userId, String projectId, Set<ApiKey.ApiPermission> allowedPerms) {
        requestedContextAuthorizationService.syncUserProjectPermissions(userId, projectId, allowedPerms);
    }

    public void pruneInvalidContexts(ApiKey apiKey) {
        staleContextPruningService.pruneInvalidContexts(apiKey);
    }

    private void validateCreateRequest(
            String name,
            Map<String, Set<ApiKey.ApiPermission>> requestedContexts,
            List<ApiKey> existingKeys
    ) {
        if (name == null || name.isBlank()) {
            throw new InvalidApiKeyRequestException("Give the API key a name before creating it.");
        }
        if (requestedContexts == null || requestedContexts.isEmpty()) {
            throw new InvalidApiKeyRequestException("Select at least one API key context before creating the key.");
        }
        if (existingKeys.size() >= maxApiKeys) {
            throw new InvalidApiKeyRequestException("You have already reached the limit of " + maxApiKeys + " API keys.");
        }
    }

    private String mintPlainKey() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String randomPart = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        return "md_" + randomPart;
    }
}
