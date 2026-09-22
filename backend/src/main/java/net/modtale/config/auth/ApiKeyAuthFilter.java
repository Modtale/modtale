package net.modtale.config.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.modtale.exception.UnauthorizedException;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.service.auth.ApiKeyService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final ApiKeyService apiKeyService;
    private final HandlerExceptionResolver exceptionResolver;

    public ApiKeyAuthFilter(
            @Lazy ApiKeyService apiKeyService,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver
    ) {
        this.apiKeyService = apiKeyService;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String apiKeyHeader = request.getHeader("X-MODTALE-KEY");

        if ((path.equals("/api/v1") || path.startsWith("/api/v1/")) && apiKeyHeader != null) {
            ApiKey apiKey = apiKeyHeader.isBlank() ? null : apiKeyService.resolveKey(apiKeyHeader);
            User user = apiKey == null ? null : apiKeyService.getUserFromKey(apiKey);
            if (user == null) {
                // A supplied credential must never fall back to an existing browser session.
                SecurityContextHolder.clearContext();
                exceptionResolver.resolveException(request, response, null, new UnauthorizedException("Invalid API Key."));
                return;
            }

            List<GrantedAuthority> authorities = new ArrayList<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_API"));
            Map<String, Set<ApiKey.ApiPermission>> perms = apiKey.getContextPermissions();
            if (perms != null) {
                for (Map.Entry<String, Set<ApiKey.ApiPermission>> entry : perms.entrySet()) {
                    String contextId = entry.getKey();
                    for (ApiKey.ApiPermission permission : entry.getValue()) {
                        authorities.add(new SimpleGrantedAuthority("SCOPE_" + contextId + "_" + permission.name()));
                    }
                }
            }

            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    user, null, authorities
            );
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
        }

        filterChain.doFilter(request, response);
    }
}
