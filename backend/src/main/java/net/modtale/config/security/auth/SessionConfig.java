package net.modtale.config.security.auth;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.session.data.mongo.config.annotation.web.http.EnableMongoHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

import java.net.URI;

@Configuration
@EnableMongoHttpSession(maxInactiveIntervalInSeconds = 2592000)
public class SessionConfig {

    private static final Logger logger = LoggerFactory.getLogger(SessionConfig.class);

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${spring.session.mongodb.collection-name:sessions}")
    private String collectionName;

    @Autowired
    private MongoTemplate mongoTemplate;

    @PostConstruct
    public void ensureSessionIndex() {
        try {
            IndexOperations sessionIndexOps = mongoTemplate.indexOps(collectionName);
            sessionIndexOps.ensureIndex(new Index().on("expireAt", Sort.Direction.ASC).expire(0));
            logger.info("Enforced TTL index on '{}' collection for automatic session cleanup.", collectionName);
        } catch (Exception e) {
            logger.warn("Failed to ensure TTL index on session collection: {}", e.getMessage());
        }
    }

    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("SESSION");
        serializer.setCookiePath("/");

        serializer.setUseSecureCookie(true);
        serializer.setSameSite("None");

        if (frontendUrl != null && !frontendUrl.isBlank()) {
            try {
                String host = URI.create(frontendUrl).getHost();
                if (host != null && !host.equalsIgnoreCase("localhost")) {
                    String[] parts = host.split("\\.");
                    if (parts.length >= 2) {
                        String rootDomain = parts[parts.length - 2] + "." + parts[parts.length - 1];
                        serializer.setDomainName(rootDomain);
                    } else {
                        serializer.setDomainName(host);
                    }
                }
            } catch (Exception e) {
                serializer.setDomainNamePattern("(?i)^.+?\\.(\\w+\\.[a-z]+)$");
            }
        } else {
            serializer.setDomainNamePattern("(?i)^.+?\\.(\\w+\\.[a-z]+)$");
        }

        return serializer;
    }
}