package net.modtale.service.auth;

import net.modtale.model.resources.Mod;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.repository.resources.ModRepository;
import net.modtale.repository.user.ApiKeyRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;

@Service
public class ApiKeyService {

    @Autowired private ApiKeyRepository apiKeyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserService userService;
    @Autowired private ModRepository modRepository;

    @Qualifier("taskExecutor")
    @Autowired private Executor taskExecutor;

    @Value("${app.limits.max-api-keys-per-user:10}")
    private int maxApiKeys;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    public String createApiKey(String userId, String name, Map<String, Set<ApiKey.ApiPermission>> requestedContexts) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<ApiKey> existingKeys = apiKeyRepository.findByUserId(userId);
        if (existingKeys.size() >= maxApiKeys) {
            throw new IllegalStateException("You have reached the maximum limit of " + maxApiKeys + " API keys.");
        }

        List<User> userOrgs = userService.getUserOrganizations(userId);
        Map<String, Set<ApiKey.ApiPermission>> validatedContexts = new HashMap<>();

        for (Map.Entry<String, Set<ApiKey.ApiPermission>> entry : requestedContexts.entrySet()) {
            String contextId = entry.getKey();
            Set<ApiKey.ApiPermission> requestedPerms = entry.getValue();

            if ("PERSONAL".equals(contextId)) {
                validatedContexts.put(contextId, requestedPerms);
            } else {
                Optional<User> orgOpt = userOrgs.stream().filter(o -> o.getId().equals(contextId)).findFirst();

                if (orgOpt.isPresent()) {
                    User org = orgOpt.get();
                    User.OrganizationMember membership = org.getOrganizationMembers().stream()
                            .filter(m -> m.getUserId().equals(userId)).findFirst().orElseThrow(() -> new SecurityException("Membership not found."));

                    Set<ApiKey.ApiPermission> allowedPerms = new HashSet<>();
                    if ("ADMIN".equals(membership.getRole())) {
                        allowedPerms.addAll(requestedPerms);
                    } else if (membership.getRoleId() != null) {
                        User.OrganizationRole role = org.getOrganizationRoles().stream()
                                .filter(r -> r.getId().equals(membership.getRoleId())).findFirst().orElse(null);

                        if (role != null) {
                            if (role.isOwner()) {
                                allowedPerms.addAll(requestedPerms);
                            } else if (role.getPermissions() != null) {
                                for (ApiKey.ApiPermission perm : requestedPerms) {
                                    if (role.getPermissions().contains(perm)) allowedPerms.add(perm);
                                }
                            }
                        }
                    }
                    if (!allowedPerms.isEmpty()) validatedContexts.put(contextId, allowedPerms);
                } else {
                    Mod project = modRepository.findById(contextId).orElse(null);
                    if (project != null) {
                        Set<ApiKey.ApiPermission> allowedPerms = new HashSet<>();

                        boolean isAuthor = project.getAuthorId() != null && project.getAuthorId().equals(userId);
                        boolean isOrgAdmin = false;

                        if (!isAuthor && project.getAuthorId() != null) {
                            User authorUser = userRepository.findById(project.getAuthorId()).orElse(null);
                            if (authorUser != null && authorUser.getAccountType() == User.AccountType.ORGANIZATION) {
                                isOrgAdmin = authorUser.getOrganizationMembers().stream()
                                        .anyMatch(m -> m.getUserId().equals(userId) && "ADMIN".equals(m.getRole()));
                            }
                        }

                        if (isAuthor || isOrgAdmin) {
                            allowedPerms.addAll(requestedPerms);
                        } else {
                            Mod.ProjectMember member = project.getTeamMembers() != null ?
                                    project.getTeamMembers().stream().filter(m -> m.getUserId().equals(userId)).findFirst().orElse(null) : null;

                            if (member == null) throw new SecurityException("You are not a contributor to project: " + contextId);

                            Mod.ProjectRole role = project.getProjectRoles() != null && member.getRoleId() != null ?
                                    project.getProjectRoles().stream().filter(r -> r.getId().equals(member.getRoleId())).findFirst().orElse(null) : null;

                            if (role != null && role.getPermissions() != null) {
                                for (ApiKey.ApiPermission perm : requestedPerms) {
                                    if (role.getPermissions().contains(perm.name())) allowedPerms.add(perm);
                                }
                            }
                        }
                        if (!allowedPerms.isEmpty()) validatedContexts.put(contextId, allowedPerms);
                    } else {
                        throw new SecurityException("Invalid context or you are not a member: " + contextId);
                    }
                }
            }
        }

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String randomPart = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        String plainKey = "md_" + randomPart;
        String prefix = plainKey.substring(0, 10);

        ApiKey apiKey = new ApiKey(userId, name, encoder.encode(plainKey), prefix);
        apiKey.setTier(user.getTier());
        apiKey.setContextPermissions(validatedContexts);

        apiKeyRepository.save(apiKey);
        return plainKey;
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

