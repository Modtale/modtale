package net.modtale.controller.system;

import net.modtale.config.properties.AppBackendProperties;
import net.modtale.config.properties.AppFrontendProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static java.util.Map.entry;

@RestController
public class ClientMetadataController {

    private final Map<String, Object> metadata;

    public ClientMetadataController(
            AppBackendProperties backendProperties,
            AppFrontendProperties frontendProperties
    ) {
        String backendUrl = backendProperties.url();
        String frontendUrl = frontendProperties.url();
        String clientId = backendUrl + "/client-metadata.json";
        String redirectUri = backendUrl + "/login/oauth2/code/bluesky";

        this.metadata = Map.ofEntries(
                entry("client_id", clientId),
                entry("client_name", "Modtale"),
                entry("client_uri", backendUrl),
                entry("logo_uri", frontendUrl + "/assets/logo.svg"),
                entry("tos_uri", frontendUrl + "/terms"),
                entry("policy_uri", frontendUrl + "/privacy"),
                entry("redirect_uris", List.of(redirectUri)),
                entry("scope", "atproto transition:generic"),
                entry("grant_types", List.of("authorization_code", "refresh_token")),
                entry("response_types", List.of("code")),
                entry("token_endpoint_auth_method", "none"),
                entry("application_type", "web"),
                entry("dpop_bound_access_tokens", true)
        );
    }

    @GetMapping("/client-metadata.json")
    public Map<String, Object> getClientMetadata() {
        return metadata;
    }
}
