package net.modtale.service.project.version;

import java.io.IOException;
import java.io.InputStream;
import net.modtale.exception.StorageArtifactOperationException;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.service.security.validation.FileValidationService;
import net.modtale.service.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VersionArtifactServiceTest {

    private VersionArtifactService service;
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() {
        service = new VersionArtifactService(
                mock(StorageService.class),
                mock(FileValidationService.class),
                mock(MongoTemplate.class)
        );
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

    private static final class BrokenInputStream extends InputStream {
        @Override
        public int read() throws IOException {
            throw new IOException("checksum boom");
        }
    }
}
