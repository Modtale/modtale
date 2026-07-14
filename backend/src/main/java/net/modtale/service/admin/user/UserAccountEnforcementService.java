package net.modtale.service.admin.user;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import net.modtale.exception.ForbiddenOperationException;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.mapper.AdminMapper;
import net.modtale.mapper.UserMapper;
import net.modtale.model.admin.BannedEmail;
import net.modtale.model.dto.admin.BannedEmailDTO;
import net.modtale.model.dto.user.UserDTO;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.repository.admin.BannedEmailRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.admin.audit.AdminAuditLogger;
import net.modtale.service.communication.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Service
public class UserAccountEnforcementService {

    private static final Logger logger = LoggerFactory.getLogger(UserAccountEnforcementService.class);
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$");

    private final UserRepository userRepository;
    private final BannedEmailRepository bannedEmailRepository;
    private final EmailService emailService;
    private final MongoTemplate mongoTemplate;
    private final AdminAuditLogger adminAuditLogger;

    public UserAccountEnforcementService(
            UserRepository userRepository,
            BannedEmailRepository bannedEmailRepository,
            EmailService emailService,
            MongoTemplate mongoTemplate,
            AdminAuditLogger adminAuditLogger
    ) {
        this.userRepository = userRepository;
        this.bannedEmailRepository = bannedEmailRepository;
        this.emailService = emailService;
        this.mongoTemplate = mongoTemplate;
        this.adminAuditLogger = adminAuditLogger;
    }

    public List<BannedEmailDTO> getBannedEmailViews() {
        return bannedEmailRepository.findAll(Sort.by(Sort.Direction.DESC, "bannedAt")).stream()
                .map(AdminMapper::toBannedEmailDTO)
                .toList();
    }

    public void banEmail(User adminUser, String email, String reason) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Invalid email format.");
        }
        if (bannedEmailRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Email is already banned.");
        }

        Optional<User> existingUser = userRepository.findByEmail(email);
        if (existingUser.isPresent() && !canManageUser(adminUser, existingUser.get())) {
            throw new ForbiddenOperationException("Cannot ban the email of an administrator.");
        }

        bannedEmailRepository.save(new BannedEmail(email, reason, adminUser.getId()));
        if (existingUser.isPresent()) {
            User user = existingUser.get();
            if (!user.isDeleted()) {
                softDeleteUser(user);
                try {
                    emailService.sendAccountDeletionEmail(
                            email,
                            user.getUsername(),
                            "Account associated with a banned email address. Reason: " + reason
                    );
                } catch (RuntimeException ex) {
                    logger.warn("Failed to send banned-email deletion notice to {}", email, ex);
                }
            }
        }

        adminAuditLogger.logAction(adminUser.getId(), "BAN_EMAIL", email, "EMAIL", "Reason: " + reason);
    }

    public void unbanEmail(String adminId, String email) {
        bannedEmailRepository.findByEmailIgnoreCase(email).ifPresent(bannedEmailRepository::delete);
        adminAuditLogger.logAction(adminId, "UNBAN_EMAIL", email, "EMAIL", null);
    }

    public UserDTO getUserDetails(String userId) {
        User target = requireUser(userId);
        return UserMapper.toDTO(target, true);
    }

    public User getRawUser(String userId) {
        User target = requireUser(userId);
        target.setGithubAccessToken(null);
        target.setGitlabAccessToken(null);
        target.setGitlabRefreshToken(null);
        target.setGitlabTokenExpiresAt(null);
        return target;
    }

    public void updateRawUser(String adminId, String userId, User updatedData) {
        User existing = requireUser(userId);

        updatedData.setId(existing.getId());
        updatedData.setPassword(existing.getPassword());
        updatedData.setMfaSecret(existing.getMfaSecret());
        updatedData.setVerificationToken(existing.getVerificationToken());
        updatedData.setVerificationTokenExpiry(existing.getVerificationTokenExpiry());
        updatedData.setPasswordResetToken(existing.getPasswordResetToken());
        updatedData.setPasswordResetTokenExpiry(existing.getPasswordResetTokenExpiry());
        updatedData.setGithubAccessToken(existing.getGithubAccessToken());
        updatedData.setGitlabAccessToken(existing.getGitlabAccessToken());
        updatedData.setGitlabRefreshToken(existing.getGitlabRefreshToken());
        updatedData.setGitlabTokenExpiresAt(existing.getGitlabTokenExpiresAt());

        if (updatedData.isMfaEnabled() && (updatedData.getMfaSecret() == null || updatedData.getMfaSecret().isBlank())) {
            throw new IllegalArgumentException("Cannot enable MFA without a valid MFA secret.");
        }
        userRepository.save(updatedData);
        adminAuditLogger.logAction(adminId, "RAW_UPDATE_USER", existing.getId(), "USER", "Updated via Raw JSON");
    }

    public void deleteUser(User adminUser, String userId, String reason) {
        User target = requireUser(userId);
        if (!canManageUser(adminUser, target)) {
            throw new ForbiddenOperationException("Only Super Admin can delete other admins.");
        }

        softDeleteUser(target);
        try {
            if (target.getEmail() != null && !target.getEmail().isEmpty()) {
                emailService.sendAccountDeletionEmail(target.getEmail(), target.getUsername(), reason);
            }
        } catch (RuntimeException ex) {
            logger.warn("Failed to send account deletion notice to {}", target.getEmail(), ex);
        }
        adminAuditLogger.logAction(
                adminUser.getId(),
                "DELETE_USER",
                target.getId(),
                "USER",
                "Username: " + target.getUsername() + ", Reason: " + reason
        );
    }

    private User requireUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }

    private boolean canManageUser(User currentUser, User targetUser) {
        if (currentUser != null && currentUser.getRoles() != null && currentUser.getRoles().contains("SUPER_ADMIN")) {
            return true;
        }
        return targetUser.getRoles() == null || !targetUser.getRoles().contains("ADMIN");
    }

    private void softDeleteUser(User user) {
        user.setDeletedAt(java.time.LocalDateTime.now());
        user.setGithubAccessToken(null);
        user.setGitlabAccessToken(null);
        user.setGitlabRefreshToken(null);
        userRepository.save(user);
        mongoTemplate.remove(new Query(Criteria.where("userId").is(user.getId())), ApiKey.class);
    }
}
