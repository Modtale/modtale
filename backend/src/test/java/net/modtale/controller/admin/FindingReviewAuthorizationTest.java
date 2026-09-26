package net.modtale.controller.admin;

import java.util.List;
import net.modtale.model.user.User;
import net.modtale.service.security.issue.FindingReviewService;
import net.modtale.service.user.account.AccountService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(FindingReviewAuthorizationTest.Config.class)
class FindingReviewAuthorizationTest {
    @Configuration @EnableMethodSecurity static class Config {
        @Bean FindingReviewService reviews() { return mock(FindingReviewService.class); }
        @Bean AccountService accounts() { return mock(AccountService.class); }
        @Bean(name="apiSecurity") Permissions permissions() { return new Permissions(); }
        @Bean FindingReviewController controller(FindingReviewService reviews, AccountService accounts) { return new FindingReviewController(reviews, accounts); }
    }
    public static class Permissions {
        public boolean hasAdminPermission(String permission, Authentication auth) {
            return auth != null && auth.getAuthorities().stream().anyMatch(a -> permission.equals(a.getAuthority()));
        }
    }
    @Autowired FindingReviewController controller;
    @Autowired FindingReviewService reviews;
    @Autowired AccountService accounts;
    @BeforeEach void resetMocks() { reset(reviews, accounts); }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private void permission(String permission) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user", null,
                List.of(new SimpleGrantedAuthority(permission))));
    }
    @Test void readPermissionCannotWriteOrRevoke() {
        permission("PROJECT_REVIEW_READ");
        assertThrows(AccessDeniedException.class, () -> controller.record("p", "v", "token", null));
        assertThrows(AccessDeniedException.class, () -> controller.revoke("p", "v", "decision", "token", null));
        verifyNoInteractions(reviews, accounts);
        controller.history("p", "v", "token", 0);
        verify(reviews).history("p", "v", "token", 0);
    }
    @Test void decisionPermissionUsesAuthenticatedActorAndSnapshot() {
        permission("PROJECT_REVIEW_DECIDE");
        var actor = new User(); actor.setId("server-actor");
        when(accounts.requireCurrentUser(anyString())).thenReturn(actor);
        var request = new FindingReviewService.Request(2, FindingReviewService.Disposition.REQUIRE_REVIEW, "Needs further investigation");
        controller.record("p", "v", "snapshot", request);
        verify(reviews).record("p", "v", "snapshot", "server-actor", request);
        controller.revoke("p", "v", "decision", "snapshot", new FindingReviewController.Revocation("Incorrect previous decision"));
        verify(reviews).revoke("p", "v", "snapshot", "server-actor", "decision", "Incorrect previous decision");
        assertThrows(AccessDeniedException.class, () -> controller.history("p", "v", "snapshot", 0));
    }
}
