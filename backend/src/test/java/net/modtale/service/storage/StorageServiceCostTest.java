package net.modtale.service.storage;

import java.util.Set;
import net.modtale.config.properties.AppR2Properties;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorageServiceCostTest {
    @Test
    void reviewArtifactsUseThePrivateBucketWhileMediaRemainsPublic() {
        S3Client client = mock(S3Client.class);
        StorageService service = new StorageService(client,
                new AppR2Properties("public", "", "", "", "https://cdn.example.test", "private"));
        var file = new MockMultipartFile("file", "sample.jar", "application/java-archive", new byte[]{1, 2, 3});
        String artifact = service.upload(file, "files/mod");
        String modpack = service.upload(file, "modpacks");
        String override = service.upload(file, "modpack-overrides");
        String image = service.upload(file, "images");
        verify(client, times(3)).putObject(argThat((PutObjectRequest r) -> "private".equals(r.bucket())), any(RequestBody.class));
        verify(client).putObject(argThat((PutObjectRequest r) -> "public".equals(r.bucket())), any(RequestBody.class));
        verify(client, times(3)).putObject(argThat((PutObjectRequest r) -> "private, no-store".equals(r.cacheControl())), any(RequestBody.class));
        assertTrue(artifact.startsWith("files/mod/"));
        assertTrue(modpack.startsWith("modpacks/"));
        assertTrue(override.startsWith("modpack-overrides/"));
        assertEquals("https://cdn.example.test/" + image, service.getPublicUrl(image));
        assertThrows(IllegalArgumentException.class, () -> service.getPublicUrl(artifact));
        assertThrows(IllegalArgumentException.class, () -> service.getPublicUrl(modpack));
        assertThrows(IllegalArgumentException.class, () -> service.getPublicUrl(override));
    }

    @Test
    void aPublicOriginWithoutPrivateArtifactStorageFailsBeforeWritingOrReading() {
        S3Client client = mock(S3Client.class);
        StorageService service = new StorageService(client,
                new AppR2Properties("public", "", "", "", "https://cdn.example.test"));
        var file = new MockMultipartFile("file", "sample.jar", "application/java-archive", new byte[]{1});
        assertThrows(IllegalStateException.class, () -> service.upload(file, "files/mod"));
        assertThrows(IllegalStateException.class, () -> service.downloadBounded("files/mod/old.jar", 10));
        assertThrows(IllegalStateException.class, () -> service.uploadDirect("modpacks/old.zip", new byte[]{1}, "application/zip"));
        verifyNoInteractions(client);
    }

    @Test
    void inventoryChecksEachBucketContainingRequiredKeys() {
        S3Client client = mock(S3Client.class);
        when(client.listObjectsV2(any(ListObjectsV2Request.class))).thenAnswer(invocation -> {
            ListObjectsV2Request request = invocation.getArgument(0);
            String key = "private".equals(request.bucket()) ? "files/mod/a.jar" : "images/a.png";
            return ListObjectsV2Response.builder().contents(S3Object.builder().key(key).build()).isTruncated(false).build();
        });
        StorageService service = new StorageService(client,
                new AppR2Properties("public", "", "", "", "https://cdn.example.test", "private"));
        assertEquals(Set.of("files/mod/a.jar", "images/a.png"),
                service.findExistingKeys(Set.of("files/mod/a.jar", "images/a.png", "files/mod/missing.jar")));
        verify(client).listObjectsV2(argThat((ListObjectsV2Request r) -> "private".equals(r.bucket())));
        verify(client).listObjectsV2(argThat((ListObjectsV2Request r) -> "public".equals(r.bucket())));
    }

    @Test
    void inventoryFollowsPaginationAndReturnsOnlyRequiredKeys() {
        S3Client client = mock(S3Client.class);
        when(client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(
                ListObjectsV2Response.builder().contents(S3Object.builder().key("a").build(), S3Object.builder().key("other").build())
                        .isTruncated(true).nextContinuationToken("next").build(),
                ListObjectsV2Response.builder().contents(S3Object.builder().key("b").build()).isTruncated(false).build());
        StorageService service = new StorageService(client, new AppR2Properties("bucket", "", "", "", ""));
        assertEquals(Set.of("a", "b"), service.findExistingKeys(Set.of("a", "b", "missing")));
        verify(client).listObjectsV2(argThat((ListObjectsV2Request r) -> "next".equals(r.continuationToken())));
    }

}
