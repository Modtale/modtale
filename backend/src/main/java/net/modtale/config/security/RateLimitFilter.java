package net.modtale.config.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.service.security.ApiKeyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitFilter implements Filter {

    @Autowired
    private ApiKeyService apiKeyService;

    private final Map<String, Bucket> bucketCache = new ConcurrentHashMap<>();

    private static final List<String> BLOCKED_AGENTS = Arrays.asList(
            "java", "python", "curl", "wget", "apache-httpclient", "libwww-perl"
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        if (!req.getRequestURI().startsWith("/api/v1")) {
            chain.doFilter(request, response);
            return;
        }

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
                limitKey = "KEY:" + apiKey.getId();
                if (apiKey.getTier() == ApiKey.Tier.ENTERPRISE) {
                    capacity = 2000;
                    tierName = "Enterprise";
                } else {
                    capacity = 300;
                    tierName = "User-API";
                }
            } else {
                limitKey = "IP:" + getClientIp(req);
                capacity = 10;
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
                capacity = 1000;
                tierName = "Frontend-User";
            } else if (isFrontend) {
                limitKey = "IP:" + getClientIp(req);
                capacity = 2000;
                tierName = "Frontend-Public";
            } else {
                if (userAgent == null || userAgent.isBlank() || isBlockedAgent(userAgent)) {
                    res.setStatus(403);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"error\": \"Forbidden\", \"message\": \"Valid User-Agent or API Key required.\"}");
                    return;
                }

                limitKey = "IP:" + getClientIp(req);
                capacity = 60;
                tierName = "Public";
            }
        }

        Bucket bucket = bucketCache.computeIfAbsent(limitKey, k -> createNewBucket(capacity));

        res.setHeader("X-RateLimit-Limit", String.valueOf(capacity));
        res.setHeader("X-RateLimit-Tier", tierName);
        res.setHeader("X-RateLimit-Remaining", String.valueOf(bucket.getAvailableTokens()));

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            res.setStatus(429);
            res.setContentType("application/json");
            res.getWriter().write("{\"error\": \"Too Many Requests\", \"message\": \"Rate limit exceeded for tier: " + tierName + "\"}");
        }
    }

    private Bucket createNewBucket(long capacity) {
        Bandwidth limit = Bandwidth.classic(capacity, Refill.greedy(capacity, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private boolean isBlockedAgent(String ua) {
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
        return req.getRemoteAddr();
    }
}