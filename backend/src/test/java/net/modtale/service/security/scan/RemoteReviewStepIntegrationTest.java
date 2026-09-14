package net.modtale.service.security.scan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.*;
import com.sun.net.httpserver.*;
import net.modtale.config.properties.AppWardenProperties;
import net.modtale.model.project.*;
import net.modtale.service.storage.StorageService;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import java.net.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REVIEW_DB_TEST",matches="true")
class RemoteReviewStepIntegrationTest {
    MongoClient db;MongoTemplate mongo;String database;HttpServer server;RemoteReviewClient client;
    RemoteReviewPollStore polls;StorageService storage;RemoteReviewStep step;RemoteReviewBinding binding;
    ScanCompletionService completion;net.modtale.service.security.issue.SecurityIssueAnalysisService analysis;
    WardenClientService policy;
    byte[] bytes="inert original".getBytes();String project="abcdefabcdefabcdefabcdef",job=UUID.randomUUID().toString();
    RemoteReviewOrigin origin=new RemoteReviewOrigin("11111111-1111-1111-1111-111111111111","e".repeat(64));
    AtomicInteger identityGets=new AtomicInteger(),posts=new AtomicInteger(),gets=new AtomicInteger();ObjectMapper mapper=new ObjectMapper();
    interface Handler {void handle(HttpExchange e)throws Exception;}
    void route(Handler handler){server.createContext("/api/v1/review-jobs",e->{try{if(e.getRequestURI().getPath().endsWith("/identity")){identityGets.incrementAndGet();byte[] body=mapper.writeValueAsBytes(origin);e.sendResponseHeaders(200,body.length);e.getResponseBody().write(body);return;}
        assertEquals(origin.deploymentId(),e.getRequestHeaders().getFirst("X-Warden-Deployment-Id"));assertEquals(origin.callerScope(),e.getRequestHeaders().getFirst("X-Warden-Caller-Scope"));if(e.getRequestMethod().equals("POST")){posts.incrementAndGet();e.getRequestBody().readAllBytes();}else gets.incrementAndGet();handler.handle(e);}catch(Exception failure){throw new RuntimeException(failure);}finally{e.close();}});}
    void reply(HttpExchange e,int code,String state)throws Exception {
        var body=new LinkedHashMap<String,Object>();body.put("jobId",job);body.put("requestId",binding.requestId());
        body.put("binding",Map.of("artifactSha256",binding.artifactSha256(),"contextSha256",binding.contextSha256(),"policyVersion",binding.policyVersion(),"reviewConfigSha256",binding.reviewConfigSha256()));
        body.put("state",state);body.put("artifactRetained",!Set.of("UPLOADING","AWAITING_UPLOAD").contains(state));body.put("createdAt",1000L);body.put("expiresAt",2000L);body.put("workState",null);
        if(e.getRequestURI().getPath().endsWith("/result")) {
            body.remove("state");body.remove("artifactRetained");body.remove("createdAt");body.remove("expiresAt");body.remove("workState");
            body.put("completedAt",1500L);body.put("scan",cleanResult());
        }
        byte[] data=mapper.writeValueAsBytes(body);e.sendResponseHeaders(code,data.length);e.getResponseBody().write(data);
    }
    @BeforeEach void setup()throws Exception {
        setup(System.getenv().getOrDefault("WARDEN_REVIEW_DB_PORT","27030"),false);
    }
    void setup(String port,boolean replica) throws Exception {
        if(!(replica?Set.of("27031","27032"):Set.of("27029","27030")).contains(port))throw new IllegalArgumentException();
        db=MongoClients.create("mongodb://127.0.0.1:"+port+"/?directConnection=true&serverSelectionTimeoutMS=3000");database="warden_remote_step_"+UUID.randomUUID().toString().replace("-","");
        var factory=new org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory(db,database);
        var conversions=new net.modtale.config.db.MongoConfig().mongoCustomConversions(new net.modtale.config.db.MongoArtifactManifestStore(factory));
        var context=new org.springframework.data.mongodb.core.mapping.MongoMappingContext();context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());context.afterPropertiesSet();
        var converter=new org.springframework.data.mongodb.core.convert.MappingMongoConverter(new org.springframework.data.mongodb.core.convert.DefaultDbRefResolver(factory),context);
        converter.setCustomConversions(conversions);converter.afterPropertiesSet();mongo=new MongoTemplate(factory,converter);
        var version=new ProjectVersion();version.setId("v");version.setFileUrl("original.zip");version.setHash(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
        var scan=new ScanResult();scan.setStatus(ScanStatus.SCANNING);scan.setScanState("SCANNING");scan.setScanAttempt(1);scan.setScanRequestId(UUID.randomUUID().toString());version.setScanResult(scan);
        var root=new Project();root.setId(project);root.setVersions(List.of(version));mongo.insert(root);
        binding=new RemoteReviewBinding(project,"v",scan.getScanRequestId(),1,version.getFileUrl(),version.getHash(),ArtifactReviewContext.automaticallyReviewableFingerprint(version),"warden-3.0.0:"+"b".repeat(64),"c".repeat(64),null,false,origin);
        assertTrue(new RemoteReviewPersistence(mongo).bind(version,binding));polls=new RemoteReviewPollStore(mongo);storage=mock(StorageService.class);when(storage.downloadBounded(binding.filePath(),100*1024*1024)).thenReturn(bytes);
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());server.start();
        client=new RemoteReviewClient(new AppWardenProperties("http://127.0.0.1:"+server.getAddress().getPort(),"fixture-key",true,1,600),Duration.ofSeconds(2));
        var repo=mock(net.modtale.repository.project.ProjectRepository.class);when(repo.findById(project)).thenAnswer(i->Optional.ofNullable(mongo.findById(project,Project.class)));
        var projects=mock(net.modtale.service.project.query.ProjectService.class);analysis=mock(net.modtale.service.security.issue.SecurityIssueAnalysisService.class);
        doAnswer(i->{new net.modtale.service.security.issue.SecurityIssueApprovalService(new net.modtale.service.security.issue.SecurityIssueClassificationService(
                new net.modtale.config.properties.AppSecurityProperties("fixture",60,120,2,4,15,20,25,2))).markIssuesAcceptedForApprovedVersion(i.getArgument(0));return null;})
                .when(analysis).markIssuesAcceptedForApprovedVersion(any());
        when(analysis.annotateAgainstBaselines(any(),any())).thenReturn(new net.modtale.service.security.issue.SecurityIssueAnalysisService.ClassificationStats(0,0,0,false));
        policy=mock(WardenClientService.class);when(policy.currentPolicyVersion()).thenReturn(binding.policyVersion());
        completion=new ScanCompletionService(repo,projects,mock(net.modtale.service.communication.ProjectNotificationService.class),mock(net.modtale.service.communication.WebhookService.class),analysis,
                new ScanRoutingService(new net.modtale.config.properties.AppSecurityProperties("fixture",60,120,2,4,15,20,25,2)),
                new ScanPersistenceService(mongo,repo,projects),new net.modtale.service.project.access.ProjectVersionAccessService(null),policy);
        completion=spy(completion);
        doAnswer(invocation->{try{return invocation.callRealMethod();}catch(RuntimeException failure){throw new AssertionError("Completion failed",failure);}})
                .when(completion).handleRemoteCompletedScan(any(),any());
        step=new RemoteReviewStep(polls,client,storage,completion);
    }
    @AfterEach void cleanup(){if(client!=null)client.close();if(server!=null)server.stop(0);if(db!=null){db.getDatabase(database).drop();db.close();}}
    ScanResult cleanResult() {
        var result=ScanEvidenceFixtures.complete(true);var old=result.getSecurityEvidence();
        result.setSecurityEvidence(new ScanResult.SecurityEvidence(binding.policyVersion(),binding.artifactSha256(),old.contentSha256(),true,true,old.reviewState(),old.entryHashes()));
        result.setReviewedContextSha256(binding.contextSha256());result.setScanRequestId(binding.requestId());return result;
    }
    ScanResult saved(){return mongo.findById(project,Project.class).getVersions().getFirst().getScanResult();}
    void change(String field,Object value){mongo.updateFirst(Query.query(Criteria.where("_id").is(project)),new Update().set("versions.0."+field,value),Project.class);}
    void ready(){change("scanResult.remotePoll.nextPollAt",new Date(0));}
    @Test void submitThenPersistAndPollExistingJobWithoutRedownload() {
        var found=new AtomicBoolean();route(e->{if(e.getRequestMethod().equals("POST")){found.set(true);reply(e,202,"QUEUED");}else reply(e,found.get()?200:404,found.get()?"COMPLETED":"AWAITING_UPLOAD");});
        assertEquals("RECORDED",step.advance(binding).state());assertEquals(job,saved().getRemoteReview().jobId());assertEquals("QUEUED",saved().getRemoteStatus().state());assertNull(saved().getRemotePoll().token());
        assertEquals("NO_WORK",step.advance(saved().getRemoteReview()).state());ready();
        var outcome=new RemoteReviewStep(new RemoteReviewPollStore(mongo),client,storage,completion).advance(saved().getRemoteReview());assertEquals("COMPLETED",outcome.remoteState(),outcome.toString());
        assertEquals("APPLIED",outcome.state());assertEquals("COMPLETED",saved().getScanState());assertTrue(saved().isArtifactVerified());
        assertEquals(ProjectVersion.ReviewStatus.SCHEDULED,mongo.findById(project,Project.class).getVersions().getFirst().getReviewStatus());assertEquals(binding.contextSha256(),saved().getReviewedContextSha256());assertEquals(1,posts.get());verify(storage,times(1)).downloadBounded(anyString(),anyInt());
    }
    RemoteReviewPollStore.Claim attached(long lease) {
        var claim=polls.claim(binding,lease);assertNotNull(claim);var attached=polls.attachJob(claim,job);assertNotNull(attached);return attached;
    }
    @Test void contextChangeDuringResultReadCannotScheduleVersion() {
        route(e->{if(e.getRequestURI().getPath().endsWith("/result"))change("manifestVersion","changed");reply(e,200,"COMPLETED");});
        assertEquals("SUPERSEDED",step.advance(binding).state());assertEquals("REMOTE_REVIEW",saved().getScanState());
        assertEquals(ProjectVersion.ReviewStatus.PENDING,mongo.findById(project,Project.class).getVersions().getFirst().getReviewStatus());
    }
    @Test void contextChangeAfterCompletionSnapshotStillFailsPublicationWrite() {
        var claim=attached(30000);when(policy.currentPolicyVersion()).thenAnswer(i->{change("manifestVersion","changed at commit");return binding.policyVersion();});
        assertFalse(completion.handleRemoteCompletedScan(claim,cleanResult()));assertEquals("REMOTE_REVIEW",saved().getScanState());
    }
    @Test void databaseLeaseExpiryDuringPolicyCheckRejectsResultWithoutChangingStoredPoll()throws Exception {
        var claim=attached(1000);when(policy.currentPolicyVersion()).thenAnswer(i->{Thread.sleep(1100);return binding.policyVersion();});
        assertFalse(completion.handleRemoteCompletedScan(claim,cleanResult()));assertEquals(claim.token(),saved().getRemotePoll().token());assertEquals("REMOTE_REVIEW",saved().getScanState());
    }
    @Test void wrongOriginalResultContextAndRequestAreRejected() {
        var claim=attached(30000);var result=cleanResult();result.setReviewedContextSha256("d".repeat(64));assertFalse(completion.handleRemoteCompletedScan(claim,result));
        result=cleanResult();result.setScanRequestId(UUID.randomUUID().toString());assertFalse(completion.handleRemoteCompletedScan(claim,result));
        assertEquals("REMOTE_REVIEW",saved().getScanState());
    }
    @Test void missingCurrentPolicyRequiresReviewAndCannotSchedule() {
        var claim=attached(30000);when(policy.currentPolicyVersion()).thenReturn(null);
        assertTrue(completion.handleRemoteCompletedScan(claim,cleanResult()));assertEquals("REVIEW",saved().getVerdict());
        assertEquals(ProjectVersion.ReviewStatus.PENDING,mongo.findById(project,Project.class).getVersions().getFirst().getReviewStatus());
        assertFalse(completion.handleRemoteCompletedScan(claim,cleanResult()));
    }
    @Test void terminalRemoteFailureDoesNotCreateAnotherLocalScanAttempt() {
        var claim=attached(30000);var result=cleanResult();var e=result.getSecurityEvidence();
        result.setSecurityEvidence(new ScanResult.SecurityEvidence(e.policyVersion(),e.artifactSha256(),e.contentSha256(),true,false,"RATE_LIMITED",e.entryHashes()));
        result.setVerdict("REVIEW");result.setStatus(ScanStatus.SUSPICIOUS);
        assertTrue(completion.handleRemoteCompletedScan(claim,result));assertEquals("COMPLETED",saved().getScanState());
        assertEquals(binding.requestId(),saved().getScanRequestId());assertEquals(job,saved().getRemoteReview().jobId());
        assertEquals(ProjectVersion.ReviewStatus.PENDING,mongo.findById(project,Project.class).getVersions().getFirst().getReviewStatus());
    }
    @Test void incompleteRemoteEvidenceRemainsManualReview() {
        var claim=attached(30000);var result=cleanResult();var e=result.getSecurityEvidence();
        result.setSecurityEvidence(new ScanResult.SecurityEvidence(e.policyVersion(),e.artifactSha256(),e.contentSha256(),false,false,"INCOMPLETE",e.entryHashes()));result.setArtifactVerified(false);result.setVerdict("REVIEW");result.setStatus(ScanStatus.SUSPICIOUS);
        assertTrue(completion.handleRemoteCompletedScan(claim,result));assertEquals("REVIEW",saved().getVerdict());assertFalse(ArtifactClearancePolicy.cleared(saved()));
        assertEquals(ProjectVersion.ReviewStatus.PENDING,mongo.findById(project,Project.class).getVersions().getFirst().getReviewStatus());
    }
    void queuedAgain() {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(project)),new Update().unset("versions.0.scanResult.remoteReview").set("versions.0.scanResult.scanState","QUEUED"),Project.class);
    }
    void configuration(HttpExchange e)throws Exception {
        byte[] data=mapper.writeValueAsBytes(Map.of("policyVersion",binding.policyVersion(),"reviewConfigSha256",binding.reviewConfigSha256()));
        e.sendResponseHeaders(200,data.length);e.getResponseBody().write(data);
    }
    RemoteReviewBootstrap bootstrap(RemoteReviewPersistence persistence) {return new RemoteReviewBootstrap(persistence,client,step);}
    @Test void bootstrapBindsQueuedRequestBeforeUploadAndReusesRetainedConfiguration() {
        queuedAgain();var configurations=new AtomicInteger();
        route(e->{if(e.getRequestURI().getPath().endsWith("configuration")){configurations.incrementAndGet();configuration(e);}
            else if(e.getRequestMethod().equals("POST"))reply(e,202,"QUEUED");else reply(e,404,"AWAITING_UPLOAD");});
        var candidate=new RemoteReviewDiscovery(mongo).page(null,16).candidates().getFirst();
        var boot=bootstrap(new RemoteReviewPersistence(mongo));assertEquals("RECORDED",boot.advance(candidate.projectId(),candidate.versionId(),candidate.attempt(),candidate.requestId()).state());
        assertEquals(job,saved().getRemoteReview().jobId());assertEquals("READY",boot.prepare(project,"v",1,binding.requestId()).state());
        assertEquals(origin,saved().getRemoteReview().origin());assertEquals(1,identityGets.get());assertEquals(1,configurations.get());assertEquals(1,posts.get());
    }
    @Test void bootstrapRecoversCommittedBindingAcknowledgementLoss() {
        queuedAgain();route(this::configuration);var persistence=spy(new RemoteReviewPersistence(mongo));
        doAnswer(i->{assertTrue((Boolean)i.callRealMethod());throw new IllegalStateException("Lost acknowledgement after committed binding");}).when(persistence).bind(any(),any());
        var prepared=bootstrap(persistence).prepare(project,"v",1,binding.requestId());assertEquals("READY",prepared.state());assertEquals(binding,prepared.binding());
        assertEquals(prepared.binding(),saved().getRemoteReview());assertEquals(0,posts.get());verifyNoInteractions(storage);
    }
    @Test void changedContextDuringConfigurationCannotCreateBindingOrUpload() {
        queuedAgain();route(e->{change("manifestVersion","changed");configuration(e);});
        assertEquals("NO_WORK",bootstrap(new RemoteReviewPersistence(mongo)).advance(project,"v",1,binding.requestId()).state());
        assertNull(saved().getRemoteReview());assertEquals(0,posts.get());verifyNoInteractions(storage);
    }
    @Test void missingBindingStopsDiscoveryWithoutRemoteWork() {
        change("scanResult.remoteReview",null);
        var boot=bootstrap(new RemoteReviewPersistence(mongo));
        assertEquals("UNAVAILABLE",boot.advance(project,"v",1,binding.requestId()).state());
        assertEquals(ScanStatus.FAILED,saved().getStatus());assertEquals("REMOTE_BINDING_MISSING",saved().getScanState());
        assertEquals(binding.requestId(),saved().getScanRequestId());assertEquals(1,saved().getScanAttempt());
        assertFalse(ArtifactClearancePolicy.cleared(saved()));
        assertTrue(new RemoteReviewDiscovery(mongo).page(null,16).candidates().isEmpty());
        assertEquals("NO_WORK",boot.advance(project,"v",1,binding.requestId()).state());
        assertEquals(0,gets.get());assertEquals(0,posts.get());verifyNoInteractions(storage);
    }
    @Test void mismatchedAttachedBindingPreservesBlockAndRejectsOldCompletion() {
        var claim=attached(30000);change("manifestVersion","changed");change("scanResult.verdict","BLOCK");
        change("scanResult.riskScore",87);
        assertEquals("UNAVAILABLE",bootstrap(new RemoteReviewPersistence(mongo)).advance(project,"v",1,binding.requestId()).state());
        assertEquals("REMOTE_BINDING_MISMATCH",saved().getScanState());assertEquals(ScanStatus.FAILED,saved().getStatus());
        assertEquals("BLOCK",saved().getVerdict());assertEquals(87,saved().getRiskScore());
        assertEquals(job,saved().getRemoteReview().jobId());assertNull(saved().getRemotePoll());
        assertFalse(polls.isCurrent(claim));assertFalse(completion.handleRemoteCompletedScan(claim,cleanResult()));
        assertEquals(ProjectVersion.ReviewStatus.PENDING,mongo.findById(project,Project.class).getVersions().getFirst().getReviewStatus());
        assertEquals(0,gets.get());assertEquals(0,posts.get());verifyNoInteractions(storage);
    }
    @Test void validOrSupersededBindingCannotBeFailed() {
        var persistence=new RemoteReviewPersistence(mongo);
        assertNull(persistence.finishBrokenBinding(project,"v",1,binding.requestId()));
        change("manifestVersion","changed");
        assertNull(persistence.finishBrokenBinding(project,"v",1,UUID.randomUUID().toString()));
        assertEquals("REMOTE_REVIEW",saved().getScanState());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"repair","request","metadata","duplicate"})
    void concurrentChangeBetweenSnapshotAndFailureWriteIsNotOverwritten(String mutation) {
        change("manifestVersion","changed");var intercepted=new AtomicBoolean();
        var settings=com.mongodb.MongoClientSettings.builder().applyConnectionString(new com.mongodb.ConnectionString(
                "mongodb://127.0.0.1:"+System.getenv().getOrDefault("WARDEN_REVIEW_DB_PORT","27030")+"/?serverSelectionTimeoutMS=3000"))
                .addCommandListener(new com.mongodb.event.CommandListener() {
                    @Override public void commandStarted(com.mongodb.event.CommandStartedEvent event) {
                        if(!event.getCommandName().equals("update") || !intercepted.compareAndSet(false,true))return;
                        switch(mutation) {
                            case "repair" -> change("manifestVersion",null);
                            case "request" -> change("scanResult.scanRequestId",UUID.randomUUID().toString());
                            case "metadata" -> change("unrecognizedMetadata","preserve this concurrent field");
                            case "duplicate" -> {
                                var collection=mongo.getCollection("projects");var root=collection.find().first();
                                collection.updateOne(new Document("_id",root.get("_id")),new Document("$push",new Document("versions",root.getList("versions",Document.class).getFirst())));
                            }
                        }
                    }
                }).build();
        try(var raceClient=MongoClients.create(settings)) {
            assertNull(new RemoteReviewPersistence(new MongoTemplate(raceClient,database)).finishBrokenBinding(project,"v",1,binding.requestId()));
        }
        assertTrue(intercepted.get());assertEquals("REMOTE_REVIEW",saved().getScanState());assertEquals(ScanStatus.SCANNING,saved().getStatus());
        if(mutation.equals("repair"))assertNotNull(new RemoteReviewPersistence(mongo).retained(project,"v",1,binding.requestId()));
        if(mutation.equals("metadata"))assertEquals("preserve this concurrent field",mongo.getCollection("projects").find().first().getList("versions",Document.class).getFirst().getString("unrecognizedMetadata"));
        if(mutation.equals("duplicate"))assertEquals(2,mongo.findById(project,Project.class).getVersions().size());
    }
    @Test void retainedContextMismatchNeverFetchesAnotherConfiguration() {
        change("manifestVersion","changed");route(this::configuration);
        assertEquals("UNAVAILABLE",bootstrap(new RemoteReviewPersistence(mongo)).prepare(project,"v",1,binding.requestId()).state());assertEquals(0,gets.get());
    }
    @Test void unsupportedContextAndMissingRequestRemainExplicitWithoutHttp() {
        queuedAgain();change("overrideFileUrl","unreviewed.zip");route(this::configuration);var boot=bootstrap(new RemoteReviewPersistence(mongo));
        assertEquals("UNAVAILABLE",boot.prepare(project,"v",1,binding.requestId()).state());assertEquals("NO_WORK",boot.prepare(project,"v",1,null).state());assertEquals(0,gets.get());
    }
    @Test void unsupportedSetupPreservesRequestAndFindingsAndStopsAutomaticPolling() {
        queuedAgain();change("overrideFileUrl","unreviewed.zip");change("scanResult.manualRescan",true);
        change("scanResult.verdict","BLOCK");change("scanResult.riskScore",42);
        change("scanResult.issues",List.of(new org.bson.Document("type","RetainedFinding")));
        route(this::configuration);var boot=bootstrap(new RemoteReviewPersistence(mongo));
        assertEquals("UNAVAILABLE",boot.advance(project,"v",1,binding.requestId()).state());
        var result=saved();assertEquals(ScanStatus.FAILED,result.getStatus());assertEquals("REMOTE_UNSUPPORTED_CONTEXT",result.getScanState());
        assertEquals(binding.requestId(),result.getScanRequestId());assertEquals(1,result.getScanAttempt());assertTrue(result.isManualRescan());
        assertEquals("BLOCK",result.getVerdict());assertEquals(42,result.getRiskScore());assertEquals("RetainedFinding",result.getIssues().getFirst().getType());
        assertFalse(ArtifactClearancePolicy.cleared(result));assertNull(result.getRemoteReview());
        assertTrue(new RemoteReviewDiscovery(mongo).page(null,16).candidates().isEmpty());
        assertEquals("NO_WORK",bootstrap(new RemoteReviewPersistence(mongo)).advance(project,"v",1,binding.requestId()).state());
        mongo.updateFirst(Query.query(Criteria.where("_id").is(project)),new Update().set("status",ProjectStatus.PUBLISHED),Project.class);
        var failures=new net.modtale.service.admin.review.ModerationQueuePageReader(mongo).page(null,25,net.modtale.service.admin.review.ModerationQueuePageReader.Filter.OPERATIONS);
        assertEquals(1,failures.items().size());assertEquals("REMOTE_UNSUPPORTED_CONTEXT",failures.items().getFirst().pendingVersion().scan().scanState());
        assertEquals(0,gets.get());assertEquals(0,posts.get());verifyNoInteractions(storage);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"context","request","verdict","binding","duplicate"})
    void unsupportedSetupRejectsConcurrentChange(String mutation) {
        queuedAgain();change("overrideFileUrl","unreviewed.zip");var persistence=new RemoteReviewPersistence(mongo);
        var observed=persistence.current(project,"v",1,binding.requestId());assertNotNull(observed);
        switch(mutation) {
            case "context" -> change("overrideFileUrl",null);
            case "request" -> change("scanResult.scanRequestId",UUID.randomUUID().toString());
            case "verdict" -> change("scanResult.verdict","BLOCK");
            case "binding" -> change("scanResult.remoteReview",binding);
            case "duplicate" -> mongo.updateFirst(Query.query(Criteria.where("_id").is(project)),new Update().push("versions",observed),Project.class);
        }
        assertFalse(persistence.finishUnsupportedContext(project,observed));assertEquals(ScanStatus.SCANNING,saved().getStatus());
    }
    @Test void supportedOrAlreadyBoundContextCannotBeFinishedAsUnsupported() {
        var persistence=new RemoteReviewPersistence(mongo);
        assertFalse(persistence.finishUnsupportedContext(project,persistence.current(project,"v",1,binding.requestId())));
        queuedAgain();assertFalse(persistence.finishUnsupportedContext(project,persistence.current(project,"v",1,binding.requestId())));
        assertEquals("QUEUED",saved().getScanState());
    }
    @Test void manualRescanModeSurvivesBootstrapAttachmentAndCompletesImmediately() {
        queuedAgain();change("scanResult.manualRescan",true);
        route(e->{if(e.getRequestURI().getPath().endsWith("configuration"))configuration(e);else reply(e,200,"COMPLETED");});
        var outcome=bootstrap(new RemoteReviewPersistence(mongo)).advance(project,"v",1,binding.requestId());assertEquals("APPLIED",outcome.state());
        var version=mongo.findById(project,Project.class).getVersions().getFirst();assertEquals(ProjectVersion.ReviewStatus.APPROVED,version.getReviewStatus());
        assertNotNull(version.getApprovedSecurityEvidence());assertNull(version.getScanResult());assertEquals(0,posts.get());
    }
    @Test void requestModeChangeDuringConfigurationRejectsStaleBinding() {
        queuedAgain();route(e->{change("scanResult.manualRescan",true);configuration(e);});
        assertEquals("NO_WORK",bootstrap(new RemoteReviewPersistence(mongo)).prepare(project,"v",1,binding.requestId()).state());assertNull(saved().getRemoteReview());
    }
    @Test void schedulerDiscoversBootstrapsUploadsAndSchedulesCompletedReview()throws Exception {
        queuedAgain();route(e->{if(e.getRequestURI().getPath().endsWith("configuration"))configuration(e);
            else if(e.getRequestMethod().equals("POST"))reply(e,202,"COMPLETED");else reply(e,e.getRequestURI().getPath().endsWith("/result")?200:404,"COMPLETED");});
        when(policy.remoteJobsEnabled()).thenReturn(true);
        var recovery=mock(ScanRecoveryService.class);var legacyPersistence=mock(ScanPersistenceService.class);
        var execution=new ScanExecutionService(policy,storage,r->{throw new AssertionError("Legacy dispatch");},legacyPersistence,completion,recovery);
        execution.enqueueBackgroundScan(project,"v",binding.filePath(),"original.zip",false,1,binding.requestId());
        execution.recoverStaleScanningVersions();
        assertEquals("QUEUED",saved().getScanState());assertEquals(binding.requestId(),saved().getScanRequestId());
        verifyNoInteractions(legacyPersistence,recovery);verify(storage,never()).download(any());
        try(var scheduler=new RemoteReviewScheduler(new RemoteReviewDiscovery(mongo),bootstrap(new RemoteReviewPersistence(mongo)),new RemoteReviewScheduler.Settings(2,4,100,2000))) {
            scheduler.start();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            while(mongo.findById(project,Project.class).getVersions().getFirst().getReviewStatus()!=ProjectVersion.ReviewStatus.SCHEDULED && System.nanoTime()<deadline)Thread.sleep(20);
            assertEquals(ProjectVersion.ReviewStatus.SCHEDULED,mongo.findById(project,Project.class).getVersions().getFirst().getReviewStatus());
            scheduler.stop();assertEquals("STOPPED",scheduler.status().state());assertEquals(1,posts.get());assertTrue(ArtifactClearancePolicy.cleared(saved()));
        }
    }
    @Test void schedulerShutdownDuringUncooperativeDownloadPreventsLateUpload()throws Exception {
        queuedAgain();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        route(e->{if(e.getRequestURI().getPath().endsWith("configuration"))configuration(e);else reply(e,404,"AWAITING_UPLOAD");});
        when(storage.downloadBounded(anyString(),anyInt())).thenAnswer(i->{entered.countDown();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(release.getCount()>0&&System.nanoTime()<deadline)try{release.await(50,TimeUnit.MILLISECONDS);}catch(InterruptedException ignored){}return bytes;});
        var scheduler=new RemoteReviewScheduler(new RemoteReviewDiscovery(mongo),bootstrap(new RemoteReviewPersistence(mongo)),new RemoteReviewScheduler.Settings(1,4,100,100));
        scheduler.start();try{assertTrue(entered.await(3,TimeUnit.SECONDS));scheduler.stop();assertEquals("DRAIN_TIMEOUT",scheduler.status().state());assertEquals(0,posts.get());}finally{release.countDown();}
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(scheduler.isRunning()&&System.nanoTime()<deadline)Thread.sleep(20);
        scheduler.close();assertEquals(0,posts.get());assertEquals("REMOTE_REVIEW",saved().getScanState());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"HELD","CANCELLED","EXPIRED"})
    void terminalRemoteStatusStopsPollingWithoutApprovalOrSyntheticFindings(String state) {
        route(e->reply(e,200,state));
        assertEquals("UNAVAILABLE",step.advance(binding).state());
        var result=saved();assertEquals(ScanStatus.FAILED,result.getStatus());assertEquals("REMOTE_"+state,result.getScanState());
        assertEquals("REVIEW",result.getVerdict());assertEquals(binding.requestId(),result.getScanRequestId());
        assertEquals(job,result.getRemoteReview().jobId());assertEquals(state,result.getRemoteStatus().state());
        assertNull(result.getRemotePoll());assertNull(result.getSecurityEvidence());assertFalse(ArtifactClearancePolicy.cleared(result));
        assertEquals(0,result.getRiskScore());assertTrue(result.getIssues().isEmpty());assertTrue(result.getScanTimestamp()>0);
        assertEquals(ProjectVersion.ReviewStatus.PENDING,mongo.findById(project,Project.class).getVersions().getFirst().getReviewStatus());
        assertTrue(new RemoteReviewDiscovery(mongo).page(null,16).candidates().isEmpty());
        assertEquals("NO_WORK",new RemoteReviewStep(new RemoteReviewPollStore(mongo),client,storage,completion).advance(result.getRemoteReview()).state());
        assertEquals(1,gets.get());assertEquals(0,posts.get());verifyNoInteractions(storage);verify(completion,never()).handleRemoteCompletedScan(any(),any());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"HELD","CANCELLED","EXPIRED"})
    void terminalServiceOutcomePreservesSecurityBlockAndFindings(String state) {
        change("scanResult.verdict","BLOCK");change("scanResult.riskScore",87);
        change("scanResult.issues",List.of(new Document("type","RetainedFinding")));
        route(e->reply(e,200,state));assertEquals("UNAVAILABLE",step.advance(binding).state());
        assertEquals("BLOCK",saved().getVerdict());assertEquals(87,saved().getRiskScore());
        assertEquals("RetainedFinding",saved().getIssues().getFirst().getType());assertEquals(ScanStatus.FAILED,saved().getStatus());
        assertEquals("REMOTE_"+state,saved().getScanState());assertFalse(ArtifactClearancePolicy.cleared(saved()));
        assertEquals(binding.requestId(),saved().getScanRequestId());assertEquals(job,saved().getRemoteReview().jobId());
        mongo.updateFirst(Query.query(Criteria.where("_id").is(project)),new Update().set("status",ProjectStatus.PUBLISHED),Project.class);
        var reader=new net.modtale.service.admin.review.ModerationQueuePageReader(mongo);
        for(var filter:List.of(net.modtale.service.admin.review.ModerationQueuePageReader.Filter.SECURITY,net.modtale.service.admin.review.ModerationQueuePageReader.Filter.OPERATIONS)) {
            var rows=reader.page(null,25,filter).items();assertEquals(1,rows.size());assertEquals("BLOCK",rows.getFirst().pendingVersion().scan().verdict());
        }
        assertEquals(1,gets.get());assertEquals(0,posts.get());verifyNoInteractions(storage);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"verdict","context","lease"})
    void terminalFailureWriteRejectsChangesAfterLiveSnapshot(String mutation) {
        var claim=attached(30000);var intercepted=new AtomicBoolean();
        var settings=com.mongodb.MongoClientSettings.builder().applyConnectionString(new com.mongodb.ConnectionString(
                "mongodb://127.0.0.1:"+System.getenv().getOrDefault("WARDEN_REVIEW_DB_PORT","27030")+"/?serverSelectionTimeoutMS=3000"))
                .addCommandListener(new com.mongodb.event.CommandListener() {
                    @Override public void commandStarted(com.mongodb.event.CommandStartedEvent event) {
                        if(!event.getCommandName().equals("update") || !intercepted.compareAndSet(false,true))return;
                        switch(mutation) {
                            case "verdict" -> change("scanResult.verdict","BLOCK");
                            case "context" -> change("manifestVersion","changed during write");
                            case "lease" -> change("scanResult.remotePoll.token",UUID.randomUUID().toString());
                        }
                    }
                }).build();
        try(var raceClient=MongoClients.create(settings)) {
            assertFalse(new RemoteReviewPollStore(new MongoTemplate(raceClient,database)).finishUnavailable(claim,
                    new RemoteReviewClient.Status(job,"HELD",true,1,2,"HELD")));
        }
        assertTrue(intercepted.get());assertEquals("REMOTE_REVIEW",saved().getScanState());assertEquals(ScanStatus.SCANNING,saved().getStatus());
        if(mutation.equals("verdict")) {
            assertEquals("BLOCK",saved().getVerdict());
            assertTrue(polls.finishUnavailable(claim,new RemoteReviewClient.Status(job,"HELD",true,1,2,"HELD")));
            assertEquals("BLOCK",saved().getVerdict());
        }
    }
    @Test void databaseQueueKeepsFailedVersionVisibleBesideScanningSibling() {
        route(e->reply(e,200,"HELD"));assertEquals("UNAVAILABLE",step.advance(binding).state());
        var sibling=new ProjectVersion();sibling.setId("scanning-sibling");sibling.setReviewStatus(ProjectVersion.ReviewStatus.PENDING);
        var scan=new ScanResult();scan.setStatus(ScanStatus.SCANNING);sibling.setScanResult(scan);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(project)),new Update().set("status",ProjectStatus.PUBLISHED).push("versions",sibling),Project.class);
        var rows=new net.modtale.service.admin.review.ModerationQueuePageReader(mongo).page(null,25).items();assertEquals(1,rows.size());assertEquals("v",rows.getFirst().pendingVersion().id());
        assertEquals("REMOTE_HELD",rows.getFirst().pendingVersion().scan().scanState());
        assertEquals(ScanStatus.FAILED,rows.getFirst().pendingVersion().scan().status());
    }
    @Test void expiredTerminalClaimCannotOverwriteOrConsumeAnotherAttempt() {
        var claim=attached(1000);
        change("scanResult.remotePoll.leaseUntil",new Date(0));
        var status=new RemoteReviewClient.Status(job,"HELD",true,1,2,"HELD");
        assertFalse(polls.finishUnavailable(claim,status));assertEquals("REMOTE_REVIEW",saved().getScanState());
        var replacement=polls.claim(claim.binding(),30000);assertNotNull(replacement);
        assertFalse(polls.finishUnavailable(claim,status));assertTrue(polls.isCurrent(replacement));
        assertTrue(polls.finishUnavailable(replacement,status));assertFalse(polls.finishUnavailable(replacement,status));
    }
    @Test void terminalOutcomeRequiresExactJobAndUnchangedContext() {
        var claim=attached(30000);
        assertFalse(polls.finishUnavailable(claim,new RemoteReviewClient.Status(UUID.randomUUID().toString(),"HELD",true,1,2,"HELD")));
        assertFalse(polls.finishUnavailable(claim,new RemoteReviewClient.Status(job,"COMPLETED",true,1,2,"COMPLETED")));
        assertFalse(polls.finishUnavailable(claim,new RemoteReviewClient.Status(job,"HELD",false,1,2,"HELD")));
        assertFalse(polls.recordStatusAndRelease(claim,new RemoteReviewClient.Status(job,"HELD",true,1,2,"HELD"),10000));
        change("manifestVersion","changed");
        assertFalse(polls.finishUnavailable(claim,new RemoteReviewClient.Status(job,"HELD",true,1,2,"HELD")));
        assertEquals("REMOTE_REVIEW",saved().getScanState());
    }
    @Test void lostPostResponseRecoversSameRequestAndRecordsJobWithoutAnotherUpload() {
        var accepted=new AtomicBoolean();route(e->{if(e.getRequestMethod().equals("POST")){accepted.set(true);return;}reply(e,accepted.get()?200:404,accepted.get()?"QUEUED":"AWAITING_UPLOAD");});
        assertEquals("RETRY",step.advance(binding).state());assertNull(saved().getRemoteReview().jobId());ready();
        assertEquals("RECORDED",step.advance(saved().getRemoteReview()).state());assertEquals(job,saved().getRemoteReview().jobId());assertEquals(1,posts.get());verify(storage,times(1)).downloadBounded(anyString(),anyInt());
    }
    @Test void contextChangeDuringLookupPreventsDownloadAndPost() {
        route(e->{change("manifestVersion","changed");reply(e,404,"AWAITING_UPLOAD");});
        assertEquals("SUPERSEDED",step.advance(binding).state());verifyNoInteractions(storage);assertEquals(0,posts.get());assertNull(saved().getRemoteStatus());
    }
    @Test void leaseExpiryDuringDownloadPreventsPost() {
        route(e->reply(e,404,"AWAITING_UPLOAD"));when(storage.downloadBounded(anyString(),anyInt())).thenAnswer(i->{change("scanResult.remotePoll.leaseUntil",new Date(0));return bytes;});
        assertEquals("SUPERSEDED",step.advance(binding).state());assertEquals(0,posts.get());assertNull(saved().getRemoteReview().jobId());
    }
    @Test void changedContextDuringPostCannotAttachRemoteJob() {
        route(e->{if(e.getRequestMethod().equals("POST")){change("manifestVersion","changed");reply(e,202,"QUEUED");}else reply(e,404,"AWAITING_UPLOAD");});
        assertEquals("SUPERSEDED",step.advance(binding).state());assertEquals(1,posts.get());assertNull(saved().getRemoteReview().jobId());assertNull(saved().getRemoteStatus());
    }
    @Test void knownUploadingJobCanResumeAbandonedUploadWithSameIdentity() {
        var state=new AtomicReference<>("UPLOADING");route(e->{if(e.getRequestMethod().equals("POST"))reply(e,202,"QUEUED");else reply(e,200,state.get());});
        assertEquals("UPLOADING",step.advance(binding).remoteState());verifyNoInteractions(storage);ready();state.set("AWAITING_UPLOAD");
        assertEquals("QUEUED",step.advance(saved().getRemoteReview()).remoteState());assertEquals(job,saved().getRemoteReview().jobId());assertEquals(1,posts.get());
    }
    @Test void competingStepCannotDispatchUnderExistingLiveClaim() {
        assertNotNull(polls.claim(binding,120000));route(e->reply(e,404,"AWAITING_UPLOAD"));assertEquals("NO_WORK",step.advance(binding).state());assertEquals(0,gets.get());verifyNoInteractions(storage);
    }
    @Test void legacyOriginRoutesOnceToOperationsWithoutNetworkOrBackfill() {
        change("scanResult.remoteReview.origin",null);change("scanResult.verdict","BLOCK");var old=saved().getRemoteReview();
        route(e->reply(e,200,"COMPLETED"));var boot=bootstrap(new RemoteReviewPersistence(mongo));
        assertEquals("UNAVAILABLE",boot.advance(project,"v",1,binding.requestId()).state());assertEquals("REMOTE_ORIGIN_UNVERIFIED",saved().getScanState());assertEquals(ScanStatus.FAILED,saved().getStatus());
        assertEquals("BLOCK",saved().getVerdict());assertEquals(old,saved().getRemoteReview());assertNull(saved().getRemoteReview().origin());assertFalse(saved().isArtifactVerified());
        assertEquals("NO_WORK",boot.advance(project,"v",1,binding.requestId()).state());assertEquals(0,gets.get());assertEquals(0,posts.get());assertEquals(0,identityGets.get());verifyNoInteractions(storage);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void legacyWriterWinningInitialBindingRoutesImmediatelyEvenAfterLostAcknowledgement(boolean lostAcknowledgement) {
        queuedAgain();change("scanResult.verdict","BLOCK");route(this::configuration);
        var persistence=spy(new RemoteReviewPersistence(mongo));
        var legacy=new RemoteReviewBinding(binding.projectId(),binding.versionId(),binding.requestId(),binding.attempt(),binding.filePath(),
                binding.artifactSha256(),binding.contextSha256(),binding.policyVersion(),binding.reviewConfigSha256(),job,binding.manualRescan());
        doAnswer(i->{change("scanResult.remoteReview",legacy);change("scanResult.scanState","REMOTE_REVIEW");
            if(lostAcknowledgement)throw new IllegalStateException("Unknown binding acknowledgement");return i.callRealMethod();}).when(persistence).bind(any(),any());
        var boot=bootstrap(persistence);var prepared=boot.prepare(project,"v",1,binding.requestId());
        assertEquals("UNAVAILABLE",prepared.state());assertNull(prepared.binding());
        assertEquals(ScanStatus.FAILED,saved().getStatus());assertEquals("REMOTE_ORIGIN_UNVERIFIED",saved().getScanState());
        assertEquals(legacy,saved().getRemoteReview());assertEquals("BLOCK",saved().getVerdict());assertFalse(saved().isArtifactVerified());
        assertEquals("NO_WORK",boot.advance(project,"v",1,binding.requestId()).state());
        assertEquals(1,identityGets.get());assertEquals(1,gets.get());assertEquals(0,posts.get());verifyNoInteractions(storage);
        assertTrue(new RemoteReviewDiscovery(mongo).page(null,16).candidates().isEmpty());
    }
    @Test void remoteContextConflictPreservesBindingAndBlockWithoutPollingAgain() {
        change("scanResult.verdict","BLOCK");route(e->reply(e,409,"QUEUED"));var before=saved().getRemoteReview();
        assertEquals("UNAVAILABLE",step.advance(binding).state());assertEquals("REMOTE_CONTEXT_CONFLICT",saved().getScanState());assertEquals(before,saved().getRemoteReview());assertEquals("BLOCK",saved().getVerdict());
        assertEquals("NO_WORK",step.advance(binding).state());assertEquals(1,gets.get());assertEquals(0,posts.get());verifyNoInteractions(storage);
    }
    @Test void aConflictArrivingAfterLocalContextChangeCannotOverwriteNewState() {
        route(e->{change("manifestVersion","concurrent");reply(e,409,"QUEUED");});assertEquals("SUPERSEDED",step.advance(binding).state());assertEquals("REMOTE_REVIEW",saved().getScanState());
    }
    @Test void changingOriginAfterCompletionSnapshotRejectsTheFinalWrite() {
        var claim=attached(30000);when(policy.currentPolicyVersion()).thenAnswer(i->{change("scanResult.remoteReview.origin.deploymentId","22222222-2222-2222-2222-222222222222");return binding.policyVersion();});
        assertFalse(completion.handleRemoteCompletedScan(claim,cleanResult()));assertEquals("REMOTE_REVIEW",saved().getScanState());assertFalse(saved().isArtifactVerified());
    }

}