    public void syncUserProjectPermissions(String userId, String projectId, List<String> allowedPermsList) {
        Set<ApiKey.ApiPermission> allowedPerms = new HashSet<>();
        if (allowedPermsList != null) {
            for (String p : allowedPermsList) {
                try {
                    allowedPerms.add(ApiKey.ApiPermission.valueOf(p));
                } catch (IllegalArgumentException ignored) {}
            }
        }

        List<ApiKey> keys = apiKeyRepository.findByUserId(userId);
        for (ApiKey key : keys) {
            if (key.getContextPermissions() != null && key.getContextPermissions().containsKey(projectId)) {
                Set<ApiKey.ApiPermission> currentPerms = new HashSet<>(key.getContextPermissions().get(projectId));
                int originalSize = currentPerms.size();

                if (allowedPerms.isEmpty()) {
                    key.getContextPermissions().remove(projectId);
                    apiKeyRepository.save(key);
                } else {
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
        }
    }

    private void cleanInvalidContexts(ApiKey apiKey) {
        if (apiKey.getContextPermissions() == null) return;
        boolean changed = false;

        Iterator<Map.Entry<String, Set<ApiKey.ApiPermission>>> it = apiKey.getContextPermissions().entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Set<ApiKey.ApiPermission>> entry = it.next();
            String contextId = entry.getKey();
            if ("PERSONAL".equals(contextId)) continue;

            Set<ApiKey.ApiPermission> currentKeyPerms = new HashSet<>(entry.getValue());
            Set<ApiKey.ApiPermission> maxAllowedPerms = new HashSet<>();
            boolean hasAccess = false;

            User org = userRepository.findById(contextId).orElse(null);
            if (org != null && !org.isDeleted() && org.getAccountType() == User.AccountType.ORGANIZATION) {
                if (org.getOrganizationMembers() != null) {
                    User.OrganizationMember membership = org.getOrganizationMembers().stream()
                            .filter(m -> m.getUserId().equals(apiKey.getUserId()))
                            .findFirst().orElse(null);

                    if (membership != null) {
                        hasAccess = true;
                        if ("ADMIN".equals(membership.getRole())) {
                            maxAllowedPerms.addAll(EnumSet.allOf(ApiKey.ApiPermission.class));
                        } else if (membership.getRoleId() != null) {
                            User.OrganizationRole role = org.getOrganizationRoles().stream()
                                    .filter(r -> r.getId().equals(membership.getRoleId())).findFirst().orElse(null);
                            if (role != null) {
                                if (role.isOwner()) {
                                    maxAllowedPerms.addAll(EnumSet.allOf(ApiKey.ApiPermission.class));
                                } else if (role.getPermissions() != null) {
                                    maxAllowedPerms.addAll(role.getPermissions());
                                }
                            }
                        }
                    }
                }
            } else {
                Mod mod = modRepository.findById(contextId).orElse(null);
                if (mod != null && !"DELETED".equals(mod.getStatus())) {
                    boolean isAuthor = mod.getAuthorId() != null && mod.getAuthorId().equals(apiKey.getUserId());
                    boolean isOrgAdmin = false;

                    if (!isAuthor && mod.getAuthorId() != null) {
                        User authorUser = userRepository.findById(mod.getAuthorId()).orElse(null);
                        if (authorUser != null && !authorUser.isDeleted() && authorUser.getAccountType() == User.AccountType.ORGANIZATION) {
                            isOrgAdmin = authorUser.getOrganizationMembers() != null && authorUser.getOrganizationMembers().stream()
                                    .anyMatch(m -> m.getUserId().equals(apiKey.getUserId()) && "ADMIN".equals(m.getRole()));
                        }
                    }

                    if (isAuthor || isOrgAdmin) {
                        hasAccess = true;
                        maxAllowedPerms.addAll(EnumSet.allOf(ApiKey.ApiPermission.class));
                    } else if (mod.getTeamMembers() != null) {
                        Mod.ProjectMember member = mod.getTeamMembers().stream()
                                .filter(m -> m.getUserId().equals(apiKey.getUserId()))
                                .findFirst().orElse(null);

                        if (member != null) {
                            hasAccess = true;
                            Mod.ProjectRole role = mod.getProjectRoles() != null && member.getRoleId() != null ?
                                    mod.getProjectRoles().stream().filter(r -> r.getId().equals(member.getRoleId())).findFirst().orElse(null) : null;

                            if (role != null && role.getPermissions() != null) {
                                for (String p : role.getPermissions()) {
                                    try {
                                        maxAllowedPerms.add(ApiKey.ApiPermission.valueOf(p));
                                    } catch (IllegalArgumentException ignored) {}
                                }
                            }
                        }
                    }
                }
            }

            if (!hasAccess) {
                it.remove();
                changed = true;
            } else {
                int originalSize = currentKeyPerms.size();
                currentKeyPerms.retainAll(maxAllowedPerms);

                if (currentKeyPerms.isEmpty()) {
                    it.remove();
                    changed = true;
                } else if (currentKeyPerms.size() != originalSize) {
                    entry.setValue(currentKeyPerms);
                    changed = true;
                }
            }
        }

        if (changed) {
            taskExecutor.execute(() -> apiKeyRepository.save(apiKey));
        }
    }

    public ApiKey resolveKey(String plainKey) {
        if (plainKey == null || plainKey.length() < 10) return null;

        String prefix = plainKey.substring(0, 10);

        ApiKey apiKey = apiKeyRepository.findByPrefix(prefix).orElse(null);
        if (apiKey == null) return null;

        if (encoder.matches(plainKey, apiKey.getKeyHash())) {
            cleanInvalidContexts(apiKey);
            updateLastUsed(apiKey);
            return apiKey;
        }

        return null;
    }

    private void updateLastUsed(ApiKey key) {
        taskExecutor.execute(() -> {
            key.setLastUsed(LocalDateTime.now());
            apiKeyRepository.save(key);
        });
    }

    public User getUserFromKey(ApiKey key) {
        return userRepository.findById(key.getUserId()).orElse(null);
    }

    public List<ApiKey> getMyKeys(String userId) {
        return apiKeyRepository.findByUserId(userId);
    }

    public void revokeKey(String keyId, String userId) {
        ApiKey key = apiKeyRepository.findById(keyId).orElse(null);
        if (key != null && key.getUserId().equals(userId)) {
            apiKeyRepository.delete(key);
        }
    }
}