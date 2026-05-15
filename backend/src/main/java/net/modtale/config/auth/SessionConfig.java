package net.modtale.config.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.session.data.mongo.JacksonMongoSessionConverter;
import org.springframework.session.data.mongo.config.annotation.web.http.EnableMongoHttpSession;

@Configuration
@EnableMongoHttpSession(maxInactiveIntervalInSeconds = 2592000, collectionName = "modtale_sessions") // 30 days
public class SessionConfig implements BeanClassLoaderAware {

    private static final Logger logger = LoggerFactory.getLogger(SessionConfig.class);
    private ClassLoader loader;

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.loader = classLoader;
    }

    @Bean
    public JacksonMongoSessionConverter mongoSessionConverter() {
        ObjectMapper objectMapper = new ObjectMapper();

        objectMapper.registerModules(SecurityJackson2Modules.getModules(this.loader));

        objectMapper.findAndRegisterModules();

        logger.info("Initialized JacksonMongoSessionConverter for JSON-based session storage.");
        return new JacksonMongoSessionConverter(objectMapper);
    }
}