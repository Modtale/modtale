package net.modtale.service.worldlist;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;
import net.modtale.config.properties.AppLimitProperties;
import net.modtale.service.admin.review.ProjectReviewPersistence;
import net.modtale.service.admin.review.VersionReviewSnapshot;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectDependency;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.worldlist.WorldModList;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.access.AccessControlService;
import net.modtale.service.storage.DownloadService;
import net.modtale.service.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.ObjectMapper;

class WorldModListArchiveFreshnessTest {
    enum Change { WITHDRAWN, REPLACED_BYTES, REPLACED_ID, PRIVATE, DELETED, DUPLICATE_ID }

    @ParameterizedTest @EnumSource(Change.class)
    void ownStorageReadCannotDeliverChangedApproval(Change change) throws Exception {
        var f = new Fixture();
        when(f.storage.downloadBounded("a.jar", StorageService.MAX_REVIEW_ARTIFACT_BYTES)).thenAnswer(call -> {
            f.change("a", change);
            return bytes("a");
        });
        assertThrows(IOException.class, () -> f.service.generateZip(f.list("a")));
    }

    @ParameterizedTest @EnumSource(Change.class)
    void laterItemReadCannotDeliverAnEarlierChangedApproval(Change change) throws Exception {
        var f = new Fixture();
        when(f.storage.downloadBounded("b.jar", StorageService.MAX_REVIEW_ARTIFACT_BYTES)).thenAnswer(call -> {
            f.change("a", change);
            return bytes("b");
        });
        assertThrows(IOException.class, () -> f.service.generateZip(f.list("a", "b")));
    }

    @ParameterizedTest @EnumSource(Change.class)
    void laterItemReadCannotDeliverAChangedBundledDependency(Change change) throws Exception {
        var f = new Fixture();
        f.modpack();
        when(f.storage.downloadBounded("b.jar", StorageService.MAX_REVIEW_ARTIFACT_BYTES)).thenAnswer(call -> {
            f.change("dependency", change);
            return bytes("b");
        });
        assertThrows(IOException.class, () -> f.service.generateZip(f.list("a", "b")));
    }

    @Test
    void modpackGenerationCannotDeliverAWithdrawnParent() throws Exception {
        var f = new Fixture();
        f.modpack();
        when(f.download.generateModpackZip(any(), any(), isNull(), anyString(), anyList())).thenAnswer(call -> {
            f.change("a", Change.WITHDRAWN);
            return bytes("pack");
        });
        assertThrows(IOException.class, () -> f.service.generateZip(f.list("a")));
    }

    @Test
    void callerLocalHashMutationCannotAuthorizeDifferentBytes() throws Exception {
        var f = new Fixture();
        var prepared = f.current.get("a");
        when(f.storage.downloadBounded("a.jar", StorageService.MAX_REVIEW_ARTIFACT_BYTES)).thenAnswer(call -> {
            f.current.put("a", project("a"));
            prepared.getVersions().getFirst().setHash(hash("replacement"));
            return bytes("replacement");
        });
        assertFalse(entries(f.service.generateZip(f.list("a"))).containsKey("a-1.0.jar"));
    }

    @Test
    void temporaryHashMutationCannotAuthorizeBytesEvenIfLaterRestored() throws Exception {
        var f = new Fixture();
        var prepared = f.current.get("a").getVersions().getFirst();
        when(f.storage.downloadBounded("a.jar", StorageService.MAX_REVIEW_ARTIFACT_BYTES)).thenAnswer(call -> {
            prepared.setHash(hash("replacement"));
            return bytes("replacement");
        });
        when(f.storage.downloadBounded("b.jar", StorageService.MAX_REVIEW_ARTIFACT_BYTES)).thenAnswer(call -> {
            prepared.setHash(hash("a"));
            return bytes("b");
        });
        assertFalse(entries(f.service.generateZip(f.list("a", "b"))).containsKey("a-1.0.jar"));
    }

