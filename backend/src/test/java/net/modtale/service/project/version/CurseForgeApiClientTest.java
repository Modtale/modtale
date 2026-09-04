package net.modtale.service.project.version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class CurseForgeApiClientTest {

    @Test
    void browsesNyoCfAndNormalizesFiltersSortingAndPagination() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo("https://nyocf.junyo.dev/api/v1/hytale/mods/search?q=map&limit=20&offset=20&include_files=true"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("User-Agent", "Modtale/1.0 (+https://modtale.net)"))
                .andRespond(withSuccess("""
                        {"data":[
                          {"id":2,"name":"Zed","slug":"zed","download_count":10,"primary_author":"Z","categories":["Utility"],"recent_files":[{"id":20,"file_name":"zed.jar","display_name":"Zed 1","release_type":"release","file_date":"2026-08-01T00:00:00Z","game_versions":["0.6"]}]},
                          {"id":1,"name":"Alpha","slug":"alpha","download_count":20,"primary_author":"A","categories":[],"recent_files":[{"id":10,"file_name":"alpha.jar","display_name":"Alpha 1","release_type":"release","file_date":"2026-09-01T00:00:00Z","game_versions":["0.5"]}]}
                        ],"pagination":{"total":60}}
                        """, MediaType.APPLICATION_JSON));

        CurseForgeApiClient.CurseForgeSearchResult result = fixture.client.searchMods("map", "0.6", 1, 20, "name");

        assertEquals(List.of("zed"), result.projects().stream().map(CurseForgeApiClient.CurseForgeProject::slug).toList());
        assertEquals(20, result.index());
        assertEquals(60, result.totalCount());
        assertEquals("RELEASE", result.projects().getFirst().files().getFirst().releaseType());
        fixture.server.verify();
    }

    @Test
    void loadsNyoCfProjectFilesDescriptionAndGallery() {
        Fixture fixture = fixture();
        expectProject(fixture.server, "simple-compost");
        fixture.server.expect(requestTo("https://nyocf.junyo.dev/api/v1/hytale/mods/1450386/files"))
                .andRespond(withSuccess("""
                        [{"id":3,"display_name":"older","file_name":"older.jar","file_date":"2026-08-01T00:00:00Z"},
                         {"id":4,"display_name":"newer","file_name":"newer.jar","file_date":"2026-09-01T00:00:00Z"}]
                        """, MediaType.APPLICATION_JSON));
        fixture.server.expect(requestTo("https://nyocf.junyo.dev/api/v1/hytale/mods/1450386/description"))
                .andRespond(withSuccess("{\"mod_id\":1450386,\"description\":\"<p>Compost</p>\"}", MediaType.APPLICATION_JSON));

        CurseForgeApiClient.CurseForgeProject project = fixture.client.resolveProject("simple-compost", null).orElseThrow();

        assertEquals(List.of("4", "3"), project.files().stream().map(CurseForgeApiClient.CurseForgeFile::id).toList());
        assertEquals(List.of("Builder"), project.authors());
        assertEquals(List.of("Gameplay"), project.categories());
        assertEquals(List.of("https://media.forgecdn.net/shot.png"), project.screenshots());
        assertEquals("<p>Compost</p>", project.description());
        fixture.server.verify();
    }

    @Test
    void resolvesRequestedFilesOnlyThroughExactNyoCfMetadata() {
        Fixture fixture = fixture();
        expectProject(fixture.server, "simple-compost");
        fixture.server.expect(requestTo("https://nyocf.junyo.dev/api/v1/hytale/mods/1450386/files/8747324"))
                .andRespond(withSuccess(exactFileJson(null), MediaType.APPLICATION_JSON));
        fixture.server.expect(requestTo("https://nyocf.junyo.dev/api/v1/hytale/mods/1450386/description"))
                .andRespond(withResourceNotFound());

        CurseForgeApiClient.CurseForgeFile file = fixture.client.resolveProject("simple-compost", "8747324")
                .orElseThrow().files().getFirst();

        assertEquals("298f58e5f18c34af847916b4068e2b9fef2f87a0", file.hashes().get("sha1"));
        assertEquals("fa60d71f39e775a70e8a997246aec95b", file.hashes().get("md5"));
        assertEquals(679752086L, file.fingerprint());
        fixture.server.verify();
    }

    @Test
    void usesNyoCfDownloadUrlAndExactIntegrityMetadata() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo("https://nyocf.junyo.dev/api/v1/hytale/mods/1450386/files/8747324"))
                .andRespond(withSuccess(exactFileJson("https://edge.forgecdn.net/files/8747/324/SimpleCompost.jar"), MediaType.APPLICATION_JSON));

        CurseForgeApiClient.CurseForgeDownload download = fixture.client.getDownload(1450386, 8747324).orElseThrow();

        assertEquals("https://edge.forgecdn.net/files/8747/324/SimpleCompost.jar", download.downloadUrl());
        assertEquals(100897L, download.fileSize());
        assertEquals("298f58e5f18c34af847916b4068e2b9fef2f87a0", download.hashes().get("sha1"));
        fixture.server.verify();
    }

    @Test
    void usesProviderPublicDeliveryWhenNyoCfOmitsADirectUrl() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo("https://nyocf.junyo.dev/api/v1/hytale/mods/1450386/files/8747324"))
                .andRespond(withSuccess(exactFileJson(null), MediaType.APPLICATION_JSON));

        CurseForgeApiClient.CurseForgeDownload download = fixture.client.getDownload(1450386, 8747324).orElseThrow();

        assertEquals("https://www.curseforge.com/api/v1/mods/1450386/files/8747324/download", download.downloadUrl());
        fixture.server.verify();
    }

    @Test
    void rejectsMismatchedExactFileMetadata() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo("https://nyocf.junyo.dev/api/v1/hytale/mods/1450386/files/8747324"))
                .andRespond(withSuccess(exactFileJson(null).replace("\"mod_id\":1450386", "\"mod_id\":999"), MediaType.APPLICATION_JSON));

        assertTrue(fixture.client.getDownload(1450386, 8747324).isEmpty());
        fixture.server.verify();
    }

    @Test
    void identifiesInstalledArtifactByExactFingerprintNotFilenameAlone() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo("https://nyocf.junyo.dev/api/v1/hytale/mods/batch-search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"queries\":[\"SimpleCompost-1.0.0.jar\"]}"))
                .andRespond(withSuccess("""
                        {"results":{"SimpleCompost-1.0.0.jar":[{"id":1450386,"slug":"simple-compost"}]}}
                        """, MediaType.APPLICATION_JSON));
        fixture.server.expect(requestTo("https://nyocf.junyo.dev/api/v1/hytale/mods/1450386/files"))
                .andRespond(withSuccess("[{\"id\":8747324,\"file_name\":\"SimpleCompost-1.0.0.jar\"}]", MediaType.APPLICATION_JSON));
        fixture.server.expect(requestTo("https://nyocf.junyo.dev/api/v1/hytale/mods/1450386/files/8747324"))
                .andRespond(withSuccess(exactFileJson(null), MediaType.APPLICATION_JSON));

        Map<Long, CurseForgeApiClient.CurseForgeFingerprintMatch> matches = fixture.client.matchArtifacts(List.of(
                new CurseForgeApiClient.CurseForgeArtifact(679752086L, "/mods/SimpleCompost-1.0.0.jar"),
                new CurseForgeApiClient.CurseForgeArtifact(123L, "C:\\Hytale\\mods\\SimpleCompost-1.0.0.jar")));

        assertEquals(1, matches.size());
        assertEquals(1450386L, matches.get(679752086L).projectId());
        assertEquals(8747324L, matches.get(679752086L).fileId());
        assertTrue(!matches.containsKey(123L));
        fixture.server.verify();
    }

    private static void expectProject(MockRestServiceServer server, String id) {
        server.expect(requestTo("https://nyocf.junyo.dev/api/v1/hytale/mods/" + id))
                .andRespond(withSuccess("""
                        {"id":1450386,"game_id":70216,"name":"Simple Compost","slug":"simple-compost",
                         "summary":"Compost things","is_available":true,"download_count":798,
                         "links":{"website":"https://www.curseforge.com/hytale/mods/simple-compost"},
                         "logo":{"thumbnail_url":"https://media.forgecdn.net/icon.png"},
                         "authors":[{"name":"Builder"}],"categories":[{"name":"Gameplay"}],
                         "screenshots":[{"url":"https://media.forgecdn.net/shot.png"}],
                         "dates":{"modified":"2026-09-01T00:00:00Z"}}
                        """, MediaType.APPLICATION_JSON));
    }

    private static String exactFileJson(String downloadUrl) {
        String download = downloadUrl == null ? "" : ",\"download_url\":\"" + downloadUrl + "\"";
        return """
                {"id":8747324,"mod_id":1450386,"game_id":70216,"display_name":"SimpleCompost 1.0.0",
                 "file_name":"SimpleCompost-1.0.0.jar","release_type":"release","is_available":true,
                 "file_date":"2026-08-27T15:11:51.190Z","file_length":100897,"download_count":18,
                 "game_versions":["Early Access"],"hashes":{"sha1":"298f58e5f18c34af847916b4068e2b9fef2f87a0",
                 "md5":"fa60d71f39e775a70e8a997246aec95b","fingerprint":679752086}%s}
                """.formatted(download);
    }

    private static Fixture fixture() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        return new Fixture(new CurseForgeApiClient(restTemplate), server);
    }

    private record Fixture(CurseForgeApiClient client, MockRestServiceServer server) {}
}
