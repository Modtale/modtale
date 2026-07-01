package net.modtale.controller.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Map;
import net.modtale.exception.InvalidAuthenticationRequestException;
import net.modtale.exception.UnauthorizedException;
import net.modtale.model.dto.request.auth.ChangePasswordRequest;
import net.modtale.model.dto.request.auth.ForgotPasswordRequest;
import net.modtale.model.dto.request.auth.MfaLoginRequest;
import net.modtale.model.dto.request.auth.RegisterRequest;
import net.modtale.model.dto.request.auth.ResetPasswordRequest;
import net.modtale.model.dto.request.auth.SignInRequest;
import net.modtale.model.dto.request.auth.UpdateCredentialsRequest;
import net.modtale.model.dto.request.auth.VerifyMfaRequest;
import net.modtale.model.dto.response.auth.MfaChallengeResponse;
import net.modtale.model.dto.response.auth.MfaSetupResponse;
import net.modtale.model.dto.response.auth.RegistrationResponse;
import net.modtale.model.dto.response.auth.SignInResponse;
import net.modtale.model.dto.response.common.MessageResponse;
import net.modtale.model.dto.response.common.StatusResponse;
import net.modtale.model.user.User;
import net.modtale.service.auth.AuthenticationMutationService;
import net.modtale.service.auth.AuthenticationService;
import net.modtale.service.auth.TwoFactorService;
import net.modtale.service.security.access.AdminAuthorityUtils;
import net.modtale.service.user.account.AccountService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final AuthenticationMutationService authenticationMutationService;
    private final AccountService accountService;
    private final TwoFactorService twoFactorService;
    private final SecurityContextRepository securityContextRepository;

    public AuthController(
            AuthenticationService authenticationService,
            AuthenticationMutationService authenticationMutationService,
            AccountService accountService,
            TwoFactorService twoFactorService,
            SecurityContextRepository securityContextRepository
    ) {
        this.authenticationService = authenticationService;
        this.authenticationMutationService = authenticationMutationService;
        this.accountService = accountService;
        this.twoFactorService = twoFactorService;
        this.securityContextRepository = securityContextRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = authenticationService.registerUser(
                request.getUsername(),
                request.getEmail(),
                request.getPassword()
        );
        return ResponseEntity.ok(new RegistrationResponse("User registered successfully", user.getUsername()));
    }

    @PostMapping("/verify")
    public ResponseEntity<MessageResponse> verifyEmail(@RequestParam String token) {
        authenticationMutationService.verifyEmail(token);
        return ResponseEntity.ok(new MessageResponse("Email verified successfully"));
    }

    @PostMapping("/resend-verification")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_READ', authentication)")
    public ResponseEntity<MessageResponse> resendVerification() {
        User user = accountService.requireCurrentUser("requesting another verification email");
        authenticationMutationService.resendVerificationEmail(user);
        return ResponseEntity.ok(new MessageResponse("Verification email sent"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest requestPayload) {
        authenticationMutationService.initiatePasswordReset(requestPayload.getEmail());
        return ResponseEntity.ok(new MessageResponse("If an account exists for that email, a password reset link has been sent."));
    }

    @PostMapping("/logout")
    public ResponseEntity<StatusResponse> logout(HttpServletRequest request, HttpServletResponse response) {
        SecurityContextHolder.clearContext();
        securityContextRepository.saveContext(SecurityContextHolder.createEmptyContext(), request, response);

        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        expireCookie(response, "SESSION");
        expireCookie(response, "JSESSIONID");
        expireCookie(response, "XSRF-TOKEN");

        return ResponseEntity.ok(new StatusResponse("success"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authenticationMutationService.completePasswordReset(request.getToken(), request.getPassword());
        return ResponseEntity.ok(new MessageResponse("Password reset successfully. You can now login."));
    }

    @PutMapping("/credentials")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_READ', authentication)")
    public ResponseEntity<Void> updateCredentials(@Valid @RequestBody UpdateCredentialsRequest requestPayload) {
        User user = accountService.requireCurrentUser("updating your email or password");
        authenticationMutationService.addCredentials(user.getId(), requestPayload.getEmail(), requestPayload.getPassword());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/change-password")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_READ', authentication)")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest requestPayload) {
        User user = accountService.requireCurrentUser("changing your password");
        authenticationMutationService.changePassword(user.getId(), requestPayload.getCurrentPassword(), requestPayload.getNewPassword());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/password")
    @PreAuthorize("!@apiSecurity.isApiKey(authentication) && @apiSecurity.hasPersonalPerm('PROFILE_READ', authentication)")
    public ResponseEntity<Void> removePassword() {
        User user = accountService.requireCurrentUser("removing your password");
        authenticationMutationService.removePassword(user.getId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/mfa/setup")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_READ', authentication)")
    public ResponseEntity<MfaSetupResponse> setupMfa() {
        User user = accountService.requireCurrentUser("setting up two-factor authentication");
        if (user.isMfaEnabled()) {
            throw new InvalidAuthenticationRequestException("Two-factor authentication is already enabled for this account.");
        }

        String secret = twoFactorService.generateNewSecret();
        authenticationMutationService.setTempMfaSecret(user.getId(), secret);

        String qrCode = twoFactorService.generateQrCodeImageUri(secret, user.getUsername());
        return ResponseEntity.ok(new MfaSetupResponse(secret, qrCode));
    }

    @PostMapping("/mfa/verify")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_READ', authentication)")
    public ResponseEntity<MessageResponse> verifyMfaSetup(@Valid @RequestBody VerifyMfaRequest requestPayload) {
        User user = accountService.requireCurrentUser("verifying two-factor authentication setup");
        if (!twoFactorService.isOtpValid(user.getMfaSecret(), requestPayload.getCode())) {
            throw new InvalidAuthenticationRequestException("That verification code was not accepted, so two-factor authentication was not enabled.");
        }
        authenticationMutationService.enableMfa(user.getId());
        return ResponseEntity.ok(new MessageResponse("MFA enabled successfully"));
    }

    @PostMapping("/signin")
    public ResponseEntity<SignInResponse> login(@Valid @RequestBody SignInRequest requestPayload, HttpServletRequest request, HttpServletResponse response) {
        User user = authenticationService.authenticate(requestPayload.getUsername(), requestPayload.getPassword());

        if (user.isMfaEnabled()) {
            String preAuthToken = authenticationService.generatePreAuthToken(user.getId());
            return ResponseEntity.accepted().body(new SignInResponse("mfa_required", true, preAuthToken));
        }

        createSession(user, request, response);
        return ResponseEntity.ok(new SignInResponse("success", false, null));
    }

    @PostMapping("/mfa/validate-login")
    public ResponseEntity<StatusResponse> validateLoginMfa(@Valid @RequestBody MfaLoginRequest requestPayload, HttpServletRequest request, HttpServletResponse response) {
        User user = authenticationService.validatePreAuthToken(requestPayload.getPre_auth_token());
        if (user == null) {
            throw new UnauthorizedException("Your two-factor login session has expired or is no longer valid. Please sign in again.");
        }

        if (!user.isMfaEnabled()) {
            throw new InvalidAuthenticationRequestException("Two-factor authentication is not enabled for this account.");
        }

        if (!twoFactorService.isOtpValid(user.getMfaSecret(), requestPayload.getCode())) {
            throw new InvalidAuthenticationRequestException("That two-factor authentication code was not accepted. Check the current code in your authenticator app and try again.");
        }

        createSession(user, request, response);
        return ResponseEntity.ok(new StatusResponse("success"));
    }

    private void createSession(User user, HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(true);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                user,
                null,
                AdminAuthorityUtils.authoritiesFor(user)
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
