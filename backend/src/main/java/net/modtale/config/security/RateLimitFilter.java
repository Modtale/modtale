package net.modtale.config.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.service.security.ApiKeyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    @Autowired
    private ApiKeyService apiKeyService;

    private final Map<String, Bucket> bucketCache = new ConcurrentHashMap<>();

    private static final List<String> BLOCKED_AGENTS = Arrays.asList(
            "java", "python", "curl", "wget", "apache-httpclient", "libwww-perl"
    );

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "DELETE", "PATCH");

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        if (!req.getRequestURI().startsWith("/api/v1")) {
            chain.doFilter(req, res);
            return;
        }

        boolean isWrite = WRITE_METHODS.contains(req.getMethod().toUpperCase());

        String apiKeyHeader = req.getHeader("X-MODTALE-KEY");
        String userAgent = req.getHeader("User-Agent");
        String referer = req.getHeader("Referer");
        String origin = req.getHeader("Origin");

        String limitKey;
        long capacity;
        String tierName;

        if (apiKeyHeader != null && !apiKeyHeader.isBlank()) {
            ApiKey apiKey = apiKeyService.resolveKey(apiKeyHeader);

            if (apiKey != null) {
                limitKey = "USER:" + apiKey.getUserId();
                if (apiKey.getTier() == ApiKey.Tier.ENTERPRISE) {
                    capacity = isWrite ? 200 : 2000;
                    tierName = "Enterprise";
                } else {
                    capacity = isWrite ? 50 : 300;
                    tierName = "User-API";
                }
            } else {
                limitKey = "IP:" + getClientIp(req);
                capacity = isWrite ? 2 : 10;
                tierName = "Invalid-Key";
            }
        } else {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            boolean isSessionUser = auth != null && auth.isAuthenticated() &&
                    auth.getPrincipal() instanceof User &&
                    !"anonymousUser".equals(auth.getName());

            boolean isFrontend = (referer != null && (referer.contains("modtale.net") || referer.contains("localhost"))) ||
                    (origin != null && (origin.contains("modtale.net") || origin.contains("localhost")));

            if (isSessionUser) {
                User user = (User) auth.getPrincipal();
                limitKey = "USER:" + user.getId();
                capacity = isWrite ? 100 : 1000;
                tierName = "Frontend-User";
            } else if (isFrontend) {
                limitKey = "IP:" + getClientIp(req);
                capacity = isWrite ? 30 : 2000;
                tierName = "Frontend-Public";
            } else {
                if (userAgent == null || userAgent.isBlank() || isBlockedAgent(userAgent)) {
                    res.setStatus(403);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"error\": \"Forbidden\", \"message\": \"Valid User-Agent or API Key required.\"}");
                    return;
                }

                limitKey = "IP:" + getClientIp(req);
                capacity = isWrite ? 5 : 60;
                tierName = "Public";
            }
        }

        limitKey = limitKey + (isWrite ? ":WRITE" : ":READ");

        final long finalCapacity = capacity;
        Bucket bucket = bucketCache.computeIfAbsent(limitKey, k -> createNewBucket(finalCapacity));

        res.setHeader("X-RateLimit-Limit", String.valueOf(finalCapacity));
        res.setHeader("X-RateLimit-Tier", tierName);
        res.setHeader("X-RateLimit-Remaining", String.valueOf(bucket.getAvailableTokens()));

        if (bucket.tryConsume(1)) {
            chain.doFilter(req, res);
        } else {
            res.setStatus(429);
            res.setContentType("application/json");
            res.getWriter().write("{\"error\": \"Too Many Requests\", \"message\": \"Rate limit exceeded for tier: " + tierName + " (" + (isWrite ? "Write" : "Read") + ")\"}");
        }
    }

    private Bucket createNewBucket(long capacity) {
        Bandwidth limit = Bandwidth.classic(capacity, Refill.greedy(capacity, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private boolean isBlockedAgent(String ua) {
        if (ua == null) return true;
        String lowerUA = ua.toLowerCase();
        return BLOCKED_AGENTS.stream().anyMatch(lowerUA::startsWith);
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
}