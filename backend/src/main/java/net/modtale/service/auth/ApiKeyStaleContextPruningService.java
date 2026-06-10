package net.modtale.service.auth;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.ApiKeyRepository;
import net.modtale.repository.user.UserRepository;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

final class ApiKeyStaleContextPruningService {

    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ApiKeyContextPermissionService permissionService;
    private final Executor taskExecutor;

    ApiKeyStaleContextPruningService(
            ApiKeyRepository apiKeyRepository,
            UserRepository userRepository,
            ProjectRepository projectRepository,
            ApiKeyContextPermissionService permissionService,
            Executor taskExecutor
    ) {
        this.apiKeyRepository = apiKeyRepository;
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.permissionService = permissionService;
        this.taskExecutor = taskExecutor;
    }

    void pruneInvalidContexts(ApiKey apiKey) {
        if (apiKey.getContextPermissions() == null) {
            return;
        }

        boolean changed = false;
        User keyOwner = userRepository.findById(apiKey.getUserId()).orElse(null);
        Iterator<Map.Entry<String, Set<ApiKey.ApiPermission>>> iterator = apiKey.getContextPermissions().entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Set<ApiKey.ApiPermission>> entry = iterator.next();
            String contextId = entry.getKey();
            if ("PERSONAL".equals(contextId)) {
                continue;
            }

            Set<ApiKey.ApiPermission> currentKeyPerms = new HashSet<>(entry.getValue());
            Set<ApiKey.ApiPermission> maxAllowedPerms =
                    resolveAllowedPermissions(contextId, apiKey.getUserId(), keyOwner);

            if (maxAllowedPerms.isEmpty()) {
                iterator.remove();
                changed = true;
                continue;
            }

            int originalSize = currentKeyPerms.size();
            currentKeyPerms.retainAll(maxAllowedPerms);

            if (currentKeyPerms.isEmpty()) {
                iterator.remove();
                changed = true;
            } else if (currentKeyPerms.size() != originalSize) {
                entry.setValue(currentKeyPerms);
                changed = true;
            }
        }

        if (changed) {
            taskExecutor.execute(() -> apiKeyRepository.save(apiKey));
        }
    }

    private Set<ApiKey.ApiPermission> resolveAllowedPermissions(String contextId, String userId, User keyOwner) {
        User organization = userRepository.findById(contextId).orElse(null);
        if (permissionService.isActiveOrganization(organization)) {
            User.OrganizationMember membership = permissionService.findOrganizationMembership(organization, userId);
            if (membership == null) {
                return Set.of();
            }
            return permissionService.filterOrganizationPermissions(
                    organization,
                    membership,
                    EnumSet.allOf(ApiKey.ApiPermission.class)
            );
        }

        Project project = projectRepository.findById(contextId).orElse(null);
        if (project == null || project.getStatus() == ProjectStatus.DELETED) {
            return Set.of();
        }

        return permissionService.filterProjectPermissionsOrEmpty(
                project,
                keyOwner,
                userId,
                EnumSet.allOf(ApiKey.ApiPermission.class)
        );
    }
}
