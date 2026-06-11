package net.modtale.service.admin;

import net.modtale.model.dto.admin.BannedEmailDTO;
import net.modtale.model.dto.response.admin.UserTierUpdateResponse;
import net.modtale.model.dto.user.UserDTO;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserManagementService {

    private final UserAccountEnforcementService userAccountEnforcementService;
    private final UserPrivilegeAdministrationService userPrivilegeAdministrationService;

    public UserManagementService(
            UserAccountEnforcementService userAccountEnforcementService,
            UserPrivilegeAdministrationService userPrivilegeAdministrationService
    ) {
        this.userAccountEnforcementService = userAccountEnforcementService;
        this.userPrivilegeAdministrationService = userPrivilegeAdministrationService;
    }

    public List<BannedEmailDTO> getBannedEmailViews() {
        return userAccountEnforcementService.getBannedEmailViews();
    }

    public void banEmail(User adminUser, String email, String reason) {
        userAccountEnforcementService.banEmail(adminUser, email, reason);
    }

    public void unbanEmail(String adminId, String email) {
        userAccountEnforcementService.unbanEmail(adminId, email);
    }

    public UserDTO getUserDetails(String userId) {
        return userAccountEnforcementService.getUserDetails(userId);
    }

    public User getRawUser(String userId) {
        return userAccountEnforcementService.getRawUser(userId);
    }

    public void updateRawUser(String adminId, String userId, User updatedData) {
        userAccountEnforcementService.updateRawUser(adminId, userId, updatedData);
    }

    public void deleteUser(User adminUser, String userId, String reason) {
        userAccountEnforcementService.deleteUser(adminUser, userId, reason);
    }

    public UserTierUpdateResponse setUserTier(String adminId, String userId, ApiKey.Tier tier) {
        return userPrivilegeAdministrationService.setUserTier(adminId, userId, tier);
    }

    public void addUserRole(String adminId, String userId, String role) {
        userPrivilegeAdministrationService.addUserRole(adminId, userId, role);
    }

    public void removeUserRole(String adminId, String userId, String role) {
        userPrivilegeAdministrationService.removeUserRole(adminId, userId, role);
    }
}
