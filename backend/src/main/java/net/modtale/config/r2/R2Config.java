package net.modtale.config.r2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

@Configuration
public class R2Config {

    private static final Logger logger = LoggerFactory.getLogger(R2Config.class);

    @Value("${app.r2.access-key}")
    private String accessKey;

    @Value("${app.r2.secret-key}")
    private String secretKey;

    @Value("${app.r2.endpoint}")
    private String endpoint;

    @Bean
    public S3Client s3Client() {
        URI uri = URI.create(endpoint);
        String cleanEndpoint = uri.getScheme() + "://" + uri.getAuthority();

        if (!endpoint.equals(cleanEndpoint)) {
            logger.warn("Corrected R2 Endpoint from '{}' to '{}' to prevent path duplication.", endpoint, cleanEndpoint);
        }

        return S3Client.builder()
                .endpointOverride(URI.create(cleanEndpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)
                ))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }
}