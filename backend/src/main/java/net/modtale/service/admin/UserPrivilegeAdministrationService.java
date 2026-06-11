package net.modtale.service.admin;

import net.modtale.exception.ResourceNotFoundException;
import net.modtale.model.dto.response.admin.UserTierUpdateResponse;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
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

    public UserTierUpdateResponse setUserTier(String adminId, String userId, String tier) {
        ApiKey.Tier tierEnum;
        if ("USER".equalsIgnoreCase(tier) || "FREE".equalsIgnoreCase(tier)) {
            tierEnum = ApiKey.Tier.USER;
        } else {
            tierEnum = ApiKey.Tier.valueOf(tier.toUpperCase());
        }

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

    public void addUserRole(String adminId, String userId, String role) {
        User target = requireUser(userId);
        if (target.getRoles() == null) {
            target.setRoles(new java.util.ArrayList<>());
        }
        if (!target.getRoles().contains(role)) {
            target.getRoles().add(role);
        }
        userRepository.save(target);
        adminAuditLogger.logAction(adminId, "ADD_ROLE", target.getId(), "USER", "Role: " + role);
    }

    public void removeUserRole(String adminId, String userId, String role) {
        User target = requireUser(userId);
        if (target.getRoles() != null) {
            target.getRoles().remove(role);
            userRepository.save(target);
        }
        adminAuditLogger.logAction(adminId, "REMOVE_ROLE", target.getId(), "USER", "Role: " + role);
    }

    private User requireUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }
}
