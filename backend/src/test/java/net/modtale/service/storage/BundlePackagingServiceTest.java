package net.modtale.service.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectDependency;
import net.modtale.model.project.ProjectVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BundlePackagingServiceTest {

    private DownloadArchiveSupport archiveSupport;
    private BundlePackagingService service;

    @BeforeEach
    void setUp() {
        archiveSupport = mock(DownloadArchiveSupport.class);
        service = new BundlePackagingService(archiveSupport);
    }

    @Test
    void generateBundleZipIncludesMainFileAndOnlySelectedNonEmbeddedDependencies() throws Exception {
        ProjectVersion mainVersion = new ProjectVersion();
        mainVersion.setFileUrl("files/main.jar");
        mainVersion.setDependencies(List.of(
                new ProjectDependency("dep-1", "Dependency One", "1.0.0"),
                new ProjectDependency("dep-2", "Dependency Two", "1.0.0"),
                new ProjectDependency("embedded", "Embedded", "1.0.0", ProjectDependency.DependencyType.EMBEDDED)
        ));
        ProjectVersion depVersion = new ProjectVersion();
        depVersion.setFileUrl("files/dep-one.jar");

        when(archiveSupport.download("files/main.jar")).thenReturn(bytes("main"));
        when(archiveSupport.extractOriginalFilename("files/main.jar")).thenReturn("main.jar");
        when(archiveSupport.resolveDependency(mainVersion.getDependencies().getFirst()))
                .thenReturn(new DownloadArchiveSupport.ResolvedDependency(new Project(), depVersion));
        when(archiveSupport.download("files/dep-one.jar")).thenReturn(bytes("dep-one"));
        when(archiveSupport.extractOriginalFilename("files/dep-one.jar")).thenReturn("dep-one.jar");

        Map<String, String> entries = unzip(service.generateBundleZip(new Project(), mainVersion, List.of("dep-1")));

        assertEquals(Map.of(
                "main.jar", "main",
                "dep-one.jar", "dep-one"
        ), entries);
        verify(archiveSupport, never()).resolveDependency(mainVersion.getDependencies().get(1));
        verify(archiveSupport, never()).resolveDependency(mainVersion.getDependencies().get(2));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static Map<String, String> unzip(byte[] zipBytes) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zis.readAllBytes(), StandardCharsets.UTF_8));
                zis.closeEntry();
            }
        }
        return entries;
    }
}
