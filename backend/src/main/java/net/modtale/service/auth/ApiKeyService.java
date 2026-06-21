package net.modtale.service.auth;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import net.modtale.config.properties.AppLimitProperties;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.ApiKeyRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.security.access.AccessControlService;
import net.modtale.service.user.organization.OrganizationApiKeyContextService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class ApiKeyService {

    private final ApiKeyIssuanceService apiKeyIssuanceService;
    private final ApiKeyResolutionService apiKeyResolutionService;

    @Autowired
    public ApiKeyService(
            ApiKeyIssuanceService apiKeyIssuanceService,
            ApiKeyResolutionService apiKeyResolutionService
    ) {
        this.apiKeyIssuanceService = apiKeyIssuanceService;
        this.apiKeyResolutionService = apiKeyResolutionService;
    }

    public ApiKeyService(
            ApiKeyRepository apiKeyRepository,
            UserRepository userRepository,
            ProjectRepository projectRepository,
            AccessControlService accessControlService,
            OrganizationApiKeyContextService organizationApiKeyContextService,
            @Qualifier("taskExecutor") Executor taskExecutor,
            AppLimitProperties limitProperties
    ) {
        ApiKeyIssuanceService issuanceService = new ApiKeyIssuanceService(
                apiKeyRepository,
                userRepository,
                projectRepository,
                accessControlService,
                organizationApiKeyContextService,
                taskExecutor,
                limitProperties
        );
        this.apiKeyIssuanceService = issuanceService;
        this.apiKeyResolutionService = new ApiKeyResolutionService(
                apiKeyRepository,
                userRepository,
                issuanceService,
                taskExecutor
        );
    }

    public String createApiKey(String userId, String name, Map<String, Set<ApiKey.ApiPermission>> requestedContexts) {
        return apiKeyIssuanceService.createApiKey(userId, name, requestedContexts);
    }

    public void syncUserProjectPermissions(String userId, String projectId, Set<ApiKey.ApiPermission> allowedPerms) {
        apiKeyIssuanceService.syncUserProjectPermissions(userId, projectId, allowedPerms);
    }

    public ApiKey resolveKey(String plainKey) {
        return apiKeyResolutionService.resolveKey(plainKey);
    }

    public User getUserFromKey(ApiKey key) {
        return apiKeyResolutionService.getUserFromKey(key);
    }

    public List<ApiKey> getMyKeys(String userId) {
        return apiKeyResolutionService.getMyKeys(userId);
    }

    public void revokeKey(String keyId, String userId) {
        apiKeyResolutionService.revokeKey(keyId, userId);
    }
}
