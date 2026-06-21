package net.modtale.config.db;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.modtale.config.properties.AppR2Properties;
import net.modtale.config.properties.AppSeedingProperties;
import net.modtale.service.storage.StorageService;
import org.bson.Document;
import org.junit.jupiter.api.Test;

class DataSeederR2SeedLocationTest {

    private final DataSeeder seeder = new DataSeeder(
            null,
            null,
            null,
            null,
            null,
            new AppR2Properties("target-bucket", "", "", "", "https://pub-test.r2.dev"),
            new AppSeedingProperties(false, AppSeedingProperties.Mode.TEMPLATE, false, "modtale-mock-template", "", "", "", "")
    );

    @Test
    void keepsPlainObjectKeys() throws Exception {
        R2Location location = location("files/plugin/example.jar");

        assertEquals("files/plugin/example.jar", location.key());
        assertFalse(location.syntheticFallback());
    }

    @Test
    void extractsKeysFromFirstPartyPublicUrls() throws Exception {
        R2Location cdnLocation = location("https://cdn.modtale.net/files/plugin/example.jar?download=1");
        R2Location r2DevLocation = location("https://pub-test.r2.dev/files/data/example.zip");

        assertEquals("files/plugin/example.jar", cdnLocation.key());
        assertFalse(cdnLocation.syntheticFallback());
        assertEquals("files/data/example.zip", r2DevLocation.key());
        assertFalse(r2DevLocation.syntheticFallback());
    }

    @Test
    void extractsKeysFromProxyUrls() throws Exception {
        R2Location location = location("/api/files/proxy/files/plugin/example.jar");

        assertEquals("files/plugin/example.jar", location.key());
        assertFalse(location.syntheticFallback());
    }

    @Test
    void extractsKeysFromAbsoluteProxyUrls() throws Exception {
        R2Location location = location("https://modtale.net/api/files/proxy/images/banner.jpg?download=1");

        assertEquals("images/banner.jpg", location.key());
        assertFalse(location.syntheticFallback());
    }

    @Test
    void collectsVersionArtifactsAndDependencyArtifactsWithoutMediaUrls() throws Exception {
        Document project = new Document("slug", "sample-project")
                .append("title", "Sample Project")
                .append("classification", "PLUGIN")
                .append("imageUrl", "https://cdn.modtale.net/images/icon.jpg")
                .append("bannerUrl", "https://cdn.modtale.net/images/banner.jpg")
                .append("galleryImages", List.of(
                        "https://cdn.modtale.net/gallery/one.webp",
                        "https://www.youtube.com/watch?v=abcdefghijk"
                ))
                .append("versions", List.of(new Document("versionNumber", "1.2.3")
                        .append("fileUrl", "files/plugin/sample.jar")
                        .append("dependencies", List.of(new Document("projectTitle", "Dep")
                                .append("cachedFileUrl", "external-dependencies/curseforge/1/2/dep.jar")
                                .append("icon", "https://cdn.modtale.net/images/dep-icon.png")))));

        Map<String, Object> objects = collectObjects(project);

        assertTrue(objects.containsKey("files/plugin/sample.jar"));
        assertTrue(objects.containsKey("external-dependencies/curseforge/1/2/dep.jar"));
        assertFalse(objects.containsKey("images/icon.jpg"));
        assertFalse(objects.containsKey("images/banner.jpg"));
        assertFalse(objects.containsKey("gallery/one.webp"));
        assertFalse(objects.containsKey("images/dep-icon.png"));
        assertFalse(objects.containsKey("watch"));
    }

    @Test
    void marksTemplateMockDownloadsForSyntheticFallback() throws Exception {
        R2Location location = location("https://example.test/mock-downloads/review-me-0.1.0.jar");

        assertEquals("mock-downloads/review-me-0.1.0.jar", location.key());
        assertTrue(location.syntheticFallback());
    }

    @Test
    void skipsExternalHttpUrls() throws Exception {
        assertNull(rawLocation("https://example.com/files/not-r2.jar"));
    }

    @Test
    void reseedsMissingArtifactsWhenMarkerIsStale() throws Exception {
        StorageService storageService = mock(StorageService.class);
        when(storageService.exists(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0, String.class);
            return key.startsWith(".modtale/seeding/r2-artifacts/");
        });

        DataSeeder staleMarkerSeeder = new DataSeeder(
                null,
                null,
                null,
                null,
                storageService,
                new AppR2Properties("target-bucket", "", "", "", "https://pub-test.r2.dev"),
                new AppSeedingProperties(false, AppSeedingProperties.Mode.MOCK, false, "modtale-mock-template", "", "", "", "")
        );
        Document project = new Document("slug", "sample-project")
                .append("title", "Sample Project")
                .append("classification", "PLUGIN")
                .append("versions", List.of(new Document("versionNumber", "1.2.3")
                        .append("fileUrl", "files/plugin/sample.jar")));

        Method method = DataSeeder.class.getDeclaredMethod("seedR2ObjectsForProjects", List.class, boolean.class);
        method.setAccessible(true);
        method.invoke(staleMarkerSeeder, List.of(project), true);

        verify(storageService).uploadDirect(eq("files/plugin/sample.jar"), any(byte[].class), eq("application/java-archive"));
        verify(storageService).uploadDirect(startsWith(".modtale/seeding/r2-artifacts/"), any(byte[].class), eq("application/json"));
    }

    @Test
    void fetchesAllTemplateProjects() throws Exception {
        Method method = DataSeeder.class.getDeclaredMethod("templateLimit", String.class);
        method.setAccessible(true);

        assertEquals(0, method.invoke(seeder, "projects"));
    }

    @Test
    void detectsIncompleteTemplateImportsByProjectsAndVersions() throws Exception {
        assertTrue(templateImportIncomplete(10, 90, 10, 90));
        assertTrue(templateImportIncomplete(90, 90, 10, 180));
        assertFalse(templateImportIncomplete(90, 90, 180, 180));
    }

    private R2Location location(String rawLocation) throws Exception {
        Object raw = rawLocation(rawLocation);
        assertNotNull(raw);
        Method key = raw.getClass().getDeclaredMethod("key");
        Method syntheticFallback = raw.getClass().getDeclaredMethod("syntheticFallback");
        key.setAccessible(true);
        syntheticFallback.setAccessible(true);
        return new R2Location((String) key.invoke(raw), (Boolean) syntheticFallback.invoke(raw));
    }

    private Object rawLocation(String rawLocation) throws Exception {
        Method method = DataSeeder.class.getDeclaredMethod("r2SeedLocation", String.class);
        method.setAccessible(true);
        return method.invoke(seeder, rawLocation);
    }

    private Map<String, Object> collectObjects(Document project) throws Exception {
        Method method = DataSeeder.class.getDeclaredMethod("collectProjectR2Objects", Document.class, Map.class);
        method.setAccessible(true);
        Map<String, Object> objects = new LinkedHashMap<>();
        method.invoke(seeder, project, objects);
        return objects;
    }

    private boolean templateImportIncomplete(
            long targetProjectCount,
            long sourceProjectCount,
            long targetVersionCount,
            long sourceVersionCount
    ) throws Exception {
        Method method = DataSeeder.class.getDeclaredMethod(
                "templateImportIncomplete",
                long.class,
                long.class,
                long.class,
                long.class
        );
        method.setAccessible(true);
        return (Boolean) method.invoke(seeder, targetProjectCount, sourceProjectCount, targetVersionCount, sourceVersionCount);
    }

    private record R2Location(String key, boolean syntheticFallback) {
    }
}
