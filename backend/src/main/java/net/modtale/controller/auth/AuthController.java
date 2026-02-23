package net.modtale.controller.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import net.modtale.model.user.User;
import net.modtale.service.user.UserService;
import net.modtale.service.auth.TwoFactorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired private UserService userService;
    @Autowired private TwoFactorService twoFactorService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            User user = userService.registerUser(request.username, request.email, request.password);
            return ResponseEntity.ok(Map.of("message", "User registered successfully", "username", user.getUsername()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyEmail(@RequestParam String token) {
        try {
            userService.verifyEmail(token);
            return ResponseEntity.ok(Map.of("message", "Email verified successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification() {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        try {
            userService.resendVerificationEmail(user);
            return ResponseEntity.ok(Map.of("message", "Verification email sent"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        if (email == null || email.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "Email is required."));
        userService.initiatePasswordReset(email);
        return ResponseEntity.ok(Map.of("message", "If an account exists for that email, a password reset link has been sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        try {
            userService.completePasswordReset(request.token, request.password);
            return ResponseEntity.ok(Map.of("message", "Password reset successfully. You can now login."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/credentials")
    public ResponseEntity<?> updateCredentials(@RequestBody Map<String, String> payload) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();

        String email = payload.get("email");
        String password = payload.get("password");

        try {
            userService.addCredentials(user.getId(), email, password);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> payload) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();

        String currentPassword = payload.get("currentPassword");
        String newPassword = payload.get("newPassword");

        try {
            userService.changePassword(user.getId(), currentPassword, newPassword);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/mfa/setup")
    public ResponseEntity<?> setupMfa() {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (user.isMfaEnabled()) return ResponseEntity.badRequest().body(Map.of("error", "MFA is already enabled."));

        String secret = twoFactorService.generateNewSecret();
        userService.setTempMfaSecret(user.getId(), secret);

        String qrCode = twoFactorService.generateQrCodeImageUri(secret, user.getUsername());
        return ResponseEntity.ok(Map.of("secret", secret, "qrCode", qrCode));
    }

    @PostMapping("/mfa/verify")
    public ResponseEntity<?> verifyMfaSetup(@RequestBody Map<String, String> body) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();

        String code = body.get("code");
        if (code == null || code.length() != 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid code format"));
        }

        String secret = user.getMfaSecret();

        if (twoFactorService.isOtpValid(secret, code)) {
            userService.enableMfa(user.getId());
            return ResponseEntity.ok(Map.of("message", "MFA enabled successfully"));
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid verification code. 2FA not enabled."));
        }
    }

    @PostMapping("/signin")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String username = body.get("username");
        String password = body.get("password");

        try {
            User user = userService.authenticate(username, password);

            if (user.isMfaEnabled()) {
                String preAuthToken = userService.generatePreAuthToken(user.getId());
                return ResponseEntity.accepted().body(Map.of("mfa_required", true, "pre_auth_token", preAuthToken));
            } else {
                createSession(user, request);
                return ResponseEntity.ok(Map.of("status", "success"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }
    }

    @PostMapping("/mfa/validate-login")
    public ResponseEntity<?> validateLoginMfa(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String preAuthToken = body.get("pre_auth_token");
        String code = body.get("code");

        User user = userService.validatePreAuthToken(preAuthToken);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Session expired or invalid. Please login again."));
        }

        if (user.isMfaEnabled()) {
            if (twoFactorService.isOtpValid(user.getMfaSecret(), code)) {
                createSession(user, request);
                return ResponseEntity.ok(Map.of("status", "success"));
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid 2FA code"));
            }
        }

        return ResponseEntity.badRequest().build();
    }

    private void createSession(User user, HttpServletRequest request) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                user,
                null,
                user.getRoles().stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).collect(Collectors.toList())
        );
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        HttpSession session = request.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }

    public static class RegisterRequest {
        public String username;
        public String email;
        public String password;
    }

    public static class ResetPasswordRequest {
        public String token;
        public String password;
    }
}