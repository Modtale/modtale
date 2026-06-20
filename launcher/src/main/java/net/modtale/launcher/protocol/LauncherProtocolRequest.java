package net.modtale.launcher.protocol;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.application.Application;

public record LauncherProtocolRequest(String installListId) {

    private static final LauncherProtocolRequest EMPTY = new LauncherProtocolRequest("");

    public LauncherProtocolRequest {
        installListId = value(installListId);
    }

    public boolean hasInstallList() {
        return !installListId.isBlank();
    }

    public static LauncherProtocolRequest from(Application.Parameters parameters) {
        if (parameters == null) {
            return EMPTY;
        }

        String namedListId = firstValue(parameters.getNamed(), "listId", "installList", "install-list", "list");
        if (!namedListId.isBlank()) {
            return new LauncherProtocolRequest(namedListId);
        }

        LauncherProtocolRequest rawRequest = firstProtocolUrl(parameters.getRaw());
        if (rawRequest.hasInstallList()) {
            return rawRequest;
        }

        return firstProtocolUrl(parameters.getUnnamed());
    }

    private static LauncherProtocolRequest firstProtocolUrl(List<String> values) {
        if (values == null) {
            return EMPTY;
        }
        for (String value : values) {
            LauncherProtocolRequest request = fromProtocolUrl(value);
            if (request.hasInstallList()) {
                return request;
            }
        }
        return EMPTY;
    }

    private static LauncherProtocolRequest fromProtocolUrl(String value) {
        if (value == null || !value.trim().startsWith("modtale:")) {
            return EMPTY;
        }
        try {
            URI uri = URI.create(value.trim());
            if (!"modtale".equalsIgnoreCase(uri.getScheme())) {
                return EMPTY;
            }
            String action = value(uri.getHost());
            if (!"install-list".equalsIgnoreCase(action)) {
                return EMPTY;
            }
            Map<String, String> params = queryParams(uri.getRawQuery());
            return new LauncherProtocolRequest(params.get("listId"));
        } catch (IllegalArgumentException ignored) {
            return EMPTY;
        }
    }

    private static Map<String, String> queryParams(String rawQuery) {
        Map<String, String> params = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return params;
        }
        for (String part : rawQuery.split("&")) {
            if (part.isBlank()) {
                continue;
            }
            int separator = part.indexOf('=');
            String key = separator >= 0 ? part.substring(0, separator) : part;
            String rawValue = separator >= 0 ? part.substring(separator + 1) : "";
            params.put(decode(key), decode(rawValue));
        }
        return params;
    }

    private static String firstValue(Map<String, String> values, String... keys) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        for (String key : keys) {
            String value = values.get(key);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String decode(String value) {
        return URLDecoder.decode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
