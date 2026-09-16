package net.modtale.controller.admin;

import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.scan.DependencyReviewSource;
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
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(DependencyInspectionAuthorizationTest.Config.class)
class DependencyInspectionAuthorizationTest {
    @Configuration @EnableMethodSecurity static class Config {
        @Bean ProjectService projects(){return mock(ProjectService.class);}
        @Bean DependencyReviewSource source(){return mock(DependencyReviewSource.class);}
        @Bean(name="apiSecurity") PermissionEvaluator permissions(){return new PermissionEvaluator();}
        @Bean DependencyInspectionController controller(ProjectService projects,DependencyReviewSource source){return new DependencyInspectionController(projects,()->source);}
    }
    public static class PermissionEvaluator {
        public boolean hasAdminPermission(String permission,Authentication auth) {
            return auth!=null&&auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals(permission));
        }
    }
    @Autowired DependencyInspectionController controller;
    @Autowired ProjectService projects;
    @Autowired DependencyReviewSource source;
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();reset(projects,source);}
    @Test void unrelatedOrDecisionOnlyPermissionCannotReadDependencies() {
        for(String permission:List.of("PROJECT_REVIEW_DECIDE","PROJECT_EDIT","ROLE_USER")) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user",null,List.of(new SimpleGrantedAuthority(permission))));
            assertThrows(AccessDeniedException.class,()->controller.inspect("p","v",null));
        }
        verifyNoInteractions(projects,source);
    }
    @Test void reviewReadPermissionEntersSnapshotValidation() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("reviewer",null,List.of(new SimpleGrantedAuthority("PROJECT_REVIEW_READ"))));
        assertEquals(404,assertThrows(ResponseStatusException.class,()->controller.inspect("p","v",null)).getStatusCode().value());
        verify(projects).getRawProjectById("p");verifyNoInteractions(source);
    }
}
