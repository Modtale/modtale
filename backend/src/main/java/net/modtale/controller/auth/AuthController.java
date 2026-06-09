package net.modtale.controller.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import net.modtale.exception.ErrorMessageUtils;
import net.modtale.model.dto.request.auth.ChangePasswordRequest;
import net.modtale.model.dto.request.auth.ForgotPasswordRequest;
import net.modtale.model.dto.request.auth.MfaLoginRequest;
import net.modtale.model.dto.request.auth.RegisterRequest;
import net.modtale.model.dto.request.auth.ResetPasswordRequest;
import net.modtale.model.dto.request.auth.SignInRequest;
import net.modtale.model.dto.request.auth.UpdateCredentialsRequest;
import net.modtale.model.dto.request.auth.VerifyMfaRequest;
import net.modtale.model.user.User;
import net.modtale.service.user.AccountService;
import net.modtale.service.auth.AuthenticationService;
import net.modtale.service.auth.TwoFactorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired private AuthenticationService authenticationService;
    @Autowired private AccountService accountService;
    @Autowired private TwoFactorService twoFactorService;
    @Autowired private SecurityContextRepository securityContextRepository;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            User user = authenticationService.registerUser(
                    request.getUsername(),
                    request.getEmail(),
                    request.getPassword()
            );
            return ResponseEntity.ok(Map.of("message", "User registered successfully", "username", user.getUsername()));
        } catch (IllegalArgumentException e) {
            return ErrorMessageUtils.badRequest(e, "We could not create that account.");
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyEmail(@RequestParam String token) {
        try {
            authenticationService.verifyEmail(token);
            return ResponseEntity.ok(Map.of("message", "Email verified successfully"));
        } catch (IllegalArgumentException e) {
            return ErrorMessageUtils.badRequest(e, "We could not verify that email address.");
        }
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification() {
        User user = accountService.getCurrentUser();
        if (user == null) return ErrorMessageUtils.unauthorized("You need to sign in before requesting another verification email.");
        try {
            authenticationService.resendVerificationEmail(user);
            return ResponseEntity.ok(Map.of("message", "Verification email sent"));
        } catch (IllegalArgumentException e) {
            return ErrorMessageUtils.badRequest(e, "We could not send another verification email.");
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest requestPayload) {
        String email = requestPayload.getEmail();
        if (email == null || email.isEmpty()) return ErrorMessageUtils.badRequest("An email address is required before we can send a password reset link.");
        authenticationService.initiatePasswordReset(email);
        return ResponseEntity.ok(Map.of("message", "If an account exists for that email, a password reset link has been sent."));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        SecurityContextHolder.clearContext();
        securityContextRepository.saveContext(SecurityContextHolder.createEmptyContext(), request, response);

        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        expireCookie(response, "SESSION");
        expireCookie(response, "JSESSIONID");
        expireCookie(response, "XSRF-TOKEN");

        return ResponseEntity.ok(Map.of("status", "success"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        try {
            authenticationService.completePasswordReset(request.getToken(), request.getPassword());
            return ResponseEntity.ok(Map.of("message", "Password reset successfully. You can now login."));
        } catch (IllegalArgumentException e) {
            return ErrorMessageUtils.badRequest(e, "We could not reset that password.");
        }
    }

    @PutMapping("/credentials")
    public ResponseEntity<?> updateCredentials(@RequestBody UpdateCredentialsRequest requestPayload) {
        User user = accountService.getCurrentUser();
        if (user == null) return ErrorMessageUtils.unauthorized("You need to sign in before updating your email or password.");

        String email = requestPayload.getEmail();
        String password = requestPayload.getPassword();

        try {
            authenticationService.addCredentials(user.getId(), email, password);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ErrorMessageUtils.badRequest(e, "We could not update those login credentials.");
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest requestPayload) {
        User user = accountService.getCurrentUser();
        if (user == null) return ErrorMessageUtils.unauthorized("You need to sign in before changing your password.");

        String currentPassword = requestPayload.getCurrentPassword();
        String newPassword = requestPayload.getNewPassword();

        try {
            authenticationService.changePassword(user.getId(), currentPassword, newPassword);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ErrorMessageUtils.badRequest(e, "We could not change that password.");
        }
    }

    @GetMapping("/mfa/setup")
    public ResponseEntity<?> setupMfa() {
        User user = accountService.getCurrentUser();
        if (user == null) return ErrorMessageUtils.unauthorized("You need to sign in before setting up two-factor authentication.");
        if (user.isMfaEnabled()) return ErrorMessageUtils.badRequest("Two-factor authentication is already enabled for this account.");

        String secret = twoFactorService.generateNewSecret();
        authenticationService.setTempMfaSecret(user.getId(), secret);

        String qrCode = twoFactorService.generateQrCodeImageUri(secret, user.getUsername());
        return ResponseEntity.ok(Map.of("secret", secret, "qrCode", qrCode));
    }

    @PostMapping("/mfa/verify")
    public ResponseEntity<?> verifyMfaSetup(@RequestBody VerifyMfaRequest requestPayload) {
        User user = accountService.getCurrentUser();
        if (user == null) return ErrorMessageUtils.unauthorized("You need to sign in before verifying two-factor authentication setup.");

        String code = requestPayload.getCode();
        if (code == null || code.length() != 6) {
            return ErrorMessageUtils.badRequest("Two-factor authentication codes must be exactly 6 digits.");
        }

        String secret = user.getMfaSecret();

        if (twoFactorService.isOtpValid(secret, code)) {
            authenticationService.enableMfa(user.getId());
            return ResponseEntity.ok(Map.of("message", "MFA enabled successfully"));
        } else {
            return ErrorMessageUtils.badRequest("That verification code was not accepted, so two-factor authentication was not enabled.");
        }
    }

    @PostMapping("/signin")
    public ResponseEntity<?> login(@RequestBody SignInRequest requestPayload, HttpServletRequest request, HttpServletResponse response) {
        String username = requestPayload.getUsername();
        String password = requestPayload.getPassword();

        try {
            User user = authenticationService.authenticate(username, password);

            if (user.isMfaEnabled()) {
                String preAuthToken = authenticationService.generatePreAuthToken(user.getId());
                return ResponseEntity.accepted().body(Map.of("mfa_required", true, "pre_auth_token", preAuthToken));
            } else {
                createSession(user, request, response);
                return ResponseEntity.ok(Map.of("status", "success"));
            }
        } catch (Exception e) {
            return ErrorMessageUtils.unauthorized("We couldn't sign you in with that username and password. Double-check both fields and try again.");
        }
    }

    @PostMapping("/mfa/validate-login")
    public ResponseEntity<?> validateLoginMfa(@RequestBody MfaLoginRequest requestPayload, HttpServletRequest request, HttpServletResponse response) {
        String preAuthToken = requestPayload.getPre_auth_token();
        String code = requestPayload.getCode();

        User user = authenticationService.validatePreAuthToken(preAuthToken);
        if (user == null) {
            return ErrorMessageUtils.unauthorized("Your two-factor login session has expired or is no longer valid. Please sign in again.");
        }

        if (user.isMfaEnabled()) {
            if (twoFactorService.isOtpValid(user.getMfaSecret(), code)) {
                createSession(user, request, response);
                return ResponseEntity.ok(Map.of("status", "success"));
            } else {
                return ErrorMessageUtils.badRequest("That two-factor authentication code was not accepted. Check the current code in your authenticator app and try again.");
            }
        }

        return ErrorMessageUtils.badRequest("Two-factor authentication is not enabled for this account.");
    }

    private void createSession(User user, HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(true);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                user,
                null,
                user.getRoles().stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).collect(Collectors.toList())
        );
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        securityContextRepository.saveContext(context, request, response);
    }

    private void expireCookie(HttpServletResponse response, String name) {
        ResponseCookie expiredCookie = ResponseCookie.from(name, "")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expiredCookie.toString());
    }

}
