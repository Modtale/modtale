package net.modtale.config.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;

@Configuration
public class SecurityConfig {

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Autowired
    private ApiKeyAuthFilter apiKeyAuthFilter;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    @Autowired
    private CustomOAuth2UserService customOAuth2UserService;

    @Autowired
    private CustomOidcUserService customOidcUserService;

    @Autowired
    private OAuth2AuthorizedClientRepository authorizedClientRepository;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository tokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        requestHandler.setCsrfRequestAttributeName(null);

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                .csrf(csrf -> csrf
                        .csrfTokenRepository(tokenRepository)
                        .csrfTokenRequestHandler(requestHandler)
                        .ignoringRequestMatchers("/api/v1/user/api-keys/**")
                )

                .addFilterBefore(rateLimitFilter, OAuth2LoginAuthenticationFilter.class)
                .addFilterBefore(apiKeyAuthFilter, OAuth2LoginAuthenticationFilter.class)

                .securityContext(sc -> sc.securityContextRepository(securityContextRepository()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation().migrateSession()
                )
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService)
                                .oidcUserService(customOidcUserService)
                        )
                        .authorizedClientRepository(authorizedClientRepository)
                        .successHandler(oauthSuccessHandler())
                        .failureHandler(oauthFailureHandler())
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/oauth2/**", "/login**", "/error", "/logout").permitAll()
                        .requestMatchers("/sitemap.xml", "/actuator/health").permitAll()
                        .requestMatchers("/client-metadata.json").permitAll()

                        .requestMatchers(HttpMethod.GET, "/api/v1/projects/**", "/api/v1/tags", "/api/v1/user/repos", "/api/v1/files/**", "/api/v1/user/profile/**").permitAll()

                        .requestMatchers(
                                "/api/v1/user/analytics",
                                "/api/v1/projects/*/analytics",
                                "/api/v1/analytics/view/**",
                                "/api/v1/user/api-keys/**",
                                "/api/v1/admin/**"
                        ).access((authentication, context) -> {
                            boolean isApiKeyUser = authentication.get().getAuthorities().stream()
                                    .anyMatch(a -> a.getAuthority().equals("ROLE_API"));

                            if (isApiKeyUser) return new AuthorizationDecision(false);

                            String path = context.getRequest().getRequestURI();
                            if (path.contains("/analytics/view/")) {
                                return new AuthorizationDecision(true);
                            }

                            return new AuthorizationDecision(authentication.get().isAuthenticated());
                        })

                        .requestMatchers(
                                "/api/v1/upload/**",
                                "/api/v1/user/me",
                                "/api/v1/user/settings/**",
                                "/api/v1/projects/*/favorite",
                                "/api/v1/projects/*/reviews"
                        ).authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/projects/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/projects/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/projects/**").authenticated()

                        .requestMatchers("/api/**").authenticated()

                        .anyRequest().permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID", "XSRF-TOKEN", "SESSION")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler())
                ).exceptionHandling(exception -> exception
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                );
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        CorsConfiguration restrictedConfig = new CorsConfiguration();
        List<String> restrictedOrigins = new ArrayList<>();

        if (frontendUrl != null && !frontendUrl.isBlank()) {
            restrictedOrigins.add(frontendUrl);
        }

        restrictedConfig.setAllowedOriginPatterns(restrictedOrigins);
        restrictedConfig.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH"));
        restrictedConfig.setAllowedHeaders(Arrays.asList("Authorization", "Cache-Control", "Content-Type", "X-Xsrf-Token"));
        restrictedConfig.setAllowCredentials(true);
        restrictedConfig.setMaxAge(3600L);

        source.registerCorsConfiguration("/api/v1/admin/**", restrictedConfig);
        source.registerCorsConfiguration("/api/v1/user/api-keys/**", restrictedConfig);
        source.registerCorsConfiguration("/api/v1/user/analytics", restrictedConfig);
        source.registerCorsConfiguration("/api/v1/projects/*/analytics", restrictedConfig);
        source.registerCorsConfiguration("/api/v1/projects/*/publish", restrictedConfig);
        source.registerCorsConfiguration("/api/v1/analytics/view/**", restrictedConfig);

        CorsConfiguration publicConfig = new CorsConfiguration();
        publicConfig.setAllowedOriginPatterns(Collections.singletonList("*"));
        publicConfig.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH"));
        publicConfig.setAllowedHeaders(Arrays.asList("Authorization", "Cache-Control", "Content-Type", "X-Xsrf-Token", "X-Modtale-Key"));
        publicConfig.setExposedHeaders(Arrays.asList("X-Xsrf-Token", "X-RateLimit-Limit", "X-RateLimit-Remaining", "X-RateLimit-Tier"));
        publicConfig.setAllowCredentials(true);
        publicConfig.setMaxAge(3600L);

        source.registerCorsConfiguration("/**", publicConfig);

        return source;
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public AuthenticationSuccessHandler oauthSuccessHandler() {
        return (request, response, authentication) -> {
            HttpSession session = request.getSession(false);
            SecurityContextRepository repository = securityContextRepository();
            repository.saveContext(SecurityContextHolder.getContext(), request, response);
            response.sendRedirect(frontendUrl + "/dashboard/profile");
        };
    }

    @Bean
    public AuthenticationFailureHandler oauthFailureHandler() {
        return (request, response, exception) -> {
            String errorParam = java.net.URLEncoder.encode(exception.getMessage(), "UTF-8");
            response.sendRedirect(frontendUrl + "/?oauth_error=" + errorParam);
        };
    }
}