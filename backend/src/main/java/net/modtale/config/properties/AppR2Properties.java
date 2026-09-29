package net.modtale.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.r2")
public record AppR2Properties(
        String bucket,
        String accessKey,
        String secretKey,
        String endpoint,
        String publicDomain,
        String artifactBucket
) {
    public AppR2Properties(String bucket, String accessKey, String secretKey, String endpoint, String publicDomain) {
        this(bucket, accessKey, secretKey, endpoint, publicDomain, null);
    }
}
