package net.modtale.config.security;

import net.modtale.config.security.auth.CustomOAuth2UserService;
import net.modtale.config.security.auth.CustomOidcUserService;
import net.modtale.service.security.CustomUserDetailsService;
import net.modtale.service.user.UserService;
import net.modtale.model.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

@Configuration
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Autowired private ApiKeyAuthFilter apiKeyAuthFilter;
    @Autowired private RateLimitFilter rateLimitFilter;
    @Autowired private CustomOAuth2UserService customOAuth2UserService;
    @Autowired private CustomOidcUserService customOidcUserService;
    @Autowired private OAuth2AuthorizedClientRepository authorizedClientRepository;
    @Autowired private CustomUserDetailsService userDetailsService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserService userService;

    @PostConstruct
    public void logConfig() {
        logger.info("Security Config Initialized. Frontend URL: {}", frontendUrl);
    }

    private String getCleanFrontendUrl() {
        if (frontendUrl != null && frontendUrl.endsWith("/")) {
            return frontendUrl.substring(0, frontendUrl.length() - 1);
        }
        return frontendUrl;
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    private boolean isPreviewEnvironment() {
        String cleanUrl = getCleanFrontendUrl();
        if (cleanUrl == null || cleanUrl.isBlank()) return false;
        try {
            String host = URI.create(cleanUrl).getHost();
            return (host != null && host.endsWith(".run.app")) || "dev.modtale.net".equalsIgnoreCase(host);
        } catch (Exception e) {
            return false;
        }
    }

    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setUseSecureCookie(true);
        serializer.setCookiePath("/");

        boolean isPreview = isPreviewEnvironment();
        String cleanUrl = getCleanFrontendUrl();

        if (isPreview) {
            serializer.setSameSite("None");
        } else {
            serializer.setSameSite("Lax");

            if (cleanUrl != null && !cleanUrl.isBlank()) {
                try {
                    String host = URI.create(cleanUrl).getHost();
                    if (host != null && !host.equalsIgnoreCase("localhost")) {
                        String[] parts = host.split("\\.");
                        if (parts.length >= 2) {
                            String rootDomain = parts[parts.length - 2] + "." + parts[parts.length - 1];
                            serializer.setDomainName(rootDomain);
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse frontend URL for cookie domain: {}", e.getMessage());
                }
            }
        }
        return serializer;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository tokenRepository = new CookieCsrfTokenRepository();
        tokenRepository.setCookieHttpOnly(false);
        tokenRepository.setSecure(true);
        tokenRepository.setCookiePath("/");

        tokenRepository.setCookieCustomizer(cookie -> {
            boolean isPreview = isPreviewEnvironment();
            String cleanUrl = getCleanFrontendUrl();

            if (isPreview) {
                cookie.sameSite("None");
                cookie.domain(null);
            } else {
                cookie.sameSite("Lax");
                if (cleanUrl != null && !cleanUrl.isBlank()) {
                    try {
                        String host = URI.create(cleanUrl).getHost();
                        if (host != null && !host.equalsIgnoreCase("localhost")) {
                            String[] parts = host.split("\\.");
                            if (parts.length >= 2) {
                                String rootDomain = parts[parts.length - 2] + "." + parts[parts.length - 1];
                                cookie.domain(rootDomain);
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to set CSRF cookie domain: {}", e.getMessage());
                    }
                }
            }
        });

        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        requestHandler.setCsrfRequestAttributeName(null);

        http
                .authenticationProvider(authenticationProvider())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> {
                    csrf
                            .csrfTokenRepository(tokenRepository)
                            .csrfTokenRequestHandler(requestHandler);

                    csrf.ignoringRequestMatchers("/api/v1/user/api-keys/**", "/api/v1/auth/**");
                    csrf.ignoringRequestMatchers("/api/v1/users/batch");
                    csrf.ignoringRequestMatchers(request -> request.getHeader("X-MODTALE-KEY") != null);

                    if (isPreviewEnvironment()) {
                        logger.warn("SECURITY WARNING: Disabling CSRF protection for Staging/Preview environment to allow cross-site requests.");
                        csrf.ignoringRequestMatchers("/**");
                    }
                })
                .addFilterBefore(rateLimitFilter, OAuth2LoginAuthenticationFilter.class)
                .addFilterBefore(apiKeyAuthFilter, OAuth2LoginAuthenticationFilter.class)
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .securityContext(sc -> sc.securityContextRepository(securityContextRepository()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation().migrateSession()
                )
                .formLogin(form -> form
                        .loginProcessingUrl("/api/v1/auth/login-legacy")
                        .permitAll()
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
                        .requestMatchers(
                                "/api/v1/auth/register",
                                "/api/v1/auth/verify",
                                "/api/v1/auth/signin",
                                "/api/v1/auth/mfa/validate-login",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password"
                        ).permitAll()
                        .requestMatchers("/sitemap.xml", "/actuator/health").permitAll()
                        .requestMatchers("/client-metadata.json").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/projects/**",
                                "/api/v1/tags",
                                "/api/v1/files/**",
                                "/api/v1/user/profile/**",
                                "/api/v1/users/search",
                                "/api/v1/users/*/organizations",
                                "/api/v1/users/*/following",
                                "/api/v1/users/*/followers",
                                "/api/v1/orgs/*/members",
                                "/api/v1/creators/**",
                                "/api/v1/og/**",
                                "/api/v1/download/**",
                                "/api/v1/meta/**",
                                "/api/v1/status",
                                "/api/v1/version/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/api/v1/projects/**", "/api/v1/tags", "/api/v1/files/**", "/api/v1/user/profile/**", "/api/v1/og/**").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/users/batch"
                        ).permitAll()
                        .requestMatchers("/api/v1/analytics/view/**").access((authentication, context) -> {
                            boolean isApiKeyUser = authentication.get().getAuthorities().stream()
                                    .anyMatch(a -> a.getAuthority().equals("ROLE_API"));
                            if (isApiKeyUser) return new AuthorizationDecision(false);

                            HttpServletRequest request = context.getRequest();
                            String origin = request.getHeader("Origin");
                            String referer = request.getHeader("Referer");
                            String cleanUrl = getCleanFrontendUrl();
                            String validHost = cleanUrl != null ? URI.create(cleanUrl).getHost() : "localhost";

                            boolean isValidOrigin = (origin != null && origin.contains(validHost));
                            boolean isValidReferer = (referer != null && referer.contains(validHost));

                            if (isPreviewEnvironment() && (origin != null && origin.contains(".run.app"))) {
                                return new AuthorizationDecision(true);
                            }

                            return new AuthorizationDecision(isValidOrigin || isValidReferer);
                        })
                        .requestMatchers(
                                "/api/v1/user/analytics",
                                "/api/v1/user/api-keys/**",
                                "/api/v1/admin/**"
                        ).access((authentication, context) -> {
                            boolean isApiKeyUser = authentication.get().getAuthorities().stream()
                                    .anyMatch(a -> a.getAuthority().equals("ROLE_API"));
                            if (isApiKeyUser) return new AuthorizationDecision(false);

                            return new AuthorizationDecision(authentication.get().isAuthenticated());
                        })
                        .requestMatchers(
                                "/api/v1/upload/**",
                                "/api/v1/user/me",
                                "/api/v1/user/settings/**",
                                "/api/v1/user/repos/**",
                                "/api/v1/projects/*/favorite",
                                "/api/v1/projects/*/reviews",
                                "/api/v1/auth/mfa/setup",
                                "/api/v1/auth/mfa/verify",
                                "/api/v1/auth/resend-verification",
                                "/api/v1/auth/change-password",
                                "/api/v1/auth/credentials"
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
                )
                .exceptionHandling(exception -> exception
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.sendError(HttpStatus.FORBIDDEN.value(), "Access Denied: " + accessDeniedException.getMessage());
                        })
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                );
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration restrictedConfig = new CorsConfiguration();
        List<String> restrictedOrigins = new ArrayList<>();

        boolean isPreview = isPreviewEnvironment();
        String cleanUrl = getCleanFrontendUrl();

        if (isPreview) {
            restrictedOrigins.add("https://*.run.app");
            if (cleanUrl != null && cleanUrl.contains("dev.modtale.net")) {
                restrictedOrigins.add(cleanUrl);
            }
        } else {
            if (cleanUrl != null && !cleanUrl.isBlank()) {
                restrictedOrigins.add(cleanUrl);
            }
        }

        restrictedConfig.setAllowedOriginPatterns(restrictedOrigins);
        restrictedConfig.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH"));
        restrictedConfig.setAllowedHeaders(Arrays.asList("Authorization", "Cache-Control", "Content-Type", "X-Xsrf-Token", "X-XSRF-TOKEN"));
        restrictedConfig.setAllowCredentials(true);
        restrictedConfig.setMaxAge(3600L);

        source.registerCorsConfiguration("/api/v1/admin/**", restrictedConfig);
        source.registerCorsConfiguration("/api/v1/user/api-keys/**", restrictedConfig);
        source.registerCorsConfiguration("/api/v1/user/analytics", restrictedConfig);
        source.registerCorsConfiguration("/api/v1/projects/*/publish", restrictedConfig);
        source.registerCorsConfiguration("/api/v1/analytics/view/**", restrictedConfig);
        source.registerCorsConfiguration("/api/v1/user/repos/**", restrictedConfig);
        source.registerCorsConfiguration("/api/v1/orgs/*/repos/**", restrictedConfig);
        source.registerCorsConfiguration("/api/v1/user/connections/**", restrictedConfig);
        source.registerCorsConfiguration("/api/v1/orgs/*/connections/**", restrictedConfig);

        CorsConfiguration publicConfig = new CorsConfiguration();
        List<String> publicOrigins = new ArrayList<>();
        publicOrigins.add("*");
        if (cleanUrl != null && !cleanUrl.isBlank()) {
            publicOrigins.add(cleanUrl);
        }
        if (isPreview) {
            publicOrigins.add("https://*.run.app");
        }
        publicConfig.setAllowedOriginPatterns(publicOrigins);
        publicConfig.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH"));
        publicConfig.setAllowedHeaders(Arrays.asList("Authorization", "Cache-Control", "Content-Type", "X-Xsrf-Token", "X-XSRF-TOKEN", "X-Modtale-Key"));
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
            OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();
            String login = oauthUser.getAttribute("login");
            if (login == null) {
                login = oauthUser.getAttribute("username");
            }

            if (authentication instanceof OAuth2AuthenticationToken) {
                OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
                String registrationId = oauthToken.getAuthorizedClientRegistrationId();
                if ("gitlab".equals(registrationId)) {
                    OAuth2AuthorizedClient client = authorizedClientRepository.loadAuthorizedClient(
                            registrationId, oauthToken, request);
                    if (client != null) {
                        String accessToken = client.getAccessToken().getTokenValue();
                        String refreshToken = client.getRefreshToken() != null ? client.getRefreshToken().getTokenValue() : null;
                        LocalDateTime expiresAt = client.getAccessToken().getExpiresAt() != null ?
                                LocalDateTime.ofInstant(client.getAccessToken().getExpiresAt(), ZoneId.systemDefault()) : null;

                        User user = userService.getPublicProfile(login);
                        if (user != null) {
                            userService.updateProviderTokens(user.getId(), "gitlab", accessToken, refreshToken, expiresAt);
                        }
                    }
                }
            }

            User user = userService.getPublicProfile(login);
            boolean isLinking = Boolean.TRUE.equals(oauthUser.getAttribute("is_linking"));

            if (user != null && user.isMfaEnabled() && !isLinking) {
                String preAuthToken = userService.generatePreAuthToken(user.getId());

                SecurityContextHolder.clearContext();

                SecurityContextRepository repository = securityContextRepository();
                repository.saveContext(SecurityContextHolder.createEmptyContext(), request, response);

                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }

                String cleanUrl = getCleanFrontendUrl();
                response.sendRedirect((cleanUrl != null ? cleanUrl : "") + "/mfa?token=" + preAuthToken);
            } else {
                SecurityContextRepository repository = securityContextRepository();
                repository.saveContext(SecurityContextHolder.getContext(), request, response);

                String cleanUrl = getCleanFrontendUrl();
                response.sendRedirect((cleanUrl != null ? cleanUrl : "") + "/dashboard/profile");
            }
        };
    }

    @Bean
    public AuthenticationFailureHandler oauthFailureHandler() {
        return (request, response, exception) -> {
            String errorParam = java.net.URLEncoder.encode(exception.getMessage(), "UTF-8");
            String cleanUrl = getCleanFrontendUrl();
            response.sendRedirect((cleanUrl != null ? cleanUrl : "") + "/?oauth_error=" + errorParam);
        };
    }
}