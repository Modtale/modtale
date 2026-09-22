package net.modtale.launcher.api;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

final class ApiPathBuilder {

    private ApiPathBuilder() {
    }

    static URI normalizeBaseUri(String apiBaseUrl, String fallbackBaseUrl) {
        String value = apiBaseUrl == null || apiBaseUrl.isBlank() ? fallbackBaseUrl : apiBaseUrl.trim();
        return URI.create(value.replaceAll("/+$", ""));
    }

    static URI apiUri(URI apiBaseUri, String pathAndQuery) {
        String base = apiBaseUri.toString().replaceAll("/+$", "");
        String path = pathAndQuery.startsWith("/") ? pathAndQuery : "/" + pathAndQuery;
        return URI.create(base + path);
    }

    static void addParam(List<String> params, String name, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        params.add(encodeQuery(name) + "=" + encodeQuery(value));
    }

    static String encodePath(String value) {
        return encodeQuery(value).replace("+", "%20");
    }

    static String encodeQuery(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
