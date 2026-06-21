package net.modtale.config.db;

import java.util.List;
import net.modtale.model.user.OAuthProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

@Configuration
public class MongoConfig {

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(List.of(new StringToOAuthProviderConverter()));
    }

    @ReadingConverter
    public static class StringToOAuthProviderConverter implements Converter<String, OAuthProvider> {
        @Override
        public OAuthProvider convert(String source) {
            if (source == null || source.isBlank()) {
                return null;
            }
            try {
                return OAuthProvider.valueOf(source.toUpperCase());
            } catch (IllegalArgumentException e) {
                try {
                    return OAuthProvider.valueOf(source);
                } catch (IllegalArgumentException ex) {
                    return null;
                }
            }
        }
    }
}
