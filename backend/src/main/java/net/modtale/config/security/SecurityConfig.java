package net.modtale.config.security;

import net.modtale.service.security.CustomUserDetailsService;
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
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
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

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

@Configuration
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

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

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostConstruct
    public void logConfig() {
        logger.info("Security Config Initialized. Frontend URL: {}", frontendUrl);
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository tokenRepository = new CookieCsrfTokenRepository();

        tokenRepository.setCookieHttpOnly(false);
        tokenRepository.setSecure(true);
        tokenRepository.setCookiePath("/");

        tokenRepository.setCookieCustomizer(cookie -> {
            cookie.sameSite("None");

            if (frontendUrl != null && !frontendUrl.isBlank()) {
                try {
                    String host = URI.create(frontendUrl).getHost();
                    if (host != null && !host.equalsIgnoreCase("localhost")) {
                        String[] parts = host.split("\\.");
                        if (parts.length >= 2) {
                            String rootDomain = parts[parts.length - 2] + "." + parts[parts.length - 1];
                            cookie.domain(rootDomain);
                        } else {
                            cookie.domain(host);
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Failed to set CSRF cookie domain: {}", e.getMessage());
                }
            }
        });

        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        requestHandler.setCsrfRequestAttributeName(null);

        http
                .authenticationProvider(authenticationProvider())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                .csrf(csrf -> csrf
                        .csrfTokenRepository(tokenRepository)
                        .csrfTokenRequestHandler(requestHandler)
                        .ignoringRequestMatchers("/api/v1/user/api-keys/**", "/api/v1/auth/**")
                )

                .addFilterBefore(rateLimitFilter, OAuth2LoginAuthenticationFilter.class)
                .addFilterBefore(apiKeyAuthFilter, OAuth2LoginAuthenticationFilter.class)
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)

                .securityContext(sc -> sc.securityContextRepository(securityContextRepository()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation().migrateSession()
                )
                .formLogin(form -> form
                        .loginProcessingUrl("/api/v1/auth/login")
                        .successHandler((request, response, authentication) -> {
                            response.setStatus(HttpStatus.OK.value());
                            response.getWriter().write("{\"status\":\"success\"}");
                        })
                        .failureHandler((request, response, exception) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.getWriter().write("{\"error\":\"Invalid credentials\"}");
                        })
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
                        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/verify", "/api/v1/auth/login", "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password").permitAll()
                        .requestMatchers("/sitemap.xml", "/actuator/health").permitAll()
                        .requestMatchers("/client-metadata.json").permitAll()

                        .requestMatchers(HttpMethod.GET, "/api/v1/projects/**", "/api/v1/tags", "/api/v1/files/**", "/api/v1/user/profile/**").permitAll()

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
                                "/api/v1/user/repos/**",
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

        if (frontendUrl != null && !frontendUrl.isBlank()) {
            restrictedOrigins.add(frontendUrl);
        }

        restrictedConfig.setAllowedOriginPatterns(restrictedOrigins);
        restrictedConfig.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH"));
        restrictedConfig.setAllowedHeaders(Arrays.asList("Authorization", "Cache-Control", "Content-Type", "X-Xsrf-Token", "X-XSRF-TOKEN"));
        restrictedConfig.setAllowCredentials(true);
        restrictedConfig.setMaxAge(3600L);

        source.registerCorsConfiguration("/api/v1/admin/**", restrictedConfig);
        source.registerCorsConfiguration("/api/v1/user/api-keys/**", restrictedConfig);
        source.registerCorsConfiguration("/api/v1/user/analytics", restrictedConfig);
        source.registerCorsConfiguration("/api/v1/projects/*/analytics", restrictedConfig);
        source.registerCorsConfiguration("/api/v1/projects/*/publish", restrictedConfig);
        source.registerCorsConfiguration("/api/v1/analytics/view/**", restrictedConfig);

        source.registerCorsConfiguration("/api/v1/user/repos/**", restrictedConfig);
        source.registerCorsConfiguration("/api/v1/orgs/*/repos/**", restrictedConfig);
        source.registerCorsConfiguration("/api/v1/user/connections/**", restrictedConfig);
        source.registerCorsConfiguration("/api/v1/orgs/*/connections/**", restrictedConfig);

        CorsConfiguration publicConfig = new CorsConfiguration();
        List<String> publicOrigins = new ArrayList<>();
        publicOrigins.add("*");

        if (frontendUrl != null && !frontendUrl.isBlank()) {
            publicOrigins.add(frontendUrl);
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