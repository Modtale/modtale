package net.modtale.launcher.hytale;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class HytaleAuthCallbackTest {
    enum Failure { NONE, HEADERS, BODY, BODY_CLOSE, EXCHANGE_CLOSE }

    @ParameterizedTest
    @EnumSource(Failure.class)
    void validGrantCompletesAfterResponseEvenWhenBrowserDisconnects(Failure failure) throws Exception {
        var state = HytaleAuthService.stateForPort(12345);
        var future = new CompletableFuture<String>();
        var exchange = new RecordingExchange("/?code=valid-code&state=" + state.callbackState(), future, failure);
        invoke(exchange, future, state, failure);
        assertEquals("valid-code", future.join());
        assertTrue(exchange.closed);
        assertEquals("exchange-close", exchange.events.getLast());
        if (failure == Failure.NONE) {
            assertEquals(List.of("headers", "body", "body-close", "exchange-close"), exchange.events);
            assertTrue(exchange.body.toString(StandardCharsets.UTF_8).contains("Authorization successful"));
            assertEquals(exchange.body.size(), exchange.responseLength);
        }
    }

    @ParameterizedTest
    @EnumSource(Failure.class)
    void authorizationErrorsAlsoCompleteOnlyAfterClosingResponse(Failure failure) throws Exception {
        var state = HytaleAuthService.stateForPort(12345);
        for (String query : List.of("error=access_denied", "code=invalid-code&state=wrong")) {
            var future = new CompletableFuture<String>();
            var exchange = new RecordingExchange("/?" + query, future, failure);
            invoke(exchange, future, state, failure);
            var error = assertThrows(CompletionException.class, future::join);
            assertInstanceOf(HytaleApiException.class, error.getCause());
            assertTrue(error.getCause().getMessage().contains(query.startsWith("error=") ? "access_denied" : "unexpected state"));
            assertTrue(exchange.closed);
            if (failure == Failure.NONE) {
                assertEquals(List.of("headers", "body", "body-close", "exchange-close"), exchange.events);
                assertTrue(exchange.body.toString(StandardCharsets.UTF_8).contains("Authorization failed"));
            }
        }
    }

    @Test
    void requestWithoutGrantClosesResponseAndKeepsWaiting() throws Exception {
        var future = new CompletableFuture<String>();
        var exchange = new RecordingExchange("/favicon.ico", future, Failure.NONE);
        HytaleAuthService.handleCallback(exchange, future, HytaleAuthService.stateForPort(12345));
        assertTrue(exchange.closed);
        assertFalse(future.isDone());
        assertTrue(exchange.body.toString(StandardCharsets.UTF_8).contains("Waiting for authorization"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void loopbackResponseSurvivesImmediateServerStopOnCompletion(boolean error) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 1);
        var future = new CompletableFuture<String>();
        var state = HytaleAuthService.stateForPort(server.getAddress().getPort());
        server.createContext("/", exchange -> HytaleAuthService.handleCallback(exchange, future, state));
        var stopped = future.whenComplete((code, failure) -> server.stop(0));
        server.start();
        try (HttpClient client = HttpClient.newHttpClient()) {
            String query = error ? "error=access_denied" : "code=valid-code&state=" + state.callbackState();
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/?" + query);
            var response = client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains(error ? "Authorization failed" : "Authorization successful"));
            assertTrue(response.body().stripTrailing().endsWith("</html>"));
            assertEquals(response.body().getBytes(StandardCharsets.UTF_8).length,
                    response.headers().firstValueAsLong("Content-Length").orElseThrow());
            if (error) assertThrows(CompletionException.class, stopped::join);
            else assertEquals("valid-code", stopped.join());
        } finally {
            server.stop(0);
        }
    }

    private static void invoke(RecordingExchange exchange, CompletableFuture<String> future,
            HytaleAuthService.OAuthState state, Failure failure) throws Exception {
        if (failure == Failure.NONE) HytaleAuthService.handleCallback(exchange, future, state);
        else if (failure == Failure.EXCHANGE_CLOSE) {
            assertThrows(IllegalStateException.class, () -> HytaleAuthService.handleCallback(exchange, future, state));
        } else {
            assertThrows(IOException.class, () -> HytaleAuthService.handleCallback(exchange, future, state));
        }
    }

    private static final class RecordingExchange extends HttpExchange {
        final URI uri;
        final CompletableFuture<String> future;
        final Failure failure;
        final Headers headers = new Headers();
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        final List<String> events = new ArrayList<>();
        long responseLength;
        boolean closed;

        RecordingExchange(String uri, CompletableFuture<String> future, Failure failure) {
            this.uri = URI.create(uri);
            this.future = future;
            this.failure = failure;
        }

        private void pending(String stage) {
            assertFalse(future.isDone(), "Authorization completed during " + stage);
            events.add(stage);
        }

        @Override public void sendResponseHeaders(int code, long length) throws IOException {
            pending("headers");
            assertEquals(200, code);
            assertEquals("text/html; charset=utf-8", headers.getFirst("Content-Type"));
            responseLength = length;
            if (failure == Failure.HEADERS) throw new IOException("browser disconnected during headers");
        }
        @Override public OutputStream getResponseBody() {
            assertFalse(future.isDone());
            return new OutputStream() {
                @Override public void write(int value) throws IOException { write(new byte[]{(byte) value}); }
                @Override public void write(byte[] bytes, int offset, int length) throws IOException {
                    pending("body");
                    if (failure == Failure.BODY) throw new IOException("browser disconnected during body");
                    body.write(bytes, offset, length);
                }
                @Override public void close() throws IOException {
                    pending("body-close");
                    if (failure == Failure.BODY_CLOSE) throw new IOException("browser disconnected on close");
                }
            };
        }
        @Override public void close() {
            pending("exchange-close");
            closed = true;
            if (failure == Failure.EXCHANGE_CLOSE) throw new IllegalStateException("exchange close failed");
        }
        @Override public Headers getRequestHeaders() { return new Headers(); }
        @Override public Headers getResponseHeaders() { return headers; }
        @Override public URI getRequestURI() { return uri; }
        @Override public String getRequestMethod() { return "GET"; }
        @Override public HttpContext getHttpContext() { return null; }
        @Override public InputStream getRequestBody() { return InputStream.nullInputStream(); }
        @Override public InetSocketAddress getRemoteAddress() { return null; }
        @Override public int getResponseCode() { return 200; }
        @Override public InetSocketAddress getLocalAddress() { return null; }
        @Override public String getProtocol() { return "HTTP/1.1"; }
        @Override public Object getAttribute(String name) { return null; }
        @Override public void setAttribute(String name, Object value) {}
        @Override public void setStreams(InputStream input, OutputStream output) {}
        @Override public HttpPrincipal getPrincipal() { return null; }
    }
}
