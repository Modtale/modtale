package net.modtale.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class StatusWebConfig {

    private final StatusServiceProperties properties;

    public StatusWebConfig(StatusServiceProperties properties) {
        this.properties = properties;
    }

    @Bean
    public SecurityFilterChain statusSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/status/refresh").authenticated()
                        .anyRequest().permitAll());
        if (!properties.getRefreshAudience().isBlank() && !properties.getRefreshServiceAccount().isBlank()) {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs").build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer("https://accounts.google.com"),
                    this::validateSchedulerIdentity));
            http.oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.decoder(decoder)));
        } else if (properties.isExternalRefresh()) {
            throw new IllegalStateException("External status refresh requires an audience and scheduler service account");
        }
        return http.build();
    }

    OAuth2TokenValidatorResult validateSchedulerIdentity(org.springframework.security.oauth2.jwt.Jwt jwt) {
        return jwt.getAudience().contains(properties.getRefreshAudience())
                && properties.getRefreshServiceAccount().equals(jwt.getClaimAsString("email"))
                && Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
    }

    @Bean
    public CorsConfigurationSource statusCorsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = properties.getCorsAllowedOrigins();
        configuration.setAllowedOriginPatterns(origins == null || origins.isEmpty() ? List.of("*") : origins);
        configuration.setAllowedMethods(List.of("GET", "HEAD", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Accept", "Content-Type", "Cache-Control"));
        configuration.setMaxAge(300L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/status/**", configuration);
        return source;
    }

    @Bean
    public ObjectMapper statusObjectMapper() {
        return JsonMapper.builder()
                .findAndAddModules()
                .build();
    }
}
