package net.modtale.controller.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;

@RestController
public class ClientMetadataController {

    @Value("${app.backend.url:https://api.modtale.net}")
    private String backendUrl;

    @Value("${app.frontend.url:https://modtale.net}")
    private String frontendUrl;

    @GetMapping("/client-metadata.json")
    public Map<String, Object> getClientMetadata() {
        String clientId = backendUrl + "/client-metadata.json";
        String redirectUri = backendUrl + "/login/oauth2/code/bluesky";

        return Map.ofEntries(
                entry("client_id", clientId),
                entry("client_name", "Modtale"),
                entry("client_uri", backendUrl),
                entry("logo_uri", frontendUrl + "/assets/logo.svg"),
                entry("tos_uri", frontendUrl + "/terms"),
                entry("policy_uri", frontendUrl + "/privacy"),
                entry("redirect_uris", List.of(redirectUri)),
                entry("scope", "atproto transition:generic"),
                entry("grant_types", Arrays.asList("authorization_code", "refresh_token")),
                entry("response_types", List.of("code")),
                entry("token_endpoint_auth_method", "none"),
                entry("application_type", "web"),
                entry("dpop_bound_access_tokens", true)
        );
    }
}