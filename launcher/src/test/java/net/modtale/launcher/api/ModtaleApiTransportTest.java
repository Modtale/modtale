package net.modtale.launcher.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;

class ModtaleApiTransportTest {
    @Test
    void avoidsCleartextUpgradeWhilePreservingHttpsNegotiation() {
        var local = ModtaleApiTransport.requestBuilder(URI.create("http://localhost:8080/api/v1/lists")).build();
        assertEquals(HttpClient.Version.HTTP_1_1, local.version().orElseThrow());
        var secure = ModtaleApiTransport.requestBuilder(URI.create("https://api.modtale.net/api/v1/lists")).build();
        assertTrue(secure.version().isEmpty());
    }
}
