package net.modtale.config.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.service.auth.ApiKeyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    @Autowired
    @Lazy
    private ApiKeyService apiKeyService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String apiKeyHeader = request.getHeader("X-MODTALE-KEY");

        if (path.startsWith("/api/v1") && apiKeyHeader != null && !apiKeyHeader.isBlank()) {

            ApiKey apiKey = apiKeyService.resolveKey(apiKeyHeader);

            if (apiKey != null) {
                User user = apiKeyService.getUserFromKey(apiKey);
                if (user != null) {
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            user,
                            null,
                            Collections.singletonList(new SimpleGrantedAuthority("ROLE_API"))
                    );
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } else {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Unauthorized\", \"message\": \"Invalid API Key.\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}