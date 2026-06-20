package net.modtale.config.security;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.modtale.controller.auth.AuthController;
import net.modtale.config.auth.ApiKeyAuthFilter;
import net.modtale.config.properties.AppFrontendProperties;
import net.modtale.exception.ErrorMessageUtils;
import net.modtale.model.user.User;
import net.modtale.service.auth.AuthenticationService;
import net.modtale.service.auth.LauncherAuthService;
import net.modtale.service.auth.LocalUserDetailsService;
import net.modtale.service.auth.OAuth2LoginService;
import net.modtale.service.auth.OidcLoginService;
import net.modtale.service.user.account.AccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    private final ApiKeyAuthFilter apiKeyAuthFilter;
    private final RateLimitFilter rateLimitFilter;
    private final OAuth2LoginService oauth2LoginService;
    private final OidcLoginService oidcLoginService;
    private final OAuth2AuthorizedClientRepository authorizedClientRepository;
    private final LocalUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final AccountService accountService;
    private final AuthenticationService authenticationService;
    private final LauncherAuthService launcherAuthService;
    private final AppFrontendProperties frontendProperties;

    public SecurityConfig(
            ApiKeyAuthFilter apiKeyAuthFilter,
            RateLimitFilter rateLimitFilter,
            OAuth2LoginService oauth2LoginService,
            OidcLoginService oidcLoginService,
            OAuth2AuthorizedClientRepository authorizedClientRepository,
            LocalUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder,
            AccountService accountService,
            AuthenticationService authenticationService,
            LauncherAuthService launcherAuthService,
            AppFrontendProperties frontendProperties
    ) {
        this.apiKeyAuthFilter = apiKeyAuthFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.oauth2LoginService = oauth2LoginService;
        this.oidcLoginService = oidcLoginService;
        this.authorizedClientRepository = authorizedClientRepository;
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.accountService = accountService;
        this.authenticationService = authenticationService;
        this.launcherAuthService = launcherAuthService;
        this.frontendProperties = frontendProperties;
    }

    @PostConstruct
    public void logConfig() {
        logger.info("Security Config Initialized. Frontend URL: {}", frontendProperties.url());
    }

    private String getCleanFrontendUrl() {
        String frontendUrl = frontendProperties.url();
        if (frontendUrl != null && frontendUrl.endsWith("/")) {
            return frontendUrl.substring(0, frontendUrl.length() - 1);
        }
        return frontendUrl;
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    private boolean isPreviewEnvironment() {
        String cleanUrl = getCleanFrontendUrl();
        if (cleanUrl == null || cleanUrl.isBlank()) return false;
        String host = safeHostFromUrl(cleanUrl);
        return (host != null && host.endsWith(".run.app")) || "dev.modtale.net".equalsIgnoreCase(host);
    }

    private boolean isLocalhost() {
        String cleanUrl = getCleanFrontendUrl();
        return cleanUrl != null && (cleanUrl.contains("localhost") || cleanUrl.contains("127.0.0.1"));
    }

    private Set<String> getAllowedFrontendOriginPatterns() {
        Set<String> origins = new LinkedHashSet<>();
        origins.add("https://modtale.net");
        origins.add("https://*.modtale.net");

        String cleanUrl = getCleanFrontendUrl();
        if (cleanUrl == null || cleanUrl.isBlank()) {
            return origins;
        }

        origins.add(cleanUrl);
        URI uri = safeUri(cleanUrl, "frontend URL");
        if (uri != null) {
            String host = uri.getHost();
            String scheme = uri.getScheme();
            if (host == null || scheme == null) {
                return origins;
            }

            if (!host.endsWith(".run.app") && !"localhost".equalsIgnoreCase(host) && !"127.0.0.1".equals(host)) {
                if (host.startsWith("www.")) {
                    origins.add(scheme + "://" + host.substring(4));
                } else {
                    origins.add(scheme + "://www." + host);
                }
            }

            if (host.endsWith("modtale.net")) {
                origins.add("https://modtale.net");
                origins.add("https://www.modtale.net");
                origins.add("https://*.modtale.net");
            }
        }

        return origins;
    }

    private boolean isAllowedFrontendHost(String host) {
        if (host == null || host.isBlank()) return false;
        String normalized = host.toLowerCase();
        for (String originPattern : getAllowedFrontendOriginPatterns()) {
            String allowedHost = safeHostFromUrl(originPattern);
            if (allowedHost != null && normalized.equalsIgnoreCase(allowedHost)) {
                return true;
            }
        }
        return false;
    }

    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setUseSecureCookie(!isLocalhost());
        serializer.setCookiePath("/");

        boolean isPreview = isPreviewEnvironment();
        String cleanUrl = getCleanFrontendUrl();

        if (isPreview) {
            serializer.setSameSite("None");
        } else {
            serializer.setSameSite("Lax");

            if (cleanUrl != null && !cleanUrl.isBlank() && !isLocalhost()) {
                String host = safeHostFromUrl(cleanUrl);
                if (host != null) {
                    String[] parts = host.split("\\.");
                    if (parts.length >= 2) {
                        String rootDomain = parts[parts.length - 2] + "." + parts[parts.length - 1];
                        serializer.setDomainName(rootDomain);
                    }
                }
            }
        }
        return serializer;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository tokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        tokenRepository.setCookiePath("/");

        tokenRepository.setCookieCustomizer(cookie -> {
            boolean isPreview = isPreviewEnvironment();
            String cleanUrl = getCleanFrontendUrl();
            cookie.secure(!isLocalhost());

            if (isPreview) {
                cookie.sameSite("None");
                cookie.domain(null);
            } else {
                cookie.sameSite("Lax");
                if (cleanUrl != null && !cleanUrl.isBlank() && !isLocalhost()) {
                    String host = safeHostFromUrl(cleanUrl);
                    if (host != null) {
                        String[] parts = host.split("\\.");
                        if (parts.length >= 2) {
                            String rootDomain = parts[parts.length - 2] + "." + parts[parts.length - 1];
                            cookie.domain(rootDomain);
                        }
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
                .addFilterAfter(new net.modtale.config.security.CsrfCookieFilter(), BasicAuthenticationFilter.class)
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
                                .userService(oauth2LoginService)
                                .oidcUserService(oidcLoginService)
                        )
                        .authorizedClientRepository(authorizedClientRepository)
                        .successHandler(oauthSuccessHandler())
                        .failureHandler(oauthFailureHandler())
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/oauth2/**", "/login**", "/error", "/logout").permitAll()
                        .requestMatchers("/api/v1/docs/**").permitAll()
                        .requestMatchers(
                                "/api/v1/auth/register",
                                "/api/v1/auth/verify",
                                "/api/v1/auth/signin",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/oauth/**",
                                "/api/v1/auth/launcher/oauth/**",
                                "/api/v1/auth/mfa/validate-login",
                                "/api/v1/auth/launcher/exchange",
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
                                "/api/v1/users/**",
                                "/api/v1/orgs/*/members",
                                "/api/v1/creators/**",
                                "/api/v1/og/**",
                                "/api/v1/download/**",
                                "/api/v1/download-bundle/**",
                                "/api/v1/lists/**",
                                "/api/v1/meta/**",
                                "/api/v1/status",
                                "/api/v1/version/**",
                                "/api/v1/analytics/platform/stats",
                                "/api/v1/wiki/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/api/v1/projects/**", "/api/v1/tags", "/api/v1/files/**", "/api/v1/user/profile/**", "/api/v1/og/**", "/api/v1/lists/**").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/users/batch"
                        ).permitAll()
                        .requestMatchers("/api/v1/analytics/platform/full").access((authentication, context) -> {
                            boolean isApiKeyUser = authentication.get().getAuthorities().stream()
                                    .anyMatch(a -> a.getAuthority().equals("ROLE_API"));
                            if (isApiKeyUser) return new AuthorizationDecision(false);

                            boolean isSuperAdmin = authentication.get().getAuthorities().stream()
                                    .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));
                            return new AuthorizationDecision(authentication.get().isAuthenticated() && isSuperAdmin);
                        })
                        .requestMatchers("/api/v1/analytics/view/**", "/api/v1/views/project/**").access((authentication, context) -> {
                            boolean isApiKeyUser = authentication.get().getAuthorities().stream()
                                    .anyMatch(a -> a.getAuthority().equals("ROLE_API"));
                            if (isApiKeyUser) return new AuthorizationDecision(false);

                            HttpServletRequest request = context.getRequest();
                            String origin = request.getHeader("Origin");
                            String referer = request.getHeader("Referer");
                            String originHost = safeHostFromUrl(origin);
                            String refererHost = safeHostFromUrl(referer);

                            boolean isValidOrigin = isAllowedFrontendHost(originHost);
                            boolean isValidReferer = isAllowedFrontendHost(refererHost);

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
                            ErrorMessageUtils.writeJsonError(
                                    response,
                                    HttpStatus.FORBIDDEN,
                                    "You do not have permission to perform this action with the current account or API key."
                            );
                        })
                        .authenticationEntryPoint((request, response, authException) -> {
                            ErrorMessageUtils.writeJsonError(
                                    response,
                                    HttpStatus.UNAUTHORIZED,
                                    "You need to sign in before performing this action. If you were already signed in, your session may have expired."
                            );
                        })
                );
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration restrictedConfig = new CorsConfiguration();
        List<String> restrictedOrigins = new ArrayList<>();

        boolean isPreview = isPreviewEnvironment();
        Set<String> frontendOrigins = getAllowedFrontendOriginPatterns();
        String cleanUrl = getCleanFrontendUrl();

        if (isPreview) {
            restrictedOrigins.add("https://*.run.app");
            if (cleanUrl != null && cleanUrl.contains("dev.modtale.net")) {
                restrictedOrigins.add(cleanUrl);
            }
        } else {
            restrictedOrigins.addAll(frontendOrigins);
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
        source.registerCorsConfiguration("/api/v1/views/project/**", restrictedConfig);
        source.registerCorsConfiguration("/api/v1/user/repos/**", restrictedConfig);
        source.registerCorsConfiguration("/api/v1/orgs/*/repos/**", restrictedConfig);
        source.registerCorsConfiguration("/api/v1/user/connections/**", restrictedConfig);
        source.registerCorsConfiguration("/api/v1/orgs/*/connections/**", restrictedConfig);

        CorsConfiguration publicConfig = new CorsConfiguration();
        List<String> publicOrigins = new ArrayList<>();
        publicOrigins.add("*");
        publicOrigins.addAll(frontendOrigins);
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

                        User user = accountService.getPublicProfile(login);
                        if (user != null) {
                            accountService.updateProviderTokens(user.getId(), "gitlab", accessToken, refreshToken, expiresAt);
                        }
                    }
                }
            }

            User user = accountService.getPublicProfile(login);
            boolean isLinking = Boolean.TRUE.equals(oauthUser.getAttribute("is_linking"));
            LauncherOAuthRequest launcherOAuthRequest = consumeLauncherOAuthRequest(request);
            if (launcherOAuthRequest != null && !isLinking) {
                if (user == null) {
                    response.sendRedirect(launcherCallbackUrl(
                            launcherOAuthRequest.redirectUri(),
                            "oauth_user_not_found",
                            launcherOAuthRequest.state(),
                            false
                    ));
                    return;
                }
                if (!user.isMfaEnabled()) {
                    SecurityContextRepository repository = securityContextRepository();
                    repository.saveContext(SecurityContextHolder.getContext(), request, response);
                    try {
                        LauncherAuthService.LauncherAuthGrant grant = launcherAuthService.issueCode(
                                user,
                                launcherOAuthRequest.redirectUri(),
                                launcherOAuthRequest.state()
                        );
                        response.sendRedirect(launcherCallbackUrl(grant.redirectUri(), grant.code(), grant.state(), true));
                    } catch (RuntimeException ex) {
                        response.sendRedirect(launcherCallbackUrl(
                                launcherOAuthRequest.redirectUri(),
                                ex.getMessage(),
                                launcherOAuthRequest.state(),
                                false
                        ));
                    }
                    return;
                }
            }

            if (user != null && user.isMfaEnabled() && !isLinking) {
                String preAuthToken = authenticationService.generatePreAuthToken(user.getId());
                String postLoginRedirect = launcherOAuthRequest == null
                        ? consumePostOAuthRedirect(request, "/dashboard/profile")
                        : launcherAuthFrontendPath(launcherOAuthRequest);

                SecurityContextHolder.clearContext();

                SecurityContextRepository repository = securityContextRepository();
                repository.saveContext(SecurityContextHolder.createEmptyContext(), request, response);

                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }

                String mfaPath = "/mfa?token=" + preAuthToken;
                if (!"/dashboard/profile".equals(postLoginRedirect)) {
                    mfaPath += "&redirect=" + URLEncoder.encode(postLoginRedirect, StandardCharsets.UTF_8);
                }
                response.sendRedirect(frontendUrl(mfaPath));
            } else {
                SecurityContextRepository repository = securityContextRepository();
                repository.saveContext(SecurityContextHolder.getContext(), request, response);

                response.sendRedirect(frontendUrl(consumePostOAuthRedirect(request, "/dashboard/profile")));
            }
        };
    }

    @Bean
    public AuthenticationFailureHandler oauthFailureHandler() {
        return (request, response, exception) -> {
            LauncherOAuthRequest launcherOAuthRequest = consumeLauncherOAuthRequest(request);
            if (launcherOAuthRequest != null) {
                response.sendRedirect(launcherCallbackUrl(
                        launcherOAuthRequest.redirectUri(),
                        exception.getMessage(),
                        launcherOAuthRequest.state(),
                        false
                ));
                return;
            }
            String errorParam = URLEncoder.encode(exception.getMessage(), StandardCharsets.UTF_8);
            String redirectPath = consumePostOAuthRedirect(request, "/");
            String separator = redirectPath.contains("?") ? "&" : "?";
            response.sendRedirect(frontendUrl(redirectPath + separator + "oauth_error=" + errorParam));
        };
    }

    private String frontendUrl(String path) {
        String cleanUrl = getCleanFrontendUrl();
        return (cleanUrl != null ? cleanUrl : "") + safeInternalRedirect(path, "/");
    }

    private LauncherOAuthRequest consumeLauncherOAuthRequest(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }

        Object redirectUri = session.getAttribute(LauncherAuthService.OAUTH_REDIRECT_URI_SESSION_ATTRIBUTE);
        Object state = session.getAttribute(LauncherAuthService.OAUTH_STATE_SESSION_ATTRIBUTE);
        session.removeAttribute(LauncherAuthService.OAUTH_REDIRECT_URI_SESSION_ATTRIBUTE);
        session.removeAttribute(LauncherAuthService.OAUTH_STATE_SESSION_ATTRIBUTE);

        if (redirectUri instanceof String redirect && !redirect.isBlank()) {
            return new LauncherOAuthRequest(redirect, state instanceof String value ? value : "");
        }
        return null;
    }

    private String launcherAuthFrontendPath(LauncherOAuthRequest request) {
        return "/launcher/auth?redirect_uri=" + URLEncoder.encode(request.redirectUri(), StandardCharsets.UTF_8)
                + (request.state().isBlank()
                ? ""
                : "&state=" + URLEncoder.encode(request.state(), StandardCharsets.UTF_8));
    }

    private String launcherCallbackUrl(String redirectUri, String value, String state, boolean success) {
        String key = success ? "code" : "error";
        int fragmentStart = redirectUri.indexOf('#');
        String base = fragmentStart >= 0 ? redirectUri.substring(0, fragmentStart) : redirectUri;
        String fragment = fragmentStart >= 0 ? redirectUri.substring(fragmentStart) : "";

        StringBuilder target = new StringBuilder(base);
        if (base.contains("?")) {
            if (!base.endsWith("?") && !base.endsWith("&")) {
                target.append('&');
            }
        } else {
            target.append('?');
        }

        target.append(key).append('=').append(URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8));
        if (state != null && !state.isBlank()) {
            target.append("&state=").append(URLEncoder.encode(state, StandardCharsets.UTF_8));
        }
        target.append(fragment);
        return target.toString();
    }

    private String consumePostOAuthRedirect(HttpServletRequest request, String fallback) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return fallback;
        }

        Object redirect = session.getAttribute(AuthController.POST_OAUTH_REDIRECT_ATTRIBUTE);
        session.removeAttribute(AuthController.POST_OAUTH_REDIRECT_ATTRIBUTE);
        if (redirect instanceof String redirectPath) {
            return safeInternalRedirect(redirectPath, fallback);
        }
        return fallback;
    }

    private String safeInternalRedirect(String redirect, String fallback) {
        if (redirect == null || redirect.isBlank()) {
            return fallback;
        }

        String trimmed = redirect.trim();
        if (!trimmed.startsWith("/") || trimmed.startsWith("//")) {
            return fallback;
        }
        return trimmed;
    }

    private URI safeUri(String rawUri, String description) {
        if (rawUri == null || rawUri.isBlank()) {
            return null;
        }
        try {
            return URI.create(rawUri);
        } catch (IllegalArgumentException ex) {
            logger.warn("Failed to parse {} '{}': {}", description, rawUri, ex.getMessage());
            return null;
        }
    }

    private String safeHostFromUrl(String rawUri) {
        URI uri = safeUri(rawUri, "request origin");
        return uri != null ? uri.getHost() : null;
    }

    private record LauncherOAuthRequest(String redirectUri, String state) {
    }
}