    @Test
    void callerLocalDependencyMutationCannotChangePreparedPackageInputs() throws Exception {
        var f = new Fixture();
        f.modpack();
        var prepared = f.current.get("a");
        var originalDependencies = prepared.getVersions().getFirst().getDependencies();
        var originalToken = VersionReviewSnapshot.modpackArchiveToken(prepared.getVersions().getFirst());
        when(f.download.generateModpackZip(any(), any(), isNull(), anyString(), anyList())).thenAnswer(call -> {
            Project saved = project("a");
            saved.setClassification(ProjectClassification.MODPACK);
            saved.getVersions().getFirst().setDependencies(originalDependencies);
            assertEquals(originalToken, VersionReviewSnapshot.modpackArchiveToken(saved.getVersions().getFirst()));
            f.current.put("a", saved);
            prepared.getVersions().getFirst().setDependencies(List.of());
            return bytes("different-package");
        });
        assertThrows(IOException.class, () -> f.service.generateZip(f.list("a")));
    }

    @Test
    void unrelatedLatestVersionAndDownloadCountsDoNotInvalidateExactBytes() throws Exception {
        var f = new Fixture();
        when(f.storage.downloadBounded("b.jar", StorageService.MAX_REVIEW_ARTIFACT_BYTES)).thenAnswer(call -> {
            var updated = project("a");
            updated.getVersions().getFirst().setDownloadCount(99);
            var newer = version("new", "2.0", "new.jar", "new");
            updated.setVersions(List.of(newer, updated.getVersions().getFirst()));
            f.current.put("a", updated);
            return bytes("b");
        });
        var entries = entries(f.service.generateZip(f.list("a", "b")));
        assertEquals("a", entries.get("a-1.0.jar"));
        assertEquals("b", entries.get("b-1.0.jar"));
    }

    @Test
    void regeneratedModpackCacheUrlPreservesTheReviewedInputs() throws Exception {
        var f = new Fixture();
        f.modpack();
        when(f.download.generateModpackZip(any(), any(), isNull(), anyString(), anyList())).thenAnswer(call -> {
            ((ProjectVersion) call.getArgument(1)).setFileUrl("generated-pack.zip");
            return bytes("pack");
        });
        assertEquals("pack", entries(f.service.generateZip(f.list("a"))).get("a-1.0.jar"));
    }

    @Test
    void actualModpackBytesMustMatchTheOuterCapturedDependencyApproval() throws Exception {
        var f = new Fixture();
        f.modpack();
        var originalDependency = f.current.get("dependency");
        DownloadService realDownload = spy(f.realDownload());
        doAnswer(call -> {
            var replacement = project("dependency");
            replacement.getVersions().getFirst().setFileUrl("other.jar");
            replacement.getVersions().getFirst().setHash(hash("other-approved"));
            f.current.put("dependency", replacement);
            return call.callRealMethod();
        }).when(realDownload).generateModpackZip(any(), any(), isNull(), anyString(), anyList());
        when(f.storage.downloadBounded("other.jar", StorageService.MAX_REVIEW_ARTIFACT_BYTES)).thenReturn(bytes("other-approved"));
        when(f.storage.downloadBounded("b.jar", StorageService.MAX_REVIEW_ARTIFACT_BYTES)).thenAnswer(call -> {
            f.current.put("dependency", originalDependency);
            return bytes("b");
        });
        var service = new WorldModListArchiveService(f.storage, new ObjectMapper(), f.projects, f.access, realDownload);
        assertFalse(entries(service.generateZip(f.list("a", "b"))).containsKey("a-1.0.jar"));
    }

