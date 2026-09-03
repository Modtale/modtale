package net.modtale.service.admin.user;

import java.util.EnumSet;
import java.util.Set;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.model.dto.response.admin.UserTierUpdateResponse;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.AdminPermission;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.admin.audit.AdminAuditLogger;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class UserPrivilegeAdministrationService {

    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;
    private final AdminAuditLogger adminAuditLogger;

    public UserPrivilegeAdministrationService(
            UserRepository userRepository,
            MongoTemplate mongoTemplate,
            AdminAuditLogger adminAuditLogger
    ) {
        this.userRepository = userRepository;
        this.mongoTemplate = mongoTemplate;
        this.adminAuditLogger = adminAuditLogger;
    }

    public UserTierUpdateResponse setUserTier(String adminId, String userId, ApiKey.Tier tierEnum) {
        User user = requireUser(userId);
        user.setTier(tierEnum);
        userRepository.save(user);
        mongoTemplate.updateMulti(
                new Query(Criteria.where("userId").is(user.getId())),
                new Update().set("tier", tierEnum),
                ApiKey.class
        );
        adminAuditLogger.logAction(adminId, "UPDATE_TIER", user.getId(), "USER", "New Tier: " + tierEnum.name());
        return new UserTierUpdateResponse("success", "User " + user.getUsername() + " updated to tier " + tierEnum.name());
    }

    public Set<AdminPermission> setAdminPermissions(String adminId, String userId, Set<AdminPermission> permissions) {
        User target = requireUser(userId);
        Set<AdminPermission> normalizedPermissions = normalizePermissions(permissions);
        target.setAdminPermissions(normalizedPermissions);
        userRepository.save(target);
        adminAuditLogger.logAction(
                adminId,
                "UPDATE_ADMIN_PERMISSIONS",
                target.getId(),
                "USER",
                "Permissions: " + normalizedPermissions
        );
        return AdminPermission.effectivePermissions(target);
    }

    private Set<AdminPermission> normalizePermissions(Set<AdminPermission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return EnumSet.noneOf(AdminPermission.class);
        }
        return EnumSet.copyOf(permissions);
    }

    private User requireUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }
}
