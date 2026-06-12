package net.modtale.controller.system;

import java.util.List;
import net.modtale.config.properties.AppBackendProperties;
import net.modtale.config.properties.AppFrontendProperties;
import net.modtale.model.dto.response.system.ClientMetadataView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ClientMetadataController {

    private final ClientMetadataView metadata;

    public ClientMetadataController(
            AppBackendProperties backendProperties,
            AppFrontendProperties frontendProperties
    ) {
        String backendUrl = backendProperties.url();
        String frontendUrl = frontendProperties.url();
        String clientId = backendUrl + "/client-metadata.json";
        String redirectUri = backendUrl + "/login/oauth2/code/bluesky";

        this.metadata = new ClientMetadataView(
                clientId,
                "Modtale",
                backendUrl,
                frontendUrl + "/assets/logo.svg",
                frontendUrl + "/terms",
                frontendUrl + "/privacy",
                List.of(redirectUri),
                "atproto transition:generic",
                List.of("authorization_code", "refresh_token"),
                List.of("code"),
                "none",
                "web",
                true
        );
    }

    @GetMapping("/client-metadata.json")
    public ClientMetadataView getClientMetadata() {
        return metadata;
    }
}
