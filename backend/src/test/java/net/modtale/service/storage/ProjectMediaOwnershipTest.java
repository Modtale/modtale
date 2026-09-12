package net.modtale.service.storage;

import java.util.*;
import net.modtale.config.properties.AppR2Properties;
import net.modtale.model.project.Project;
import net.modtale.service.project.lifecycle.ProjectArtifactDeletionService;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectMediaOwnershipTest {
    private final String key = "project-media/project-1/images/01234567-89ab-cdef-0123-456789abcdef-icon.png";
    private final String domain = "https://cdn.example.test";
    private StorageService storage(S3Client client) {
        return new StorageService(client, new AppR2Properties("bucket", "", "", "", domain + "/"), null);
    }
    @Test void onlyCanonicalOwnedKeysCanAuthorizeDeletion() {
        for (String value : List.of(key, domain + "/" + key, "/api/files/proxy/" + key)) {
            var client = mock(S3Client.class);
            storage(client).deleteOwnedProjectMedia("project-1", value, List.of());
            verify(client).deleteObject(argThat((DeleteObjectRequest request) -> key.equals(request.key()) && "bucket".equals(request.bucket())));
        }
    }
    @Test void foreignLegacyAmbiguousAndUntrustedReferencesNeverDeleteObjects() {
        var client = mock(S3Client.class); var service = storage(client);
        for (String value : Arrays.asList(null, "images/legacy.png", "files/victim.jar", key.replace("project-1", "project-2"),
                domain + ".attacker.test/" + key, "https://attacker.test/" + key, domain + "/../" + key,
                key + "?download=1", key + "#fragment", key.replace("/images/", "/images/../images/"),
                key.replace("/images/", "/images%2f"), key.replace("icon.png", "../icon.png"),
                key.replace("01234567-89ab-cdef-0123-456789abcdef-", "guessed-")))
            service.deleteOwnedProjectMedia("project-1", value, List.of());
        service.deleteOwnedProjectMedia("../project-1", key, List.of());
        service.deleteOwnedProjectMedia("project-1", key, null);
        verifyNoInteractions(client);
    }
    @Test void remainingReferencesPreventDeletionAcrossSupportedUrlForms() {
        var client = mock(S3Client.class); var service = storage(client);
        for (String remaining : List.of(key, domain + "/" + key, "/api/files/proxy/" + key))
            service.deleteOwnedProjectMedia("project-1", key, Arrays.asList(null, remaining));
        verifyNoInteractions(client);
    }
    @Test void projectCleanupRetainsSameProjectReferencesAndRejectsForeignDraftImages() {
        var client = mock(S3Client.class);
        var cleanup = new ProjectArtifactDeletionService(storage(client));
        var project = new Project(); project.setId("project-1"); project.setImageUrl(domain + "/" + key);
        cleanup.deleteProjectMediaFile(project, key);
        verifyNoInteractions(client);
        project.setImageUrl(domain + "/" + key.replace("project-1", "victim-project"));
        cleanup.deleteProjectMedia(project);
        assertNull(project.getImageUrl()); verifyNoInteractions(client);
    }
    @Test void projectCleanupDeletesOnlyOwnedDetachedMediaAndPreservesUnknownLegacyFiles() {
        var client = mock(S3Client.class); var cleanup = new ProjectArtifactDeletionService(storage(client));
        var project = new Project(); project.setId("project-1"); project.setImageUrl(domain + "/" + key);
        project.setBannerUrl("images/legacy.png"); project.setGalleryImages(List.of("https://elsewhere.test/external.png"));
        cleanup.deleteProjectMedia(project);
        verify(client).deleteObject(argThat((DeleteObjectRequest request) -> key.equals(request.key())));
        verifyNoMoreInteractions(client);
        assertNull(project.getImageUrl()); assertTrue(project.getGalleryImages().isEmpty());
    }
    @Test void uploadNamespacesRejectUntrustedProjectIdsAndKinds() {
        assertEquals("project-media/project-1/gallery", ProjectMediaKeys.prefix("project-1", "gallery"));
        for (String id : Arrays.asList(null, "", "../victim", "a/b", "a%2fb"))
            assertThrows(IllegalArgumentException.class, () -> ProjectMediaKeys.prefix(id, "images"));
        assertThrows(IllegalArgumentException.class, () -> ProjectMediaKeys.prefix("project-1", "files"));
    }
}
