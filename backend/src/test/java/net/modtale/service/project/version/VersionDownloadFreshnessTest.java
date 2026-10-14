package net.modtale.service.project.version;

import java.io.IOException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import net.modtale.config.properties.AppFrontendProperties;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.exception.VersionNotFoundException;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import net.modtale.service.analytics.AnalyticsEligibilityService;
import net.modtale.service.analytics.TrackingService;
import net.modtale.service.project.access.ProjectVersionAccessService;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.project.validation.ValidationService;
import net.modtale.service.security.access.AccessControlService;
import net.modtale.service.storage.DownloadService;
import net.modtale.service.storage.DownloadTokenService;
import net.modtale.service.storage.StorageService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VersionDownloadFreshnessTest {
    enum Path { ARTIFACT, BUNDLE, MODPACK }
    enum Change { WITHDRAWN, REPLACED, CONTEXT, REMOVED_VERSION, CHANGED_ID, PRIVATE,
        SOFT_DELETED, DELETED, HARD_DELETED, CLASSIFICATION, DUPLICATE_ID }
    private static final byte[] BYTES = {1, 2, 3};

    static Stream<Arguments> changes() {
        return Stream.of(Path.values()).flatMap(path -> Stream.of(Change.values())
                .map(change -> Arguments.of(path, change)));
    }

    @ParameterizedTest
    @MethodSource("changes")
    void changedAuthorityDuringPreparationCannotDeliverOrCount(Path path, Change change) throws Exception {
        var fixture = new Fixture(path);
        var current = fixture.fresh();
        var version = current.getVersions().getFirst();
        switch (change) {
            case WITHDRAWN -> version.setReviewStatus(ProjectVersion.ReviewStatus.PENDING);
            case REPLACED -> {
                version.setHash("f".repeat(64));
                version.setFileUrl("files/replaced.jar");
            }
            case CONTEXT -> version.setGameVersions(List.of("changed-runtime"));
            case REMOVED_VERSION -> current.setVersions(List.of());
            case CHANGED_ID -> version.setId("replacement-version");
            case PRIVATE -> current.setStatus(ProjectStatus.PRIVATE);
            case SOFT_DELETED -> current.setDeletedAt(LocalDateTime.now());
            case DELETED -> current.setStatus(ProjectStatus.DELETED);
            case HARD_DELETED -> current = null;
            case CLASSIFICATION -> current.setClassification(ProjectClassification.DATA);
            case DUPLICATE_ID -> current.setVersions(List.of(version, fixture.version()));
        }
        fixture.afterIo.set(current);
        Exception failure = assertThrows(Exception.class, fixture::download);
        assertTrue(failure instanceof IOException || failure instanceof VersionNotFoundException
                || failure instanceof ResourceNotFoundException, failure.toString());
        fixture.verifyPreparation();
        verifyNoInteractions(fixture.tracking);
    }

    @ParameterizedTest
    @EnumSource(Path.class)
    void unrelatedNewLatestVersionAndDownloadCountDoNotInvalidateSelectedBytes(Path path) throws Exception {
        var fixture = new Fixture(path);
        fixture.selector = "latest";
        var current = fixture.fresh();
        var selected = current.getVersions().getFirst();
        selected.setDownloadCount(42);
        var newer = fixture.version();
        newer.setId("newer-version");
        newer.setVersionNumber("2.0.0");
        current.setVersions(List.of(newer, selected));
        fixture.afterIo.set(current);

        assertArrayEquals(BYTES, fixture.download().bytes());
        fixture.verifyPreparation();
        verify(fixture.tracking).logDownload(eq("p"), eq("selected-version"), any(),
                anyBoolean(), any(), anyBoolean());
    }

    @ParameterizedTest
    @EnumSource(Path.class)
    void unchangedCurrentApprovalDeliversExactlyThePreparedBytes(Path path) throws Exception {
        var fixture = new Fixture(path);
        fixture.afterIo.set(fixture.fresh());
        assertArrayEquals(BYTES, fixture.download().bytes());
        fixture.verifyPreparation();
        verify(fixture.projects, times(2)).getRawProjectById("p");
    }

    @ParameterizedTest
    @EnumSource(value = Path.class, names = "MODPACK")
    void generatedModpackCacheUrlDoesNotChangeApprovalBinding(Path path) throws Exception {
        var fixture = new Fixture(path);
        var current = fixture.fresh();
        current.getVersions().getFirst().setFileUrl("modpacks/generated-cache.zip");
        fixture.afterIo.set(current);
        fixture.callerChange = () -> fixture.initial.getVersions().getFirst()
                .setFileUrl("modpacks/generated-cache.zip");
        assertArrayEquals(BYTES, fixture.download().bytes());
    }

    @ParameterizedTest
    @EnumSource(Path.class)
    void callerLocalContextMutationCannotChangePreparedProvenance(Path path) throws Exception {
        var fixture = new Fixture(path);
        fixture.afterIo.set(fixture.fresh());
        fixture.callerChange = () -> fixture.initial.getVersions().getFirst()
                .setGameVersions(List.of("caller-mutated-runtime"));
        assertThrows(IOException.class, fixture::download);
        fixture.verifyPreparation();
        verifyNoInteractions(fixture.tracking);
    }

    @ParameterizedTest
    @EnumSource(value = Path.class, names = "ARTIFACT")
    void callerLocalHashMutationCannotValidateUnapprovedBytes(Path path) throws Exception {
        var fixture = new Fixture(path);
        fixture.afterIo.set(fixture.fresh());
        fixture.readBytes = new byte[]{4, 5, 6};
        String replacementHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(fixture.readBytes));
        fixture.callerChange = () -> fixture.initial.getVersions().getFirst().setHash(replacementHash);
        assertThrows(IOException.class, fixture::download);
        fixture.verifyPreparation();
        verifyNoInteractions(fixture.tracking);
    }

    private static final class Fixture {
        final Path path;
        final ProjectService projects = mock(ProjectService.class);
        final DownloadService packages = mock(DownloadService.class);
        final DownloadTokenService tokens = mock(DownloadTokenService.class);
        final TrackingService tracking = mock(TrackingService.class);
        final StorageService storage = mock(StorageService.class);
        final Project initial;
        final AtomicReference<Project> persisted;
        final AtomicReference<Project> afterIo = new AtomicReference<>();
        final VersionDownloadOrchestrationService service;
        String selector = "1.0.0";
        Runnable callerChange = () -> {};
        byte[] readBytes = BYTES;

        Fixture(Path path) throws Exception {
            this.path = path;
            initial = fresh();
            persisted = new AtomicReference<>(initial);
            when(projects.getRawProjectById("p")).thenAnswer(call -> persisted.get());
            when(tokens.validateAndConsume("token")).thenAnswer(call -> new DownloadTokenService.DownloadToken(
                    "p", selector, null, null, Instant.now().plusSeconds(60)));
            var access = mock(AccessControlService.class);
            when(access.canReadProject(any(), isNull())).thenAnswer(call ->
                    ((Project) call.getArgument(0)).getStatus() != ProjectStatus.PRIVATE);
            var analytics = mock(AnalyticsEligibilityService.class);
            when(analytics.shouldCountProjectEngagement(any(), isNull())).thenReturn(true);
            service = new VersionDownloadOrchestrationService(
                    new ProjectVersionAccessService(mock(ValidationService.class)), projects, packages,
                    tokens, analytics, tracking, storage, access, new AppFrontendProperties("https://modtale.test"));
            when(storage.downloadBounded(anyString(), anyInt())).thenAnswer(call -> prepared());
            when(packages.generateModpackZip(any(), any(), isNull())).thenAnswer(call -> prepared());
            when(packages.generateBundleZip(any(), any(), isNull(), isNull())).thenAnswer(call -> prepared());
        }

        byte[] prepared() {
            callerChange.run();
            persisted.set(afterIo.get());
            return readBytes;
        }

        VersionDownloadPayload download() throws IOException {
            return path == Path.BUNDLE
                    ? service.downloadBundle("token", false, null, null, null, null)
                    : service.downloadVersion("token", false, null, null, null, null);
        }

        void verifyPreparation() throws IOException {
            if (path == Path.ARTIFACT) verify(storage).downloadBounded(anyString(), anyInt());
            else if (path == Path.MODPACK) verify(packages).generateModpackZip(any(), any(), isNull());
            else verify(packages).generateBundleZip(any(), any(), isNull(), isNull());
        }

        Project fresh() throws Exception {
            Project project = new Project();
            project.setId("p");
            project.setTitle("Approved Project");
            project.setAuthor("author");
            project.setStatus(ProjectStatus.PUBLISHED);
            project.setClassification(path == Path.MODPACK ? ProjectClassification.MODPACK : ProjectClassification.PLUGIN);
            project.setVersions(List.of(version()));
            return project;
        }

        ProjectVersion version() throws Exception {
            ProjectVersion version = new ProjectVersion();
            version.setId("selected-version");
            version.setVersionNumber("1.0.0");
            version.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);
            version.setHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(BYTES)));
            version.setFileUrl(path == Path.MODPACK ? "modpacks/cache.zip" : "files/approved.jar");
            return version;
        }
    }
}
