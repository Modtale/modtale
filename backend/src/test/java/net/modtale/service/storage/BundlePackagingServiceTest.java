package net.modtale.service.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.stream.Stream;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectDependency;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import net.modtale.service.admin.review.VersionReviewSnapshot;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.access.AccessControlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

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

        when(archiveSupport.downloadApproved(mainVersion)).thenReturn(bytes("main"));
        when(archiveSupport.extractOriginalFilename("files/main.jar")).thenReturn("main.jar");
        when(archiveSupport.resolveDependency(mainVersion.getDependencies().getFirst()))
                .thenReturn(new DownloadArchiveSupport.ResolvedDependency(new Project(), depVersion));
        when(archiveSupport.downloadApproved(depVersion)).thenReturn(bytes("dep-one"));
        when(archiveSupport.extractOriginalFilename("files/dep-one.jar")).thenReturn("dep-one.jar");

        Map<String, String> entries = unzip(service.generateBundleZip(new Project(), mainVersion, List.of("dep-1")));

        assertEquals(Map.of(
                "main.jar", "main",
                "dep-one.jar", "dep-one"
        ), entries);
        verify(archiveSupport, never()).resolveDependency(mainVersion.getDependencies().get(1));
        verify(archiveSupport, never()).resolveDependency(mainVersion.getDependencies().get(2));
    }

    @Test
    void unavailableSelectedDependencyCannotProduceAnIncompleteBundle() throws Exception {
        ProjectVersion mainVersion = new ProjectVersion();
        mainVersion.setFileUrl("files/main.jar");
        mainVersion.setDependencies(List.of(new ProjectDependency("dep-1", "Dependency One", "1.0.0")));
        when(archiveSupport.downloadApproved(mainVersion)).thenReturn(bytes("main"));
        when(archiveSupport.extractOriginalFilename("files/main.jar")).thenReturn("main.jar");

        assertThrows(IOException.class,
                () -> service.generateBundleZip(new Project(), mainVersion, List.of("dep-1")));
    }

    @Test
    void unknownSelectionCannotBeSilentlyIgnored() throws Exception {
        ProjectVersion mainVersion = new ProjectVersion();
        mainVersion.setFileUrl("files/main.jar");
        mainVersion.setDependencies(List.of());
        when(archiveSupport.downloadApproved(mainVersion)).thenReturn(bytes("main"));
        when(archiveSupport.extractOriginalFilename("files/main.jar")).thenReturn("main.jar");

        assertThrows(IOException.class,
                () -> service.generateBundleZip(new Project(), mainVersion, List.of("unknown")));
    }

    @ParameterizedTest
    @MethodSource("dependencyChanges")
    void dependencyChangingDuringALaterReadCannotBeDelivered(String change, boolean selectAll) throws Exception {
        ProjectService projects = mock(ProjectService.class);
        StorageService storage = mock(StorageService.class);
        AccessControlService access = mock(AccessControlService.class);
        service = new BundlePackagingService(new DownloadArchiveSupport(projects, storage, access));
        ProjectVersion main = approvedVersion("main-v", "main.jar", "main");
        ProjectVersion first = approvedVersion("first-v", "first.jar", "first");
        ProjectVersion second = approvedVersion("second-v", "second.jar", "second");
        main.setDependencies(List.of(new ProjectDependency("first", "First", "1.0.0"),
                new ProjectDependency("second", "Second", "1.0.0")));
        Map<String, Project> current = new HashMap<>();
        current.put("first", publicProject("first", first));
        current.put("second", publicProject("second", second));
        when(projects.getRawProjectById(any())).thenAnswer(invocation -> current.get(invocation.getArgument(0)));
        when(access.isPubliclyReadable(any())).thenAnswer(invocation ->
                ((Project) invocation.getArgument(0)).getStatus() == ProjectStatus.PUBLISHED);
        when(storage.downloadBounded("main.jar", StorageService.MAX_REVIEW_ARTIFACT_BYTES)).thenReturn(bytes("main"));
        when(storage.downloadBounded("first.jar", StorageService.MAX_REVIEW_ARTIFACT_BYTES)).thenReturn(bytes("first"));
        when(storage.downloadBounded("second.jar", StorageService.MAX_REVIEW_ARTIFACT_BYTES)).thenAnswer(invocation -> {
            Project fresh = publicProject("first", approvedVersion("first-v", "first.jar", "first"));
            switch (change) {
                case "withdrawn" -> fresh.getVersions().getFirst().setReviewStatus(ProjectVersion.ReviewStatus.PENDING);
                case "replaced" -> fresh.setVersions(List.of(approvedVersion("replacement-v", "replacement.jar", "replacement")));
                case "reviewChanged" -> fresh.getVersions().getFirst().setChangelog("Changed review inputs");
                case "private" -> fresh.setStatus(ProjectStatus.PRIVATE);
                case "softDeleted" -> fresh.setDeletedAt(LocalDateTime.of(2026, 10, 1, 0, 0));
                case "deleted" -> fresh.setStatus(ProjectStatus.DELETED);
                case "versionRemoved" -> fresh.setVersions(List.of());
                case "projectRemoved" -> fresh = null;
                default -> throw new IllegalArgumentException(change);
            }
            current.put("first", fresh);
            return bytes("second");
        });

        assertThrows(IOException.class, () -> service.generateBundleZip(new Project(), main,
                selectAll ? null : List.of("first", "second")));
        verify(storage).downloadBounded("first.jar", StorageService.MAX_REVIEW_ARTIFACT_BYTES);
        verify(storage).downloadBounded("second.jar", StorageService.MAX_REVIEW_ARTIFACT_BYTES);
        verify(storage, never()).downloadBounded("replacement.jar", StorageService.MAX_REVIEW_ARTIFACT_BYTES);
    }

    private static Stream<Arguments> dependencyChanges() {
        return Stream.of("withdrawn", "replaced", "reviewChanged", "private", "softDeleted", "deleted", "versionRemoved", "projectRemoved")
                .flatMap(change -> Stream.of(Arguments.of(change, true), Arguments.of(change, false)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void lastDependencyChangingDuringItsOwnReadCannotBeDelivered(boolean withdrawn) throws Exception {
        ProjectVersion main = new ProjectVersion();
        main.setFileUrl("main.jar");
        ProjectDependency dependency = new ProjectDependency("dep", "Dependency", "1.0.0");
        main.setDependencies(List.of(dependency));
        ProjectVersion original = approvedVersion("dep-v", "dep.jar", "dep");
        ProjectVersion replacement = approvedVersion("replacement-v", "replacement.jar", "replacement");
        var resolved = new DownloadArchiveSupport.ResolvedDependency(new Project(), original);
        var changed = new DownloadArchiveSupport.ResolvedDependency(new Project(), replacement);
        when(archiveSupport.downloadApproved(main)).thenReturn(bytes("main"));
        when(archiveSupport.extractOriginalFilename("main.jar")).thenReturn("main.jar");
        when(archiveSupport.resolveDependency(dependency)).thenReturn(resolved, withdrawn ? null : changed);
        when(archiveSupport.downloadApproved(original)).thenReturn(bytes("dep"));
        when(archiveSupport.extractOriginalFilename("dep.jar")).thenReturn("dep.jar");

        assertThrows(IOException.class, () -> service.generateBundleZip(new Project(), main, List.of("dep")));
        verify(archiveSupport).downloadApproved(original);
        verify(archiveSupport, never()).downloadApproved(replacement);
    }

    @Test
    void dependencyBindingIsCapturedBeforeTheArtifactRead() throws Exception {
        ProjectVersion main = new ProjectVersion();
        main.setFileUrl("main.jar");
        ProjectDependency dependency = new ProjectDependency("dep", "Dependency", "1.0.0");
        main.setDependencies(List.of(dependency));
        ProjectVersion original = approvedVersion("dep-v", "dep.jar", "dep");
        when(archiveSupport.downloadApproved(main)).thenReturn(bytes("main"));
        when(archiveSupport.extractOriginalFilename("main.jar")).thenReturn("main.jar");
        when(archiveSupport.resolveDependency(dependency))
                .thenReturn(new DownloadArchiveSupport.ResolvedDependency(new Project(), original));
        when(archiveSupport.downloadApproved(original)).thenAnswer(invocation -> {
            original.setChangelog("Changed while reading");
            return bytes("dep");
        });
        when(archiveSupport.extractOriginalFilename("dep.jar")).thenReturn("dep.jar");

        assertThrows(IOException.class, () -> service.generateBundleZip(new Project(), main, List.of("dep")));
    }

    @Test
    void locallyChangedDependencyCannotDeliverWhenRepositorySnapshotIsUnchanged() throws Exception {
        ProjectVersion main = new ProjectVersion();
        main.setFileUrl("main.jar");
        ProjectDependency dependency = new ProjectDependency("dep", "Dependency", "1.0.0");
        main.setDependencies(List.of(dependency));
        ProjectVersion prepared = approvedVersion("dep-v", "dep.jar", "dep");
        ProjectVersion persisted = approvedVersion("dep-v", "dep.jar", "dep");
        assertEquals(VersionReviewSnapshot.token(persisted), VersionReviewSnapshot.token(prepared));
        when(archiveSupport.downloadApproved(main)).thenReturn(bytes("main"));
        when(archiveSupport.extractOriginalFilename("main.jar")).thenReturn("main.jar");
        when(archiveSupport.resolveDependency(dependency)).thenReturn(
                new DownloadArchiveSupport.ResolvedDependency(new Project(), prepared),
                new DownloadArchiveSupport.ResolvedDependency(new Project(), persisted));
        when(archiveSupport.downloadApproved(prepared)).thenAnswer(invocation -> {
            prepared.setHash(approvedVersion("other", "dep.jar", "replacement").getHash());
            return bytes("replacement");
        });
        when(archiveSupport.extractOriginalFilename("dep.jar")).thenReturn("dep.jar");

        assertThrows(IOException.class, () -> service.generateBundleZip(new Project(), main, List.of("dep")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"projectId", "versionNumber", "source", "dependencyType"})
    void changedSelectorCannotRevalidateAgainstAnIdenticalVersionSnapshot(String field) throws Exception {
        ProjectVersion main = new ProjectVersion();
        main.setFileUrl("main.jar");
        ProjectDependency dependency = new ProjectDependency("dep", "Dependency", "1.0.0");
        main.setDependencies(List.of(dependency));
        ProjectVersion prepared = approvedVersion("dep-v", "dep.jar", "dep");
        ProjectVersion other = approvedVersion("dep-v", "dep.jar", "dep");
        assertEquals(VersionReviewSnapshot.token(prepared), VersionReviewSnapshot.token(other));
        when(archiveSupport.downloadApproved(main)).thenReturn(bytes("main"));
        when(archiveSupport.extractOriginalFilename("main.jar")).thenReturn("main.jar");
        when(archiveSupport.resolveDependency(dependency)).thenReturn(
                new DownloadArchiveSupport.ResolvedDependency(publicProject("dep", prepared), prepared),
                new DownloadArchiveSupport.ResolvedDependency(publicProject("other-project", other), other));
        when(archiveSupport.downloadApproved(prepared)).thenAnswer(invocation -> {
            switch (field) {
                case "projectId" -> dependency.setProjectId("other-project");
                case "versionNumber" -> dependency.setVersionNumber("other-version");
                case "source" -> dependency.setSource(ProjectDependency.Source.OTHER);
                case "dependencyType" -> dependency.setDependencyType(ProjectDependency.DependencyType.EMBEDDED);
                default -> throw new IllegalArgumentException(field);
            }
            return bytes("dep");
        });
        when(archiveSupport.extractOriginalFilename("dep.jar")).thenReturn("dep.jar");

        assertThrows(IOException.class, () -> service.generateBundleZip(new Project(), main, List.of("dep")));
    }

    @Test
    void earlierLocalDependencyMutationDuringALaterReadCannotDeliverWithUnchangedRepository() throws Exception {
        ProjectVersion main = new ProjectVersion();
        main.setFileUrl("main.jar");
        ProjectDependency first = new ProjectDependency("first", "First", "1.0.0");
        ProjectDependency second = new ProjectDependency("second", "Second", "1.0.0");
        main.setDependencies(List.of(first, second));
        ProjectVersion preparedFirst = approvedVersion("first-v", "first.jar", "first");
        ProjectVersion persistedFirst = approvedVersion("first-v", "first.jar", "first");
        ProjectVersion preparedSecond = approvedVersion("second-v", "second.jar", "second");
        when(archiveSupport.downloadApproved(main)).thenReturn(bytes("main"));
        when(archiveSupport.extractOriginalFilename("main.jar")).thenReturn("main.jar");
        when(archiveSupport.resolveDependency(first)).thenReturn(
                new DownloadArchiveSupport.ResolvedDependency(new Project(), preparedFirst),
                new DownloadArchiveSupport.ResolvedDependency(new Project(), persistedFirst));
        when(archiveSupport.resolveDependency(second))
                .thenReturn(new DownloadArchiveSupport.ResolvedDependency(new Project(), preparedSecond));
        when(archiveSupport.downloadApproved(preparedFirst)).thenReturn(bytes("first"));
        when(archiveSupport.extractOriginalFilename("first.jar")).thenReturn("first.jar");
        when(archiveSupport.downloadApproved(preparedSecond)).thenAnswer(invocation -> {
            preparedFirst.setChangelog("Changed during later read");
            return bytes("second");
        });
        when(archiveSupport.extractOriginalFilename("second.jar")).thenReturn("second.jar");

        assertThrows(IOException.class, () -> service.generateBundleZip(new Project(), main, null));
        verify(archiveSupport).downloadApproved(preparedSecond);
    }

    @Test
    void emptySelectionDoesNotReadOrRevalidateDependencies() throws Exception {
        ProjectVersion main = new ProjectVersion();
        main.setFileUrl("main.jar");
        ProjectDependency dependency = new ProjectDependency("dep", "Dependency", "1.0.0");
        main.setDependencies(List.of(dependency));
        when(archiveSupport.downloadApproved(main)).thenReturn(bytes("main"));
        when(archiveSupport.extractOriginalFilename("main.jar")).thenReturn("main.jar");

        assertEquals(Map.of("main.jar", "main"), unzip(service.generateBundleZip(new Project(), main, List.of())));
        verify(archiveSupport, never()).resolveDependency(dependency);
    }

    @Test
    void downloadCounterChangesDoNotInvalidateAnIncludedDependency() throws Exception {
        ProjectVersion main = new ProjectVersion();
        main.setFileUrl("main.jar");
        ProjectDependency dependency = new ProjectDependency("dep", "Dependency", "1.0.0");
        main.setDependencies(List.of(dependency));
        ProjectVersion version = approvedVersion("dep-v", "dep.jar", "dep");
        when(archiveSupport.downloadApproved(main)).thenReturn(bytes("main"));
        when(archiveSupport.extractOriginalFilename("main.jar")).thenReturn("main.jar");
        when(archiveSupport.resolveDependency(dependency))
                .thenReturn(new DownloadArchiveSupport.ResolvedDependency(new Project(), version));
        when(archiveSupport.downloadApproved(version)).thenAnswer(invocation -> {
            version.setDownloadCount(100);
            return bytes("dep");
        });
        when(archiveSupport.extractOriginalFilename("dep.jar")).thenReturn("dep.jar");

        assertEquals(Map.of("main.jar", "main", "dep.jar", "dep"),
                unzip(service.generateBundleZip(new Project(), main, null)));
        verify(archiveSupport).downloadApproved(version);
    }

    private static ProjectVersion approvedVersion(String id, String fileUrl, String data) throws Exception {
        ProjectVersion version = new ProjectVersion();
        version.setId(id);
        version.setVersionNumber("1.0.0");
        version.setFileUrl(fileUrl);
        version.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);
        version.setHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes(data))));
        return version;
    }

    private static Project publicProject(String id, ProjectVersion version) {
        Project project = new Project();
        project.setId(id);
        project.setStatus(ProjectStatus.PUBLISHED);
        project.setVersions(List.of(version));
        return project;
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
