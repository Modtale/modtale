package net.modtale.service.project.version;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import net.modtale.exception.StorageArtifactOperationException;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.service.security.validation.FileValidationService;
import net.modtale.service.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class VersionArtifactServiceTest {

    private VersionArtifactService service;
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        service = new VersionArtifactService(mock(StorageService.class), mock(FileValidationService.class), mongoTemplate);
    }

    @Test
    void prepareVersionArtifactWrapsChecksumReadFailuresInANamedException() throws Exception {
        StorageService storageService = mock(StorageService.class);
        FileValidationService fileValidationService = mock(FileValidationService.class);
        service = new VersionArtifactService(storageService, fileValidationService, mongoTemplate);

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("mod.jar");
        when(file.getInputStream()).thenReturn(new BrokenInputStream());

        Project project = new Project();
        project.setClassification(ProjectClassification.PLUGIN);
        when(mongoTemplate.exists(any(), any(Class.class))).thenReturn(false);

        StorageArtifactOperationException error = assertThrows(
                StorageArtifactOperationException.class,
                () -> service.prepareVersionArtifact(project, file)
        );

        assertEquals(
                "Failed to read the uploaded file while calculating its checksum: checksum boom",
                error.getMessage()
        );
    }

    @Test
    void prepareVersionArtifactHashesAndStoresModpackOverridesSeparately() throws Exception {
        StorageService storageService = mock(StorageService.class);
        FileValidationService fileValidationService = mock(FileValidationService.class);
        service = new VersionArtifactService(storageService, fileValidationService, mongoTemplate);
        byte[] bytes = "modpack override bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MultipartFile file = new MockMultipartFile("file", "overrides.zip", "application/zip", bytes);
        when(storageService.upload(file, "modpack-overrides")).thenReturn("modpack-overrides/source.zip");
        Project project = new Project();
        project.setClassification(ProjectClassification.MODPACK);

        VersionArtifactService.PreparedVersionArtifact artifact = service.prepareVersionArtifact(project, file);

        assertEquals("modpack-overrides/source.zip", artifact.filePath());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), artifact.fileHash());
        verify(fileValidationService).validateProjectFile(file, "MODPACK");
        verify(mongoTemplate, org.mockito.Mockito.never()).exists(any(), any(Class.class));
    }

    @Test
    void modpackChecksumFailurePreventsStorageUpload() throws Exception {
        StorageService storageService = mock(StorageService.class);
        service = new VersionArtifactService(storageService, mock(FileValidationService.class), mongoTemplate);
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getInputStream()).thenReturn(new BrokenInputStream());
        Project project = new Project();
        project.setClassification(ProjectClassification.MODPACK);

        assertThrows(StorageArtifactOperationException.class, () -> service.prepareVersionArtifact(project, file));
        verify(storageService, org.mockito.Mockito.never()).upload(any(), any());
    }

    private static final class BrokenInputStream extends InputStream {
        @Override
        public int read() throws IOException {
            throw new IOException("checksum boom");
        }
    }
}
