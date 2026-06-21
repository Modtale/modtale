package net.modtale.config.auth;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.mongo.JdkMongoSessionConverter;
import org.springframework.session.data.mongo.config.annotation.web.http.EnableMongoHttpSession;

@Configuration
@EnableMongoHttpSession(maxInactiveIntervalInSeconds = 2592000, collectionName = "modtale_sessions") // 30 days
public class SessionConfig {

    private static final Logger logger = LoggerFactory.getLogger(SessionConfig.class);

    @Bean
    public JdkMongoSessionConverter mongoSessionConverter() {
        logger.info("Initialized JdkMongoSessionConverter for robust binary session storage.");
        return new JdkMongoSessionConverter(Duration.ofDays(30));
    }
}
