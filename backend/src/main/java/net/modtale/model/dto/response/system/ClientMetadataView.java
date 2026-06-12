package net.modtale.model.dto.response.system;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ClientMetadataView(
        @JsonProperty("client_id") String clientId,
        @JsonProperty("client_name") String clientName,
        @JsonProperty("client_uri") String clientUri,
        @JsonProperty("logo_uri") String logoUri,
        @JsonProperty("tos_uri") String tosUri,
        @JsonProperty("policy_uri") String policyUri,
        @JsonProperty("redirect_uris") List<String> redirectUris,
        String scope,
        @JsonProperty("grant_types") List<String> grantTypes,
        @JsonProperty("response_types") List<String> responseTypes,
        @JsonProperty("token_endpoint_auth_method") String tokenEndpointAuthMethod,
        @JsonProperty("application_type") String applicationType,
        @JsonProperty("dpop_bound_access_tokens") boolean dpopBoundAccessTokens
) {}
