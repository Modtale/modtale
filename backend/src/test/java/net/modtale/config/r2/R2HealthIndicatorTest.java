package net.modtale.config.r2;

import net.modtale.config.properties.AppR2Properties;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class R2HealthIndicatorTest {
    @Test
    void publicOriginRequiresAReachableSeparateArtifactBucket() {
        S3Client client = mock(S3Client.class);
        var absent = new R2HealthIndicator(client,
                new AppR2Properties("public", "", "", "", "https://cdn.example.test"));
        assertEquals("DOWN", absent.health().getStatus().getCode());
        verifyNoInteractions(client);

        var configured = new R2HealthIndicator(client,
                new AppR2Properties("public", "", "", "", "https://cdn.example.test", "private"));
        assertEquals("UP", configured.health().getStatus().getCode());
        verify(client).headBucket(argThat((HeadBucketRequest r) -> "public".equals(r.bucket())));
        verify(client).headBucket(argThat((HeadBucketRequest r) -> "private".equals(r.bucket())));

        when(client.headBucket(argThat((HeadBucketRequest r) -> "private".equals(r.bucket()))))
                .thenThrow(S3Exception.builder().statusCode(403).message("denied").build());
        assertEquals("DOWN", configured.health().getStatus().getCode());
    }
}
