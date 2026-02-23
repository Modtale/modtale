package net.modtale.config.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.mongo.config.annotation.web.http.EnableMongoHttpSession;

@Configuration
@EnableMongoHttpSession(maxInactiveIntervalInSeconds = 2592000, collectionName = "modtale_sessions") // 30 days
public class SessionConfig {

    private static final Logger logger = LoggerFactory.getLogger(SessionConfig.class);
}