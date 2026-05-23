package net.modtale.service.auth;

import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

@Service
public class ReservedAccountGuardService {

    private static final Logger logger = LoggerFactory.getLogger(ReservedAccountGuardService.class);

    private static final Set<String> RESERVED_EMAILS = Set.of(
            "admin@modtale.net",
            "super_admin@modtale.net",
            "user@modtale.net"
    );

    private final UserRepository userRepository;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    public ReservedAccountGuardService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean isProductionDeployment() {
        String host = extractHost(frontendUrl);
        if (host == null || host.isBlank()) return false;

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        return !normalizedHost.equals("localhost")
                && !normalizedHost.equals("127.0.0.1")
                && !normalizedHost.equals("::1")
                && !normalizedHost.endsWith(".run.app");
    }

    public void purgeReservedAccountsIfProduction() {
        if (!isProductionDeployment()) return;

        for (String reservedEmail : RESERVED_EMAILS) {
            userRepository.findByEmailIgnoreCase(reservedEmail).ifPresent(user -> {
                logger.error("Deleting reserved account '{}' (id={}) in production.", user.getEmail(), user.getId());
                userRepository.delete(user);
            });
        }
    }

    public boolean isReservedEmail(String email) {
        if (email == null) return false;
        return RESERVED_EMAILS.contains(email.trim().toLowerCase(Locale.ROOT));
    }

    public void rejectReservedEmailInProduction(String email) {
        if (!isProductionDeployment()) return;
        if (!isReservedEmail(email)) return;

        purgeReservedAccountsIfProduction();
        throw new IllegalArgumentException("Invalid credentials");
    }

    public void rejectReservedUserInProduction(User user) {
        if (!isProductionDeployment() || user == null) return;
        if (!isReservedEmail(user.getEmail())) return;

        logger.error("Blocking sign-in for reserved account '{}' (id={}) in production.", user.getEmail(), user.getId());
        userRepository.deleteById(user.getId());
        throw new IllegalArgumentException("Invalid credentials");
    }

    private String extractHost(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            return URI.create(url).getHost();
        } catch (Exception ignored) {
            return null;
        }
    }
}
