package net.modtale.config.security;

import java.util.List;
import java.util.Set;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** Keeps browser sessions private while allowing third-party API-key clients. */
final class ApiCorsPolicy {
    private static final List<String> RESTRICTED_PATHS = List.of(
            "/api/v1/admin/**",
            "/api/v1/user/api-keys/**",
            "/api/v1/user/analytics",
            "/api/v1/projects/*/publish",
            "/api/v1/analytics/view/**",
            "/api/v1/views/project/**",
            "/api/v1/user/repos/**",
            "/api/v1/orgs/*/repos/**",
            "/api/v1/user/connections/**",
            "/api/v1/orgs/*/connections/**"
    );

    private ApiCorsPolicy() {
    }

    static CorsConfigurationSource create(Set<String> frontendOrigins) {
        CorsConfiguration restricted = new CorsConfiguration();
        restricted.setAllowedOriginPatterns(List.copyOf(frontendOrigins));
        restricted.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH"));
        restricted.setAllowedHeaders(List.of("Authorization", "Cache-Control", "Content-Type", "X-XSRF-TOKEN"));
        restricted.setAllowCredentials(true);
        restricted.setMaxAge(3600L);

        CorsConfiguration frontend = new CorsConfiguration(restricted);
        frontend.addAllowedHeader("X-Modtale-Key");
        frontend.setExposedHeaders(List.of("X-XSRF-TOKEN", "X-RateLimit-Limit", "X-RateLimit-Remaining", "X-RateLimit-Tier"));

        CorsConfiguration publicApi = new CorsConfiguration(frontend);
        publicApi.setAllowedOriginPatterns(List.of());
        publicApi.setAllowedOrigins(List.of("*"));
        publicApi.setAllowCredentials(false);
        publicApi.setAllowedHeaders(List.of("Authorization", "Cache-Control", "Content-Type", "X-Modtale-Key"));
        publicApi.setExposedHeaders(List.of("X-RateLimit-Limit", "X-RateLimit-Remaining", "X-RateLimit-Tier"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        for (String path : RESTRICTED_PATHS) {
            source.registerCorsConfiguration(path, restricted);
        }
        source.registerCorsConfiguration("/**", publicApi);

        return request -> {
            // Never reflect arbitrary origins alongside Access-Control-Allow-Credentials.
            String origin = request.getHeader("Origin");
            CorsConfiguration configuration = source.getCorsConfiguration(request);
            return configuration == publicApi && origin != null && frontend.checkOrigin(origin) != null
                    ? frontend : configuration;
        };
    }
}
