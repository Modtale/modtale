package net.modtale.controller.system;

import net.modtale.config.properties.AppBackendProperties;
import net.modtale.config.properties.AppFrontendProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientMetadataControllerTest {

    @Test
    void getClientMetadataBuildsUrlsFromConfiguredFrontendAndBackendHosts() {
        ClientMetadataController controller = new ClientMetadataController(
                new AppBackendProperties("https://api.modtale.test"),
                new AppFrontendProperties("https://modtale.test")
        );

        Map<String, Object> metadata = controller.getClientMetadata();

        assertEquals("https://api.modtale.test/client-metadata.json", metadata.get("client_id"));
        assertEquals("https://api.modtale.test/login/oauth2/code/bluesky", ((List<?>) metadata.get("redirect_uris")).getFirst());
        assertEquals("https://modtale.test/assets/logo.svg", metadata.get("logo_uri"));
    }
}
