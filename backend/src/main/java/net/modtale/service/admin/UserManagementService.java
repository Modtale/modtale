package net.modtale.service.admin;

import net.modtale.model.admin.BannedEmail;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.repository.admin.BannedEmailRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.communication.EmailService;
import net.modtale.service.user.AccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class UserManagementService {

    private static final Logger logger = LoggerFactory.getLogger(UserManagementService.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$");

    @Autowired private UserRepository userRepository;
    @Autowired private BannedEmailRepository bannedEmailRepository;
    @Autowired private AccountService accountService;
    @Autowired private EmailService emailService;
    @Autowired private MongoTemplate mongoTemplate;

    public void banEmail(String email, String reason, String bannedBy) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Invalid email format.");
        }
        if (bannedEmailRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Email is already banned.");
        }

        bannedEmailRepository.save(new BannedEmail(email, reason, bannedBy));

        Optional<User> existingUser = userRepository.findByEmail(email);
        if (existingUser.isPresent()) {
            User user = existingUser.get();
            if (!user.isDeleted()) {
                accountService.deleteUser(user.getId());
                try {
                    emailService.sendAccountDeletionEmail(email, user.getUsername(), "Account associated with a banned email address. Reason: " + reason);
                } catch (Exception e) {
                    logger.error("Failed to send ban email to " + email, e);
                }
                logger.info("Automatically deleted user " + user.getUsername() + " due to email ban on " + email);
            }
        }
    }

    public void unbanEmail(String email) {
        bannedEmailRepository.findByEmailIgnoreCase(email).ifPresent(bannedEmailRepository::delete);
    }

    public List<BannedEmail> getBannedEmails() {
        return bannedEmailRepository.findAll(Sort.by(Sort.Direction.DESC, "bannedAt"));
    }

    public void setUserTier(String userId, ApiKey.Tier newTier) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setTier(newTier);
        userRepository.save(user);
        mongoTemplate.updateMulti(new Query(Criteria.where("userId").is(user.getId())), new Update().set("tier", newTier), ApiKey.class);
    }
}