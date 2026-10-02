package net.modtale.config.r2;

import net.modtale.config.properties.AppR2Properties;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
public class R2HealthIndicator implements HealthIndicator {

    private final S3Client s3Client;
    private final String bucketName;
    private final String artifactBucketName;
    private final String publicDomain;

    public R2HealthIndicator(S3Client s3Client, AppR2Properties r2Properties) {
        this.s3Client = s3Client;
        this.bucketName = r2Properties.bucket();
        this.artifactBucketName = r2Properties.artifactBucket();
        this.publicDomain = r2Properties.publicDomain();
    }

    @Override
    public Health health() {
        try {
            if (publicDomain != null && !publicDomain.isBlank()
                    && (artifactBucketName == null || artifactBucketName.isBlank() || artifactBucketName.equals(bucketName)))
                return Health.down().withDetail("error", "Private artifact bucket is not configured").build();
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            if (artifactBucketName != null && !artifactBucketName.isBlank() && !artifactBucketName.equals(bucketName))
                s3Client.headBucket(HeadBucketRequest.builder().bucket(artifactBucketName).build());
            return Health.up().withDetail("bucket", bucketName).build();
        } catch (S3Exception e) {
            return Health.down().withDetail("error", e.getMessage()).build();
        }
    }
}
