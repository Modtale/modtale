package net.modtale.service.auth;

import net.modtale.exception.ApiKeyOperationForbiddenException;
import net.modtale.exception.ProjectNotFoundException;
import net.modtale.model.project.Project;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.ApiKeyRepository;
import net.modtale.service.user.OrganizationApiKeyContextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class ApiKeyRequestedContextAuthorizationService {

    private static final Logger logger = LoggerFactory.getLogger(ApiKeyRequestedContextAuthorizationService.class);

    private final ApiKeyRepository apiKeyRepository;
    private final ProjectRepository projectRepository;
    private final OrganizationApiKeyContextService organizationApiKeyContextService;
    private final ApiKeyContextPermissionService permissionService;

    ApiKeyRequestedContextAuthorizationService(
            ApiKeyRepository apiKeyRepository,
            ProjectRepository projectRepository,
            OrganizationApiKeyContextService organizationApiKeyContextService,
            ApiKeyContextPermissionService permissionService
    ) {
        this.apiKeyRepository = apiKeyRepository;
        this.projectRepository = projectRepository;
        this.organizationApiKeyContextService = organizationApiKeyContextService;
        this.permissionService = permissionService;
    }

    Map<String, Set<ApiKey.ApiPermission>> validateRequestedContexts(
            User user,
            Map<String, Set<ApiKey.ApiPermission>> requestedContexts
    ) {
        String userId = user.getId();
        List<User> userOrganizations = organizationApiKeyContextService.getUserOrganizations(userId);
        Map<String, Set<ApiKey.ApiPermission>> validatedContexts = new HashMap<>();

        for (Map.Entry<String, Set<ApiKey.ApiPermission>> entry : requestedContexts.entrySet()) {
            String contextId = entry.getKey();
            Set<ApiKey.ApiPermission> requestedPermissions = entry.getValue() == null ? Set.of() : entry.getValue();

            if ("PERSONAL".equals(contextId)) {
                validatedContexts.put(contextId, requestedPermissions);
                continue;
            }

            Optional<User> organizationMatch = userOrganizations.stream()
                    .filter(organization -> organization.getId().equals(contextId))
                    .findFirst();

            if (organizationMatch.isPresent()) {
                User organization = organizationMatch.get();
                User.OrganizationMember membership = permissionService.findOrganizationMembership(organization, userId);
                if (membership == null) {
                    throw new ApiKeyOperationForbiddenException("You are no longer a member of that organization.");
                }

                Set<ApiKey.ApiPermission> allowedPermissions =
                        permissionService.filterOrganizationPermissions(organization, membership, requestedPermissions);
                if (!allowedPermissions.isEmpty()) {
                    validatedContexts.put(contextId, allowedPermissions);
                }
                continue;
            }

            Project project = projectRepository.findById(contextId).orElse(null);
            if (project == null) {
                throw new ProjectNotFoundException("We couldn't find one of the requested API key contexts.");
            }

            Set<ApiKey.ApiPermission> allowedPermissions =
                    permissionService.filterProjectPermissions(project, user, userId, requestedPermissions);
            if (!allowedPermissions.isEmpty()) {
                validatedContexts.put(contextId, allowedPermissions);
            }
        }

        return validatedContexts;
    }

    void syncUserProjectPermissions(String userId, String projectId, List<String> allowedPermsList) {
        Set<ApiKey.ApiPermission> allowedPerms = parsePermissions(projectId, allowedPermsList);
        List<ApiKey> keys = apiKeyRepository.findByUserId(userId);
        for (ApiKey key : keys) {
            if (key.getContextPermissions() == null || !key.getContextPermissions().containsKey(projectId)) {
                continue;
            }

            Set<ApiKey.ApiPermission> currentPerms = new HashSet<>(key.getContextPermissions().get(projectId));
            int originalSize = currentPerms.size();

            if (allowedPerms.isEmpty()) {
                key.getContextPermissions().remove(projectId);
                apiKeyRepository.save(key);
                continue;
            }

            currentPerms.retainAll(allowedPerms);
            if (currentPerms.isEmpty()) {
                key.getContextPermissions().remove(projectId);
                apiKeyRepository.save(key);
            } else if (currentPerms.size() != originalSize) {
                key.getContextPermissions().put(projectId, currentPerms);
                apiKeyRepository.save(key);
            }
        }
    }

    private Set<ApiKey.ApiPermission> parsePermissions(String projectId, List<String> allowedPermsList) {
        Set<ApiKey.ApiPermission> allowedPerms = new HashSet<>();
        if (allowedPermsList == null) {
            return allowedPerms;
        }

        for (String permission : allowedPermsList) {
            try {
                allowedPerms.add(ApiKey.ApiPermission.valueOf(permission));
            } catch (IllegalArgumentException ex) {
                logger.warn("Ignoring unknown project API permission '{}' while syncing project={}", permission, projectId);
            }
        }
        return allowedPerms;
    }
}
