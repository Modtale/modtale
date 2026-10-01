package net.modtale.controller;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import net.modtale.exception.ApiKeyOperationForbiddenException;
import net.modtale.model.jam.Modjam;
import net.modtale.model.user.User;
import net.modtale.service.ModjamService;
import net.modtale.service.security.access.AccessControlService;
import net.modtale.service.user.account.AccountService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ModjamControllerSessionTest {
    private ModjamController controller;
    private ModjamService modjamService;
    private AccountService accountService;
    private AccessControlService accessControlService;

    @BeforeEach
    void setUp() {
        controller = new ModjamController();
        modjamService = mock(ModjamService.class);
        accountService = mock(AccountService.class);
        accessControlService = mock(AccessControlService.class);
        ReflectionTestUtils.setField(controller, "modjamService", modjamService);
        ReflectionTestUtils.setField(controller, "accountService", accountService);
        ReflectionTestUtils.setField(controller, "accessControlService", accessControlService);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void everyMutationRejectsApiKeysBeforeReadingUserOrCallingService() {
        var authentication = new UsernamePasswordAuthenticationToken("owner", null,
                List.of(new SimpleGrantedAuthority("ROLE_API")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(accessControlService.isApiKey(authentication)).thenReturn(true);

        int checked = 0;
        for (var method : ModjamController.class.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(PostMapping.class)
                    && !method.isAnnotationPresent(PutMapping.class)
                    && !method.isAnnotationPresent(DeleteMapping.class)) continue;
            Object[] arguments = new Object[method.getParameterCount()];
            var error = assertThrows(InvocationTargetException.class,
                    () -> method.invoke(controller, arguments), method.getName());
            assertInstanceOf(ApiKeyOperationForbiddenException.class, error.getCause(), method.getName());
            checked++;
        }
        assertTrue(checked >= 13);
        verifyNoInteractions(accountService, modjamService);
    }

    @Test
    void hostedJamListRequiresBrowserSession() {
        var authentication = new UsernamePasswordAuthenticationToken("owner", null,
                List.of(new SimpleGrantedAuthority("ROLE_API")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(accessControlService.isApiKey(authentication)).thenReturn(true);
        assertThrows(ApiKeyOperationForbiddenException.class, controller::getMyJams);
        verifyNoInteractions(accountService, modjamService);
    }

    @Test
    void browserMutationUsesAuthenticatedUser() {
        User user = new User();
        user.setId("user-1");
        user.setUsername("creator");
        when(accountService.getCurrentUser()).thenReturn(user);
        Modjam jam = new Modjam();
        controller.createJam(jam);
        verify(modjamService).createJam(jam, "user-1", "creator");
    }

    @Test
    void anonymousMutationCannotCallService() {
        assertEquals(401, controller.createJam(new Modjam()).getStatusCode().value());
        verifyNoInteractions(modjamService);
    }
}