    @Test
    void realModpackGeneratorRetainsUnchangedReviewedDependencies() throws Exception {
        var f = new Fixture();
        f.modpack();
        var service = new WorldModListArchiveService(f.storage, new ObjectMapper(), f.projects, f.access, f.realDownload());
        byte[] outer = service.generateZip(f.list("a"));
        try (var zip = new ZipInputStream(new ByteArrayInputStream(outer))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals("a-1.0.jar")) {
                    assertEquals("dependency", entries(zip.readAllBytes()).get("dependency.jar"));
                    return;
                }
            }
        }
        fail("The approved modpack must be included");
    }

    @Test
    void invalidVersionIdentityIsNeverPackaged() throws Exception {
        var f = new Fixture();
        f.current.get("a").getVersions().getFirst().setId(null);
        assertFalse(entries(f.service.generateZip(f.list("a"))).containsKey("a-1.0.jar"));
        verify(f.storage, never()).downloadBounded(anyString(), anyInt());
    }

    private static class Fixture {
        final StorageService storage = mock(StorageService.class);
        final ProjectService projects = mock(ProjectService.class);
        final AccessControlService access = mock(AccessControlService.class);
        final DownloadService download = mock(DownloadService.class);
        final Map<String, Project> current = new HashMap<>();
        final WorldModListArchiveService service = new WorldModListArchiveService(storage, new ObjectMapper(), projects, access, download);
        Fixture() {
            for (String id : List.of("a", "b", "dependency")) {
                current.put(id, project(id));
                when(storage.downloadBounded(id + ".jar", StorageService.MAX_REVIEW_ARTIFACT_BYTES)).thenReturn(bytes(id));
            }
            when(projects.getRawProjectById(anyString())).thenAnswer(call -> current.get(call.getArgument(0)));
            when(access.isPubliclyReadable(any())).thenAnswer(call -> {
                Project p = call.getArgument(0);
                return p != null && (p.getStatus() == ProjectStatus.PUBLISHED || p.getStatus() == ProjectStatus.ARCHIVED);
            });
        }
        DownloadService realDownload() {
            var persistence = mock(ProjectReviewPersistence.class);
            when(persistence.cacheModpackArchive(any(), any(), any(), any(), any())).thenReturn(true);
            when(storage.upload(any(), eq("modpacks"))).thenReturn("generated-pack.zip");
            var limits = mock(AppLimitProperties.class);
            when(limits.modpackGenPerHour()).thenReturn(10);
            return new DownloadService(persistence, projects, storage, access, limits);
        }
        void modpack() throws IOException {
            var pack = current.get("a");
            pack.setClassification(ProjectClassification.MODPACK);
            pack.getVersions().getFirst().setDependencies(List.of(new ProjectDependency("dependency", "Dependency", "1.0")));
            when(download.generateModpackZip(any(), any(), isNull(), anyString(), anyList())).thenReturn(bytes("pack"));
        }
        void change(String id, Change change) {
            Project replacement = project(id);
            ProjectVersion v = replacement.getVersions().getFirst();
            switch (change) {
                case WITHDRAWN -> v.setReviewStatus(ProjectVersion.ReviewStatus.PENDING);
                case REPLACED_BYTES -> v.setHash(hash("replacement"));
                case REPLACED_ID -> v.setId("replacement-id");
                case PRIVATE -> replacement.setStatus(ProjectStatus.DRAFT);
                case DELETED -> replacement.setDeletedAt(java.time.LocalDateTime.now());
                case DUPLICATE_ID -> replacement.setVersions(List.of(v, version(v.getId(), "other-label", id + ".jar", id)));
            }
            current.put(id, replacement);
        }
        WorldModList list(String... ids) {
            var list = new WorldModList();
            list.setId("list");
            var items = new ArrayList<WorldModList.Item>();
            for (String id : ids) {
                var item = new WorldModList.Item();
                item.setId(id); item.setProjectId(id); item.setTitle(id); item.setVersionNumber("1.0");
                item.setSource(ProjectDependency.Source.MODTALE); item.setDownloadable(true); item.setFileUrl(id + ".jar");
                items.add(item);
            }
            list.setMods(items);
            return list;
        }
    }
    private static Project project(String id) {
        var p = new Project(); p.setId(id); p.setStatus(ProjectStatus.PUBLISHED); p.setClassification(ProjectClassification.PLUGIN);
        p.setVersions(List.of(version(id + "-v", "1.0", id + ".jar", id))); return p;
    }
    private static ProjectVersion version(String id, String label, String file, String content) {
        var v = new ProjectVersion(); v.setId(id); v.setVersionNumber(label); v.setFileUrl(file); v.setHash(hash(content));
        v.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED); return v;
    }
    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes(value))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static Map<String, String> entries(byte[] archive) throws IOException {
        var entries = new HashMap<String, String>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
        }
        return entries;
    }
}
