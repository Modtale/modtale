package net.modtale.config.security;

import net.modtale.model.resources.Mod;
import net.modtale.model.user.User;
import net.modtale.service.resources.ModService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("apiSecurity")
public class ApiSecurityEvaluator {

    @Autowired
    private ModService modService;

    public boolean hasProjectPerm(String projectId, String perm, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return false;
        if (auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_API"))) return true;

        Mod mod = modService.getModById(projectId);
        if (mod == null) return false;

        User user = (User) auth.getPrincipal();
        String contextId = mod.getAuthorId().equals(user.getId()) ? "PERSONAL" : mod.getAuthorId();
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("SCOPE_" + contextId + "_" + perm));
    }

    public boolean hasOrgPerm(String orgId, String perm, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return false;
        if (auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_API"))) return true;

        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("SCOPE_" + orgId + "_" + perm));
    }

    public boolean hasPersonalPerm(String perm, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return false;
        if (auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_API"))) return true;

        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("SCOPE_PERSONAL_" + perm));
    }

    public boolean hasCreateProjectPerm(String ownerParam, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return false;
        if (auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_API"))) return true;

        User user = (User) auth.getPrincipal();
        String contextId = (ownerParam == null || ownerParam.isEmpty() || ownerParam.equals(user.getId())) ? "PERSONAL" : ownerParam;
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("SCOPE_" + contextId + "_PROJECT_CREATE"));
    }

    public boolean hasAnyPerm(String permSuffix, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return false;
        if (auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_API"))) return true;

        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().endsWith("_" + permSuffix));
    }
}