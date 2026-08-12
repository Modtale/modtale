package net.modtale.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class StatusWebConfig {

    private final StatusServiceProperties properties;

    public StatusWebConfig(StatusServiceProperties properties) {
        this.properties = properties;
    }

    @Bean
    public SecurityFilterChain statusSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    public CorsConfigurationSource statusCorsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = properties.getCorsAllowedOrigins();
        configuration.setAllowedOriginPatterns(origins == null || origins.isEmpty() ? List.of("*") : origins);
        configuration.setAllowedMethods(List.of("GET", "HEAD", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Accept", "Content-Type", "Cache-Control"));
        configuration.setMaxAge(300L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/status/**", configuration);
        return source;
    }

    @Bean
    public ObjectMapper statusObjectMapper() {
        return JsonMapper.builder()
                .findAndAddModules()
                .build();
    }
}
