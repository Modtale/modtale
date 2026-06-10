package net.modtale.service.user;

import net.modtale.exception.UnauthorizedException;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserResolutionService {

    private static final Logger logger = LoggerFactory.getLogger(CurrentUserResolutionService.class);

    private final UserRepository userRepository;

    public CurrentUserResolutionService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User resolveCurrentUser(Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return null;
            }

            Object principal = authentication.getPrincipal();
            String username = null;
            String userId = null;

            if (principal instanceof User authenticatedUser) {
                userId = authenticatedUser.getId();
                username = authenticatedUser.getUsername();
            } else if (principal instanceof org.springframework.security.core.userdetails.User springUser) {
                username = springUser.getUsername();
            } else if (principal instanceof OAuth2User oauth2User) {
                username = oauth2User.getAttribute("login");
                Object oauthId = oauth2User.getAttribute("id");
                if (oauthId != null) {
                    userId = oauthId.toString();
                }
            } else if (principal instanceof String principalName && !"anonymousUser".equals(principalName)) {
                username = principalName;
            }

            if (userId != null) {
                User user = userRepository.findById(userId).orElse(null);
                if (user != null && !user.isDeleted()) {
                    return user;
                }
            }

            if (username != null) {
                User user = userRepository.findByUsernameIgnoreCase(username).orElse(null);
                if (user != null && !user.isDeleted()) {
                    return user;
                }
            }
        } catch (RuntimeException e) {
            logger.error("Error retrieving current user", e);
        }

        return null;
    }

    public User requireCurrentUser(Authentication authentication, String actionDescription) {
        User currentUser = resolveCurrentUser(authentication);
        if (currentUser == null) {
            throw new UnauthorizedException("You need to sign in before " + actionDescription + ".");
        }
        return currentUser;
    }
}
