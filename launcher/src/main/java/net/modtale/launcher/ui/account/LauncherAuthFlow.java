package net.modtale.launcher.ui.account;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.modtale.launcher.api.ModtaleApiClient;
import net.modtale.launcher.logging.LogSanitizer;
import net.modtale.launcher.model.user.CurrentUser;
import net.modtale.launcher.settings.LauncherConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class LauncherAuthFlow {

    private static final Logger LOG = LogManager.getLogger(LauncherAuthFlow.class);
    private static final Duration AUTH_TIMEOUT = Duration.ofMinutes(5);
    private static final String APP_NAME = "Modtale Launcher";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ModtaleApiClient apiClient;
    private final String siteBaseUrl;

    public LauncherAuthFlow(ModtaleApiClient apiClient) {
        this.apiClient = apiClient;
        this.siteBaseUrl = LauncherConfig.siteBaseUrl();
    }

    public CurrentUser authenticate() {
        return authenticate(this::buildAuthUri);
    }

    public CurrentUser authenticateWithOAuthProvider(String provider) {
        return authenticate((callbackUri, state) -> buildOAuthAuthUri(provider, callbackUri, state));
    }

    private CurrentUser authenticate(AuthUriFactory authUriFactory) {
        HttpServer callbackServer = null;
        CompletableFuture<String> codeFuture = new CompletableFuture<>();
        try {
            String state = randomState();
            callbackServer = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
            callbackServer.createContext("/callback", exchange -> handleCallback(exchange, codeFuture, state));
            callbackServer.setExecutor(null);
            callbackServer.start();

            URI callbackUri = URI.create("http://127.0.0.1:" + callbackServer.getAddress().getPort() + "/callback");
            openBrowser(authUriFactory.build(callbackUri, state));

            String code = codeFuture.get(AUTH_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            apiClient.exchangeLauncherCode(code);
            return apiClient.currentUser();
        } catch (TimeoutException ex) {
            LOG.warn("Launcher sign-in timed out.", ex);
            throw new RuntimeException("Launcher sign-in timed out. Please try again.", ex);
        } catch (CancellationException ex) {
            LOG.warn("Launcher sign-in was cancelled.", ex);
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOG.warn("Launcher sign-in was interrupted.", ex);
            throw new RuntimeException("Launcher sign-in was interrupted.", ex);
        } catch (Exception ex) {
            LOG.warn("Launcher sign-in failed.", ex);
            if (ex instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(ex.getMessage(), ex);
        } finally {
            if (callbackServer != null) {
                callbackServer.stop(0);
            }
        }
    }

    private URI buildAuthUri(URI callbackUri, String state) {
        String base = siteBaseUrl.replaceAll("/+$", "");
        String query = "redirect_uri=" + encode(callbackUri.toString())
                + "&state=" + encode(state)
                + "&app_name=" + encode(APP_NAME);
        return URI.create(base + "/launcher/auth?" + query);
    }

    private URI buildOAuthAuthUri(String provider, URI callbackUri, String state) {
        String normalizedProvider = provider == null ? "" : provider.trim().toLowerCase(java.util.Locale.ROOT);
        if (!normalizedProvider.matches("[a-z0-9_-]+")) {
            throw new RuntimeException("That OAuth provider is not valid.");
        }
        String base = apiClient.apiBaseUri().toString().replaceAll("/+$", "");
        String query = "redirect_uri=" + encode(callbackUri.toString())
                + "&state=" + encode(state)
                + "&app_name=" + encode(APP_NAME);
        return URI.create(base + "/auth/launcher/oauth/" + encodePath(normalizedProvider) + "?" + query);
    }

    private void handleCallback(HttpExchange exchange, CompletableFuture<String> codeFuture, String expectedState) throws IOException {
        Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
        String responseTitle = "Launcher sign-in complete";
        String responseMessage = "You can return to Modtale Launcher.";

        String returnedState = params.get("state");
        String code = params.get("code");
        String error = params.get("error");
        if (error != null && !error.isBlank()) {
            LOG.warn("Launcher sign-in callback returned an error: " + error);
            codeFuture.completeExceptionally(new RuntimeException("Launcher sign-in failed: " + error));
            responseTitle = "Launcher sign-in failed";
            responseMessage = "Return to Modtale Launcher and try again.";
        } else if (!expectedState.equals(returnedState)) {
            LOG.warn("Launcher sign-in callback returned an unexpected state.");
            codeFuture.completeExceptionally(new RuntimeException("Launcher sign-in returned an unexpected state."));
            responseTitle = "Launcher sign-in failed";
            responseMessage = "Return to Modtale Launcher and try again.";
        } else if (code == null || code.isBlank()) {
            LOG.warn("Launcher sign-in callback did not include an authorization code.");
            codeFuture.completeExceptionally(new RuntimeException("Launcher sign-in did not return an authorization code."));
            responseTitle = "Launcher sign-in failed";
            responseMessage = "Return to Modtale Launcher and try again.";
        } else {
            codeFuture.complete(code);
        }

        byte[] body = callbackPage(responseTitle, responseMessage).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static void openBrowser(URI uri) {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            throw new RuntimeException("Desktop browser integration is not available. Could not open Modtale sign-in.");
        }
        try {
            LOG.info("Opening sign-in URL " + LogSanitizer.uri(uri));
            Desktop.getDesktop().browse(uri);
        } catch (IOException ex) {
            LOG.warn("Could not open sign-in URL " + LogSanitizer.uri(uri), ex);
            throw new RuntimeException("Could not open Modtale sign-in in your browser.", ex);
        }
    }

    private static String callbackPage(String title, String message) {
        return """
                <!doctype html>
                <html lang="en">
                  <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>%s</title>
                    <style>
                      :root { color-scheme: dark; font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
                      body { margin: 0; min-height: 100vh; display: grid; place-items: center; background: #0b1120; color: #cbd5e1; }
                      main { width: min(92vw, 520px); border: 1px solid rgba(255,255,255,.1); border-radius: 24px; background: rgba(15,23,42,.9); padding: 32px; box-shadow: 0 24px 70px rgba(2,6,23,.45); }
                      h1 { margin: 0 0 10px; color: white; font-size: 28px; letter-spacing: 0; }
                      p { margin: 0; line-height: 1.6; }
                    </style>
                    <script>setTimeout(function(){ window.close(); }, 1600);</script>
                  </head>
                  <body><main><h1>%s</h1><p>%s</p></main></body>
                </html>
                """.formatted(escapeHtml(title), escapeHtml(title), escapeHtml(message));
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> params = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            int separator = pair.indexOf('=');
            String key = separator >= 0 ? pair.substring(0, separator) : pair;
            String value = separator >= 0 ? pair.substring(separator + 1) : "";
            params.put(decode(key), decode(value));
        }
        return params;
    }

    private static String randomState() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String encodePath(String value) {
        return encode(value).replace("+", "%20");
    }

    private static String decode(String value) {
        return URLDecoder.decode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String escapeHtml(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    @FunctionalInterface
    private interface AuthUriFactory {
        URI build(URI callbackUri, String state);
    }
}
