package net.modtale.config.r2;

import java.net.URI;
import net.modtale.config.properties.AppR2Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
public class R2Config {

    private static final Logger logger = LoggerFactory.getLogger(R2Config.class);

    private final AppR2Properties r2Properties;

    public R2Config(AppR2Properties r2Properties) {
        this.r2Properties = r2Properties;
    }

    @Bean
    public S3Client s3Client() {
        String endpoint = r2Properties.endpoint();
        URI uri = URI.create(endpoint);
        String cleanEndpoint = uri.getScheme() + "://" + uri.getAuthority();

        if (!endpoint.equals(cleanEndpoint)) {
            logger.warn("Corrected R2 Endpoint from '{}' to '{}' to prevent path duplication.", endpoint, cleanEndpoint);
        }

        return S3Client.builder()
                .endpointOverride(URI.create(cleanEndpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(r2Properties.accessKey(), r2Properties.secretKey())
                ))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }
}
