package net.modtale.service.security.access;

import java.util.ArrayList;
import java.util.List;
import net.modtale.model.user.AdminPermission;
import net.modtale.model.user.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public final class AdminAuthorityUtils {
    public static final String ADMIN_PERMISSION_AUTHORITY_PREFIX = "ADMIN_PERMISSION_";

    private AdminAuthorityUtils() {}

    public static List<GrantedAuthority> authoritiesFor(User user) {
        List<GrantedAuthority> authorities = new ArrayList<>();

        if (user != null && user.getRoles() != null) {
            user.getRoles().stream()
                    .filter(role -> role != null && !role.isBlank())
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .forEach(authorities::add);
        }

        AdminPermission.effectivePermissions(user).stream()
                .map(permission -> new SimpleGrantedAuthority(ADMIN_PERMISSION_AUTHORITY_PREFIX + permission.name()))
                .forEach(authorities::add);

        return authorities;
    }
}
