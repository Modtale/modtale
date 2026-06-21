package net.modtale.controller.system;

import net.modtale.config.properties.AppBackendProperties;
import net.modtale.config.properties.AppFrontendProperties;
import net.modtale.model.dto.response.system.ClientMetadataView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientMetadataControllerTest {

    @Test
    void getClientMetadataBuildsUrlsFromConfiguredFrontendAndBackendHosts() {
        ClientMetadataController controller = new ClientMetadataController(
                new AppBackendProperties("https://api.modtale.test"),
                new AppFrontendProperties("https://modtale.test")
        );

        ClientMetadataView metadata = controller.getClientMetadata();

        assertEquals("https://api.modtale.test/client-metadata.json", metadata.clientId());
        assertEquals("https://api.modtale.test/login/oauth2/code/bluesky", metadata.redirectUris().getFirst());
        assertEquals("https://modtale.test/assets/logo.svg", metadata.logoUri());
    }
}
