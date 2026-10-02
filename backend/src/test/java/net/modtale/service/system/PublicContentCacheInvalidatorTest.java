package net.modtale.service.system;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import net.modtale.config.properties.AppBackendProperties;
import net.modtale.config.properties.AppFrontendProperties;
import net.modtale.config.properties.PublicCachePurgeProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

class PublicContentCacheInvalidatorTest {
  private static final String ZONE = "ff20a05c676ca023b19d20e177054945";
  private final ObjectMapper mapper = new ObjectMapper();
  private final HttpClient http = mock(HttpClient.class);
  private final MutableClock clock = new MutableClock();

  private PublicContentCacheInvalidator service(boolean enabled, String site, String api) {
    return new PublicContentCacheInvalidator(
        new PublicCachePurgeProperties(enabled, ZONE, "test-token"),
        new AppFrontendProperties(site),
        new AppBackendProperties(api),
        mapper,
        http,
        clock);
  }

  @SuppressWarnings("unchecked")
  private HttpResponse<String> response(int status, String body, String retryAfter) {
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(status);
    when(response.body()).thenReturn(body);
    when(response.headers())
        .thenReturn(
            HttpHeaders.of(
                retryAfter == null ? Map.of() : Map.of("Retry-After", List.of(retryAfter)),
                (key, value) -> true));
    return response;
  }

  @Test
  void onlyApprovedMatchingEnvironmentAndExplicitOptInCanPurge() throws Exception {
    for (String site :
        List.of(
            "https://evil.example",
            "http://modtale.net",
            "https://modtale.net@evil.example",
            "https://modtale.net/some/path",
            "https://modtale.net?next=evil",
            "https://modtale.net:443",
            "https://dev.modtale.net")) {
      var service = service(true, site, "https://api.modtale.net");
      assertNull(service.apiCacheTag());
      service.contentChanged();
      service.flushPending();
    }
    service(false, "https://modtale.net", "https://api.modtale.net").contentChanged();
    var wrongZone =
        new PublicContentCacheInvalidator(
            new PublicCachePurgeProperties(true, "different-zone", "test-token"),
            new AppFrontendProperties("https://modtale.net"),
            new AppBackendProperties("https://api.modtale.net"),
            mapper,
            http,
            clock);
    wrongZone.contentChanged();
    verifyNoInteractions(http);
    assertFalse(new PublicCachePurgeProperties(true, ZONE, "secret").toString().contains("secret"));
  }

  @Test
  void firstWritePurgesSynchronouslyAndBurstsCoalesceWithoutLosingPendingChanges()
      throws Exception {
    when(http.<String>send(any(), any()))
        .thenAnswer(invocation -> response(200, "{\"success\":true}", null));
    var service = service(true, "https://dev.modtale.net/", "https://dev.api.modtale.net");
    service.contentChanged();
    service.contentChanged();
    service.contentChanged();
    var capture = ArgumentCaptor.forClass(HttpRequest.class);
    verify(http).send(capture.capture(), any());
    HttpRequest request = capture.getValue();
    assertEquals(
        "https://api.cloudflare.com/client/v4/zones/" + ZONE + "/purge_cache",
        request.uri().toString());
    assertEquals(Duration.ofSeconds(2), request.timeout().orElseThrow());
    assertEquals("POST", request.method());
    assertEquals(
        Map.of("tags", List.of("modtale-html-dev.modtale.net", "modtale-api-dev.api.modtale.net")),
        mapper.readValue(body(request), Map.class));
    clock.advance(Duration.ofSeconds(119));
    service.flushPending();
    verify(http, times(1)).send(any(), any());
    clock.advance(Duration.ofSeconds(1));
    service.flushPending();
    service.flushPending();
    verify(http, times(2)).send(any(), any());
  }

  @Test
  void failuresNeverBreakTheWriteAndRateLimitRetryAfterIsRespected() throws Exception {
    when(http.<String>send(any(), any()))
        .thenAnswer(invocation -> response(429, "{\"success\":false}", "300"))
        .thenThrow(new IOException("do-not-log-sensitive-request-details"))
        .thenAnswer(invocation -> response(200, "{\"success\":true}", null));
    var service = service(true, "https://modtale.net", "https://api.modtale.net");
    assertDoesNotThrow(service::contentChanged);
    clock.advance(Duration.ofSeconds(299));
    service.flushPending();
    verify(http, times(1)).send(any(), any());
    clock.advance(Duration.ofSeconds(1));
    assertDoesNotThrow(service::flushPending);
    clock.advance(Duration.ofSeconds(120));
    service.flushPending();
    verify(http, times(3)).send(any(), any());
  }

  @Test
  void apiSuccessFalseAndMalformedResponsesAreRetriedRatherThanMarkedComplete() throws Exception {
    when(http.<String>send(any(), any()))
        .thenAnswer(invocation -> response(200, "{\"success\":false}", null))
        .thenAnswer(invocation -> response(200, "not-json", null))
        .thenAnswer(invocation -> response(200, "{\"success\":true}", null));
    var service = service(true, "https://modtale.net", "https://api.modtale.net");
    assertDoesNotThrow(service::contentChanged);
    clock.advance(Duration.ofMinutes(2));
    assertDoesNotThrow(service::flushPending);
    clock.advance(Duration.ofMinutes(2));
    service.flushPending();
    verify(http, times(3)).send(any(), any());
  }

  @Test
  void rolledBackTransactionsDoNotPurgeAndCommittedChangesWaitUntilCommit() throws Exception {
    when(http.<String>send(any(), any()))
        .thenAnswer(invocation -> response(200, "{\"success\":true}", null));
    var service = service(true, "https://modtale.net", "https://api.modtale.net");
    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(true);
    try {
      service.contentChanged();
      verifyNoInteractions(http);
      var callbacks = TransactionSynchronizationManager.getSynchronizations();
      assertEquals(1, callbacks.size());
      callbacks.forEach(
          callback -> callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
      verifyNoInteractions(http);
      service.contentChanged();
      TransactionSynchronizationManager.getSynchronizations().getLast().afterCommit();
      verify(http).send(any(), any());
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
      TransactionSynchronizationManager.setActualTransactionActive(false);
    }
  }

  @Test
  void editArrivingDuringNetworkRequestRemainsPending() throws Exception {
    var service = service(true, "https://modtale.net", "https://api.modtale.net");
    when(http.<String>send(any(), any()))
        .thenAnswer(
            invocation -> {
              service.contentChanged();
              return response(200, "{\"success\":true}", null);
            });
    service.contentChanged();
    clock.advance(Duration.ofMinutes(2));
    service.flushPending();
    verify(http, times(2)).send(any(), any());
  }

  private String body(HttpRequest request) {
    var output = new java.io.ByteArrayOutputStream();
    request
        .bodyPublisher()
        .orElseThrow()
        .subscribe(
            new Flow.Subscriber<ByteBuffer>() {
              @Override
              public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
              }

              @Override
              public void onNext(ByteBuffer buffer) {
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                output.writeBytes(bytes);
              }

              @Override
              public void onError(Throwable error) {
                throw new AssertionError(error);
              }

              @Override
              public void onComplete() {}
            });
    return output.toString(StandardCharsets.UTF_8);
  }

  private static class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-10-02T00:00:00Z");

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
