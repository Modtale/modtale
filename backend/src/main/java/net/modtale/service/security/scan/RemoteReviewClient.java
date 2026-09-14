package net.modtale.service.security.scan;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import net.modtale.config.properties.AppWardenProperties;
import net.modtale.model.project.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.core.publisher.Sinks;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@ConditionalOnProperty(name="app.warden.jobs.enabled",havingValue="true")
public final class RemoteReviewClient implements AutoCloseable {
    public record Configuration(String policyVersion,String reviewConfigSha256) {}
    public record Status(String jobId,String state,boolean artifactRetained,long createdAt,long expiresAt,String workState) {}
    public static final class Unavailable extends RuntimeException {
        private final int status;
        Unavailable(int status) { super("Remote review service unavailable ("+status+")");this.status=status; }
        public int status() { return status; }
    }
    private final WebClient client;
    private final ConnectionProvider connections;
    private final Semaphore slots=new Semaphore(2);
    private final AtomicBoolean closed=new AtomicBoolean();
    private final Sinks.Empty<Void> stop=Sinks.empty();
    private final Duration timeout;
    private final ObjectMapper mapper=new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(64).maxStringLength(16*1024*1024).maxNumberLength(20).build()).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    @org.springframework.beans.factory.annotation.Autowired
    public RemoteReviewClient(AppWardenProperties properties) { this(properties,Duration.ofSeconds(30)); }
    RemoteReviewClient(AppWardenProperties properties,Duration timeout) {
        URI base=URI.create(properties.url());
        if (!properties.enabled() || properties.apiKey()==null || properties.apiKey().isBlank()
                || base.getHost()==null || !Set.of("http","https").contains(base.getScheme()) || base.getUserInfo()!=null
                || base.getQuery()!=null || base.getFragment()!=null || !(base.getPath().isEmpty() || base.getPath().equals("/"))
                || timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofSeconds(30))>0)
            throw new IllegalArgumentException("Invalid remote review configuration");
        this.timeout=timeout;
        connections=ConnectionProvider.builder("remote-review").maxConnections(2).pendingAcquireMaxCount(2)
                .pendingAcquireTimeout(timeout).build();
        var http=reactor.netty.http.client.HttpClient.create(connections).disableRetry(true).followRedirect(false)
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS,5000).responseTimeout(timeout);
        client=WebClient.builder().baseUrl(base.toString()).clientConnector(new ReactorClientHttpConnector(http))
                .defaultHeader("X-Warden-Api-Key",properties.apiKey())
                .codecs(c->c.defaultCodecs().maxInMemorySize(16*1024*1024)).build();
    }
    public Configuration configuration() {
        var body=exchange(client.get().uri("/api/v1/review-jobs/configuration"),200,65536);
        fields(body,"policyVersion","reviewConfigSha256");String policy=text(body,"policyVersion"),config=text(body,"reviewConfigSha256");
        if (!policy.matches("warden-3\\.0\\.0:[0-9a-f]{64}") || !digest(config)) throw new Unavailable(502);
        return new Configuration(policy,config);
    }
    public Optional<Status> find(RemoteReviewBinding binding) {
        try { return Optional.of(statusBody(exchange(client.get().uri(uri(binding,"/requests/"+binding.requestId(),false)),200,65536),binding)); }
        catch(Unavailable failure) { if(failure.status()==404)return Optional.empty();throw failure; }
    }
    public static final class Superseded extends RuntimeException { public Superseded(){super("Remote review ownership changed");} }
    public Status submitOrFind(RemoteReviewBinding binding,byte[] bytes) {
        if(binding.jobId()==null)validateOriginal(binding,bytes);
        return submitOrFind(binding,()->bytes,()->true);
    }
    public Status submitOrFind(RemoteReviewBinding binding,java.util.function.Supplier<byte[]> original,java.util.function.BooleanSupplier current) {
        if(!current.getAsBoolean())throw new Superseded();
        var found=binding.jobId()!=null?Optional.of(status(binding)):find(binding);
        if(!current.getAsBoolean())throw new Superseded();
        if(found.isPresent() && !found.get().state().equals("AWAITING_UPLOAD"))return found.get();
        byte[] bytes=original.get();validateOriginal(binding,bytes);
        if(!current.getAsBoolean())throw new Superseded();
        var form=new MultipartBodyBuilder();form.part("requestId",binding.requestId());form.part("artifactSha256",binding.artifactSha256());
        form.part("contextSha256",binding.contextSha256());form.part("policyVersion",binding.policyVersion());form.part("reviewConfigSha256",binding.reviewConfigSha256());
        form.part("file",new ByteArrayResource(bytes){@Override public String getFilename(){return "artifact.zip";}});
        return statusBody(exchange(client.post().uri("/api/v1/review-jobs").contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(form.build())),202,65536,current),found.isPresent()?binding.withJobId(found.get().jobId()):binding);
    }
    private static void validateOriginal(RemoteReviewBinding binding,byte[] bytes) {
        if(bytes==null || bytes.length==0 || bytes.length>100*1024*1024 || !binding.artifactSha256().equals(hash(bytes)))
            throw new IllegalArgumentException("Original artifact does not match review binding");
    }
    public Status status(RemoteReviewBinding binding) {
        requireJob(binding);return statusBody(exchange(client.get().uri(uri(binding,"/"+binding.jobId(),true)),200,65536),binding);
    }
    public Status cancel(RemoteReviewBinding binding) {
        requireJob(binding);return statusBody(exchange(client.delete().uri(uri(binding,"/"+binding.jobId(),true)),200,65536),binding);
    }
    public ScanResult result(RemoteReviewBinding binding) {
        requireJob(binding);var body=exchange(client.get().uri(uri(binding,"/"+binding.jobId()+"/result",true)),200,16*1024*1024);
        fields(body,"jobId","requestId","binding","completedAt","scan");identity(body,binding);
        if(number(body,"completedAt")<=0 || !body.path("scan").isObject())throw new Unavailable(502);
        try {
            var scan=mapper.treeToValue(body.get("scan"),ScanResult.class);var evidence=scan.getSecurityEvidence();
            if(evidence==null || !binding.artifactSha256().equals(evidence.artifactSha256()) || !binding.policyVersion().equals(evidence.policyVersion()))throw new Unavailable(502);
            scan.setArtifactVerified(evidence.complete());scan.setReviewedContextSha256(binding.contextSha256());scan.setScanRequestId(binding.requestId());
            return scan;
        }catch(Unavailable failure){throw failure;}catch(Exception invalid){throw new Unavailable(502);}
    }
    private Status statusBody(JsonNode body,RemoteReviewBinding binding) {
        fields(body,"jobId","requestId","binding","state","artifactRetained","createdAt","expiresAt","workState");identity(body,binding);
        String state=text(body,"state");long created=number(body,"createdAt"),expires=number(body,"expiresAt");
        if(!Set.of("QUEUED","RUNNING","COMPLETED","CANCELLED","EXPIRED","HELD","UPLOADING","AWAITING_UPLOAD").contains(state)
                || !body.path("artifactRetained").isBoolean() || created<=0 || expires<=created
                || !(body.path("workState").isNull() || body.path("workState").isTextual() && body.path("workState").textValue().length()<=128))throw new Unavailable(502);
        boolean retained=body.get("artifactRetained").booleanValue();
        if(Set.of("QUEUED","RUNNING","COMPLETED","HELD").contains(state)&&!retained)throw new Unavailable(502);
        return new Status(text(body,"jobId"),state,retained,created,expires,body.get("workState").isNull()?null:body.get("workState").textValue());
    }
    private void identity(JsonNode body,RemoteReviewBinding expected) {
        String job=text(body,"jobId");
        if(!job.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                || expected.jobId()!=null&&!expected.jobId().equals(job) || !expected.requestId().equals(text(body,"requestId")))throw new Unavailable(502);
        var b=body.path("binding");fields(b,"artifactSha256","contextSha256","policyVersion","reviewConfigSha256");
        if(!expected.artifactSha256().equals(text(b,"artifactSha256")) || !expected.contextSha256().equals(text(b,"contextSha256"))
                || !expected.policyVersion().equals(text(b,"policyVersion")) || !expected.reviewConfigSha256().equals(text(b,"reviewConfigSha256")))throw new Unavailable(502);
    }
    private JsonNode exchange(WebClient.RequestHeadersSpec<?> request,int expected,int max) {return exchange(request,expected,max,()->true);}
    private JsonNode exchange(WebClient.RequestHeadersSpec<?> request,int expected,int max,java.util.function.BooleanSupplier current) {
        if(closed.get() || !slots.tryAcquire())throw new Unavailable(503);
        try {
            if(closed.get())throw new Unavailable(503);
            if(!current.getAsBoolean())throw new Superseded();
            byte[] bytes=request.exchangeToMono(response->{
                if(response.statusCode().value()!=expected)return response.releaseBody().then(reactor.core.publisher.Mono.error(new Unavailable(response.statusCode().value())));
                return response.bodyToMono(byte[].class);
            }).takeUntilOther(stop.asMono()).timeout(timeout).block();
            if(closed.get() || bytes==null || bytes.length==0 || bytes.length>max)throw new Unavailable(502);
            var parsed=mapper.readTree(bytes);if(parsed==null || !parsed.isObject())throw new Unavailable(502);return parsed;
        }catch(Unavailable | Superseded failure){throw failure;}catch(Exception failure){throw new Unavailable(503);}finally{slots.release();}
    }
    private static String uri(RemoteReviewBinding b,String suffix,boolean request) {
        return "/api/v1/review-jobs"+suffix+"?artifactSha256="+b.artifactSha256()+"&contextSha256="+b.contextSha256()
                +"&policyVersion="+b.policyVersion()+"&reviewConfigSha256="+b.reviewConfigSha256()+(request?"&requestId="+b.requestId():"");
    }
    private static void requireJob(RemoteReviewBinding binding){if(binding.jobId()==null)throw new IllegalArgumentException("Remote job identity is required");}
    private static void fields(JsonNode node,String... fields) {
        if(!node.isObject() || node.size()!=fields.length)throw new Unavailable(502);
        for(String field:fields)if(!node.has(field))throw new Unavailable(502);
    }
    private static String text(JsonNode node,String key){if(!node.path(key).isTextual())throw new Unavailable(502);return node.get(key).textValue();}
    private static long number(JsonNode node,String key){if(!node.path(key).isIntegralNumber()||!node.get(key).canConvertToLong())throw new Unavailable(502);return node.get(key).longValue();}
    private static boolean digest(String value){return value.matches("[0-9a-f]{64}");}
    private static String hash(byte[] bytes){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    @Override @jakarta.annotation.PreDestroy public void close(){if(closed.compareAndSet(false,true)){stop.tryEmitEmpty();connections.dispose();}}
}
