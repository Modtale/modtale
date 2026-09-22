package net.modtale.service.storage;

import java.util.Set;
import net.modtale.config.properties.AppR2Properties;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorageServiceCostTest {
    @Test
    void inventoryFollowsPaginationAndReturnsOnlyRequiredKeys() {
        S3Client client = mock(S3Client.class);
        when(client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(
                ListObjectsV2Response.builder().contents(S3Object.builder().key("a").build(), S3Object.builder().key("other").build())
                        .isTruncated(true).nextContinuationToken("next").build(),
                ListObjectsV2Response.builder().contents(S3Object.builder().key("b").build()).isTruncated(false).build());
        StorageService service = new StorageService(client, new AppR2Properties("bucket", "", "", "", ""), null);
        assertEquals(Set.of("a", "b"), service.findExistingKeys(Set.of("a", "b", "missing")));
        verify(client).listObjectsV2(argThat((ListObjectsV2Request r) -> "next".equals(r.continuationToken())));
    }

    @Test
    void signedGrantIsShortLivedAndDoesNotRequirePublicBucketAccess() {
        try (S3Presigner signer = S3Presigner.builder().region(Region.of("auto"))
                .endpointOverride(java.net.URI.create("https://account.r2.cloudflarestorage.com"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test-key", "test-secret"))).build()) {
            StorageService service = new StorageService(mock(S3Client.class), new AppR2Properties("private-bucket", "", "", "", ""), signer);
            assertNull(service.directDownloadUri("files/a.jar", "a.jar"));
            ReflectionTestUtils.setField(service, "directStorageDownloads", true);
            java.net.URI uri = service.directDownloadUri("files/a.jar", "a.jar");
            assertNotNull(uri);
            assertTrue(uri.getRawQuery().contains("X-Amz-Expires=60"));
            assertTrue(uri.getRawQuery().contains("X-Amz-Signature="));
            assertTrue(uri.getQuery().contains("response-cache-control=private, no-store"));
        }
    }
}
