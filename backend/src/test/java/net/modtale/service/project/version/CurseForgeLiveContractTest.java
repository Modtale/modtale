package net.modtale.service.project.version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class CurseForgeLiveContractTest {

    @Test
    void nyoCfSupportsBrowseDetailIdentityAndVerifiedDownload() throws Exception {
        assumeTrue("true".equalsIgnoreCase(System.getenv("NYOCF_LIVE_TESTS")),
                "Set NYOCF_LIVE_TESTS=true to run the nyoCF integration contract.");
        CurseForgeApiClient client = new CurseForgeApiClient();

        CurseForgeApiClient.CurseForgeSearchResult catalog = client.searchMods("Simple Compost", null, 0, 20, "downloads");
        CurseForgeApiClient.CurseForgeProject card = catalog.projects().stream()
                .filter(project -> "1450386".equals(project.id())).findFirst().orElseThrow();
        CurseForgeApiClient.CurseForgeProject project = client.getProject(Long.parseLong(card.id())).orElseThrow();
        CurseForgeApiClient.CurseForgeFile listed = project.files().getFirst();
        CurseForgeApiClient.CurseForgeProject exactReference = client.resolveProject(project.slug(), listed.id()).orElseThrow();
        CurseForgeApiClient.CurseForgeFile exact = exactReference.files().getFirst();
        CurseForgeApiClient.CurseForgeDownload download = client.getDownload(
                Long.parseLong(project.id()), Long.parseLong(exact.id())).orElseThrow();

        assertFalse(exact.hashes().isEmpty());
        assertTrue(exact.fingerprint() != null && exact.fingerprint() >= 0);
        CurseForgeApiClient.CurseForgeFingerprintMatch identity = client.matchArtifacts(List.of(
                new CurseForgeApiClient.CurseForgeArtifact(exact.fingerprint(), exact.fileName())))
                .get(exact.fingerprint());
        assertEquals(Long.parseLong(project.id()), identity.projectId());
        assertEquals(Long.parseLong(exact.id()), identity.fileId());

        HttpResponse<byte[]> response = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build().send(
                HttpRequest.newBuilder(URI.create(download.downloadUrl()))
                        .header("User-Agent", "Modtale/1.0 (+https://modtale.net)")
                        .header("Referer", "https://www.curseforge.com/").GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());

        assertEquals(200, response.statusCode());
        assertEquals(download.fileSize().longValue(), response.body().length);
        assertTrue(response.body().length > 4);
        assertEquals('P', response.body()[0]);
        assertEquals('K', response.body()[1]);
        String expectedSha1 = download.hashes().get("sha1");
        if (expectedSha1 != null) {
            assertEquals(expectedSha1, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(response.body())));
        }
    }
}
