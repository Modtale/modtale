package net.modtale.controller.admin;

import net.modtale.service.project.query.ProjectService;
import net.modtale.service.storage.StorageService;
import net.modtale.service.security.scan.WardenClientService;
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
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(ArtifactInspectionAuthorizationTest.Config.class)
class ArtifactInspectionAuthorizationTest {
    @Configuration @EnableMethodSecurity static class Config {
        @Bean ProjectService projects() {return mock(ProjectService.class);}
        @Bean StorageService storage() {return mock(StorageService.class);}
        @Bean WardenClientService inspector() {return mock(WardenClientService.class);}
        @Bean(name="apiSecurity") PermissionEvaluator permissionEvaluator() {return new PermissionEvaluator();}
        @Bean ArtifactInspectionController controller(ProjectService projects, StorageService storage, WardenClientService inspector) {
            return new ArtifactInspectionController(projects,storage,inspector);
        }
    }
    public static class PermissionEvaluator {
        public boolean hasAdminPermission(String permission, Authentication auth) {
            return auth!=null && auth.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals(permission));
        }
    }
    @Autowired ArtifactInspectionController controller;
    @Autowired ProjectService projects;
    @Autowired StorageService storage;
    @Autowired WardenClientService inspector;
    @AfterEach void clearContext() {SecurityContextHolder.clearContext();}
    @Test void comparisonAndSourceInspectionRequireReviewReadPermission() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("ordinary-user",null,
                List.of(new SimpleGrantedAuthority("PROJECT_REVIEW_DECIDE"))));
        assertThrows(AccessDeniedException.class,()->controller.structure("project","version",null));
        assertThrows(AccessDeniedException.class,()->controller.file("project","version","manifest.json",null));
        assertThrows(AccessDeniedException.class,()->controller.window("project","version","manifest.json",0,32000,0,null,null));
        assertThrows(AccessDeniedException.class,()->controller.changes("project","version",null));
        verifyNoInteractions(projects,storage,inspector);
    }
}
