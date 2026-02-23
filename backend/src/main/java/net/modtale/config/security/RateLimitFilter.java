package net.modtale.config.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.service.auth.ApiKeyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    @Autowired
    @Lazy
    private ApiKeyService apiKeyService;

    private final Cache<String, Bucket> bucketCache = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();

    private static final List<String> BLOCKED_AGENTS = Arrays.asList(
            "java", "python", "curl", "wget", "apache-httpclient", "libwww-perl", "scrapy", "go-http-client", "axios"
    );

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "DELETE", "PATCH");

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String path = req.getRequestURI();

        if (!path.startsWith("/api/v1")) {
            chain.doFilter(req, res);
            return;
        }

        if (path.equals("/api/v1/status") || path.equals("/api/v1/auth/session")) {
            chain.doFilter(req, res);
            return;
        }

        boolean isWrite = WRITE_METHODS.contains(req.getMethod().toUpperCase());
        String clientIp = getClientIp(req);
        String userAgent = req.getHeader("User-Agent");
        String apiKeyHeader = req.getHeader("X-MODTALE-KEY");

        long capacity;
        String tierName;
        String limitKey;

        if (apiKeyHeader != null && !apiKeyHeader.isBlank()) {
            ApiKey apiKey = apiKeyService.resolveKey(apiKeyHeader);

            if (apiKey != null) {
                limitKey = "API:" + apiKey.getId();
                if (apiKey.getTier() == ApiKey.Tier.ENTERPRISE) {
                    capacity = isWrite ? 500 : 5000;
                    tierName = "Enterprise-API";
                } else {
                    capacity = isWrite ? 60 : 600;
                    tierName = "Standard-API";
                }
            } else {
                sendError(res, 401, "Unauthorized", "Invalid API Key.");
                return;
            }
        } else if (isAuthenticatedUser()) {
            User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            limitKey = "USER:" + user.getId();

            boolean isAdmin = user.getRoles() != null && user.getRoles().contains("ADMIN");

            if (isAdmin) {
                capacity = isWrite ? 1000 : 20000;
                tierName = "Admin-Session";
            } else {
                capacity = isWrite ? 150 : 2000;
                tierName = "User-Session";
            }
        } else if (isFrontendRequest(req)) {
            limitKey = "IP:FE:" + clientIp;
            capacity = isWrite ? 40 : 3000;
            tierName = "Frontend-Public";
        } else {
            if (isBlockedAgent(userAgent)) {
                sendError(res, 403, "Forbidden", "Automated access requires an API Key.");
                return;
            }

            limitKey = "IP:PUB:" + clientIp;
            capacity = isWrite ? 5 : 60;
            tierName = "Public-IP";
        }

        limitKey = limitKey + (isWrite ? ":WRITE" : ":READ");

        Bucket bucket = getBucket(limitKey, capacity);

        res.setHeader("X-RateLimit-Limit", String.valueOf(capacity));
        res.setHeader("X-RateLimit-Remaining", String.valueOf(bucket.getAvailableTokens()));
        res.setHeader("X-RateLimit-Tier", tierName);

        if (bucket.tryConsume(1)) {
            chain.doFilter(req, res);
        } else {
            sendError(res, 429, "Too Many Requests", "Rate limit exceeded for " + tierName + " tier.");
        }
    }

    private Bucket getBucket(String key, long capacity) {
        return bucketCache.get(key, k -> {
            Bandwidth limit = Bandwidth.classic(capacity, Refill.greedy(capacity, Duration.ofMinutes(1)));
            return Bucket.builder().addLimit(limit).build();
        });
    }

    private boolean isAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() &&
                auth.getPrincipal() instanceof User &&
                !"anonymousUser".equals(auth.getName());
    }

    private boolean isFrontendRequest(HttpServletRequest req) {
        String referer = req.getHeader("Referer");
        String origin = req.getHeader("Origin");
        return (referer != null && (referer.contains("modtale.net") || referer.contains("localhost"))) ||
                (origin != null && (origin.contains("modtale.net") || origin.contains("localhost")));
    }

    private boolean isBlockedAgent(String ua) {
        if (ua == null || ua.isBlank()) return true;
        String lowerUA = ua.toLowerCase();
        return BLOCKED_AGENTS.stream().anyMatch(lowerUA::contains);
    }

    private String getClientIp(HttpServletRequest req) {
        String cfIp = req.getHeader("CF-Connecting-IP");
        if (cfIp != null && !cfIp.isBlank()) {
            return cfIp;
        }

        String xForwardedFor = req.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String remoteAddr = req.getRemoteAddr();
        return remoteAddr != null ? remoteAddr : "unknown";
    }

    private void sendError(HttpServletResponse res, int status, String error, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.getWriter().write(String.format("{\"error\": \"%s\", \"message\": \"%s\"}", error, message));
    }
}