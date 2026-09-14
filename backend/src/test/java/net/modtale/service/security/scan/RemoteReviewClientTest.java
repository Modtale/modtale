package net.modtale.service.security.scan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.*;
import net.modtale.config.properties.AppWardenProperties;
import net.modtale.model.project.*;
import org.junit.jupiter.api.*;
import java.net.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class RemoteReviewClientTest {
    HttpServer server;RemoteReviewClient client;ObjectMapper mapper=new ObjectMapper();
    byte[] bytes="inert original".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    String request=UUID.randomUUID().toString(),job=UUID.randomUUID().toString();RemoteReviewBinding binding;
    AtomicInteger calls=new AtomicInteger(),posts=new AtomicInteger();
    interface Handler { void handle(HttpExchange exchange)throws Exception; }
    void route(Handler handler) {
        server.createContext("/api/v1/review-jobs",exchange->{calls.incrementAndGet();if(exchange.getRequestMethod().equals("POST"))posts.incrementAndGet();
            try {assertEquals("fixture-key",exchange.getRequestHeaders().getFirst("X-Warden-Api-Key"));handler.handle(exchange);}
            catch(Exception failure){throw new RuntimeException(failure);}finally{exchange.close();}});
    }
    void reply(HttpExchange exchange,int code,Object value)throws Exception {
        byte[] body=mapper.writeValueAsBytes(value);exchange.getResponseHeaders().set("Content-Type","application/json");
        exchange.sendResponseHeaders(code,body.length);exchange.getResponseBody().write(body);
    }
    Map<String,Object> status(String state) {
        var result=new LinkedHashMap<String,Object>();result.put("jobId",job);result.put("requestId",request);result.put("binding",wireBinding());
        result.put("state",state);result.put("artifactRetained",!state.equals("AWAITING_UPLOAD"));result.put("createdAt",1000L);result.put("expiresAt",2000L);result.put("workState",null);return result;
    }
    Map<String,String> wireBinding(){return Map.of("artifactSha256",binding.artifactSha256(),"contextSha256",binding.contextSha256(),"policyVersion",binding.policyVersion(),"reviewConfigSha256",binding.reviewConfigSha256());}
    @BeforeEach void setup()throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());server.start();
        client=new RemoteReviewClient(new AppWardenProperties("http://127.0.0.1:"+server.getAddress().getPort(),"fixture-key",true,9,600),Duration.ofSeconds(2));
        binding=new RemoteReviewBinding("p","v",request,1,"original.zip",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)),
                "b".repeat(64),"warden-3.0.0:"+"c".repeat(64),"d".repeat(64),null);
    }
    @AfterEach void cleanup(){client.close();server.stop(0);}
    @Test void lostSubmissionResponseIsRecoveredBySameRequestWithoutAnotherPost() {
        var accepted=new AtomicBoolean();route(e->{
            if(e.getRequestMethod().equals("POST")){assertTrue(new String(e.getRequestBody().readAllBytes()).contains(request));accepted.set(true);return;}
            assertTrue(e.getRequestURI().getPath().endsWith(request));assertTrue(e.getRequestURI().getQuery().contains(binding.contextSha256()));
            reply(e,accepted.get()?200:404,accepted.get()?status("QUEUED"):Map.of());
        });
        assertThrows(RemoteReviewClient.Unavailable.class,()->client.submitOrFind(binding,bytes));
        assertEquals(job,client.submitOrFind(binding,bytes).jobId());assertEquals(1,posts.get());assertEquals(3,calls.get());
    }
    @Test void awaitingUploadResubmitsOnlyOriginalRequestAndRequiresSameJob() {
        route(e->{if(e.getRequestMethod().equals("POST")){e.getRequestBody().readAllBytes();var changed=status("QUEUED");changed.put("jobId",UUID.randomUUID().toString());reply(e,202,changed);}else reply(e,200,status("AWAITING_UPLOAD"));});
        assertThrows(RemoteReviewClient.Unavailable.class,()->client.submitOrFind(binding,bytes));assertEquals(1,posts.get());
    }
    @Test void submissionRejectsWrongOriginalBeforeAnyHttp() {
        route(e->reply(e,200,status("QUEUED")));assertThrows(IllegalArgumentException.class,()->client.submitOrFind(binding,new byte[]{1}));assertEquals(0,calls.get());
    }
    @Test void mismatchedIdentityAndUnknownStateNeverBecomeUsableStatus() {
        var response=new AtomicReference<>(status("QUEUED"));route(e->reply(e,200,response.get()));
        response.get().put("requestId",UUID.randomUUID().toString());assertThrows(RemoteReviewClient.Unavailable.class,()->client.find(binding));
        response.set(status("NEW_UNKNOWN_STATE"));assertThrows(RemoteReviewClient.Unavailable.class,()->client.status(binding.withJobId(job)));
        response.set(status("QUEUED"));response.get().put("binding",Map.of());assertThrows(RemoteReviewClient.Unavailable.class,()->client.status(binding.withJobId(job)));
    }
    @Test void redirectsAndTemporaryFailuresAreNotRetriedOrTurnedIntoFindings() {
        route(e->{e.getResponseHeaders().set("Location","/leaked");reply(e,307,Map.of());});
        assertEquals(307,assertThrows(RemoteReviewClient.Unavailable.class,()->client.find(binding)).status());assertEquals(1,calls.get());
    }
    @Test void onlyNotFoundPermitsSubmission() {
        route(e->reply(e,503,Map.of()));assertEquals(503,assertThrows(RemoteReviewClient.Unavailable.class,()->client.submitOrFind(binding,bytes)).status());assertEquals(0,posts.get());assertEquals(1,calls.get());
    }
    @Test void boundResultRetainsOriginalContextAndRejectsWrongJob() {
        var response=new LinkedHashMap<String,Object>();response.put("jobId",job);response.put("requestId",request);response.put("binding",wireBinding());response.put("completedAt",1500L);
        var scan=ScanEvidenceFixtures.complete(true);var old=scan.getSecurityEvidence();
        scan.setSecurityEvidence(new ScanResult.SecurityEvidence(binding.policyVersion(),binding.artifactSha256(),old.contentSha256(),true,true,old.reviewState(),old.entryHashes()));response.put("scan",scan);
        route(e->reply(e,200,response));var result=client.result(binding.withJobId(job));
        assertTrue(result.isArtifactVerified());assertEquals(binding.contextSha256(),result.getReviewedContextSha256());assertEquals(request,result.getScanRequestId());
        response.put("jobId",UUID.randomUUID().toString());assertThrows(RemoteReviewClient.Unavailable.class,()->client.result(binding.withJobId(job)));
    }
    @Test void duplicateJsonKeysAndOversizedStatusAreRejected() {
        var raw=new AtomicReference<>("{\"policyVersion\":\"a\",\"policyVersion\":\"b\"}");route(e->{byte[] body=raw.get().getBytes();e.sendResponseHeaders(200,body.length);e.getResponseBody().write(body);});
        assertThrows(RemoteReviewClient.Unavailable.class,()->client.configuration());raw.set(" ".repeat(65537));assertThrows(RemoteReviewClient.Unavailable.class,()->client.configuration());
    }
    @Test void closeCancelsActiveRequestsAndRejectsFurtherCalls()throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);route(e->{entered.countDown();release.await(5,TimeUnit.SECONDS);});
        try(var executor=Executors.newSingleThreadExecutor()) {
            var pending=executor.submit(()->assertThrows(RemoteReviewClient.Unavailable.class,()->client.find(binding)));
            try {assertTrue(entered.await(2,TimeUnit.SECONDS));client.close();pending.get(1,TimeUnit.SECONDS);assertThrows(RemoteReviewClient.Unavailable.class,()->client.find(binding));}
            finally{release.countDown();}
        }
        assertEquals(1,calls.get());
    }
    @Test void thirdConcurrentRequestIsRejectedWithoutHttpAndCloseDrainsBoth()throws Exception {
        var entered=new CountDownLatch(2);var release=new CountDownLatch(1);route(e->{entered.countDown();release.await(5,TimeUnit.SECONDS);});
        try(var executor=Executors.newFixedThreadPool(2)) {
            var first=executor.submit(()->assertThrows(RemoteReviewClient.Unavailable.class,()->client.find(binding)));
            var second=executor.submit(()->assertThrows(RemoteReviewClient.Unavailable.class,()->client.find(binding)));
            try {assertTrue(entered.await(2,TimeUnit.SECONDS));assertEquals(503,assertThrows(RemoteReviewClient.Unavailable.class,()->client.find(binding)).status());
                assertEquals(2,calls.get());client.close();first.get(1,TimeUnit.SECONDS);second.get(1,TimeUnit.SECONDS);}
            finally{release.countDown();}
        }
    }
    @Test void deadlineCancelsSlowResponseWithoutRetry() {
        client.close();client=new RemoteReviewClient(new AppWardenProperties("http://127.0.0.1:"+server.getAddress().getPort(),"fixture-key",true,9,600),Duration.ofMillis(150));
        var release=new CountDownLatch(1);route(e->release.await(3,TimeUnit.SECONDS));
        long start=System.nanoTime();
        try {assertThrows(RemoteReviewClient.Unavailable.class,()->client.find(binding));assertTrue(System.nanoTime()-start<TimeUnit.SECONDS.toNanos(2));assertEquals(1,calls.get());}
        finally{release.countDown();}
    }
    @Test void configurationAndCancellationUseAuthenticatedBoundRoutes() {
        route(e->{if(e.getRequestURI().getPath().endsWith("configuration"))reply(e,200,Map.of("policyVersion",binding.policyVersion(),"reviewConfigSha256",binding.reviewConfigSha256()));
            else {assertEquals("DELETE",e.getRequestMethod());assertTrue(e.getRequestURI().getQuery().contains(request));reply(e,200,status("CANCELLED"));}});
        assertEquals(binding.reviewConfigSha256(),client.configuration().reviewConfigSha256());assertEquals("CANCELLED",client.cancel(binding.withJobId(job)).state());
    }
}
