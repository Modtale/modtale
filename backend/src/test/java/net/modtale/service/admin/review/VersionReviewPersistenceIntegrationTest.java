package net.modtale.service.admin.review;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import net.modtale.config.db.MongoConfig;
import net.modtale.model.project.*;
import net.modtale.service.security.scan.ScanEvidenceFixtures;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.convert.*;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REVIEW_DB_TEST",matches="true")
class VersionReviewPersistenceIntegrationTest {
    private net.modtale.model.project.RemoteReviewBinding prepareRemote() {
        var scan=new ScanResult();scan.setStatus(ScanStatus.SCANNING);scan.setScanState("SCANNING");scan.setScanAttempt(1);
        scan.setScanRequestId(UUID.randomUUID().toString());version.setScanResult(scan);version.setFileUrl("original.zip");
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)),new Update().set("versions.0",version),Project.class);
        return new RemoteReviewBinding(id,version.getId(),scan.getScanRequestId(),1,version.getFileUrl(),version.getHash(),
                net.modtale.service.security.scan.ArtifactReviewContext.automaticallyReviewableFingerprint(version),
                "warden-3.0.0:"+"a".repeat(64),"b".repeat(64),null,false,new net.modtale.model.project.RemoteReviewOrigin("11111111-1111-1111-1111-111111111111","e".repeat(64)));
    }
    @Test void remoteBindingSurvivesRestartAndAttachesOnlyOneJob() {
        var binding=prepareRemote();var remote=new net.modtale.service.security.scan.RemoteReviewPersistence(mongo);
        assertTrue(remote.bind(version,binding));assertTrue(remote.bind(version,binding));
        var restored=mongo.findById(id,Project.class).getVersions().getFirst();assertEquals(binding,restored.getScanResult().getRemoteReview());
        var restarted=new net.modtale.service.security.scan.RemoteReviewPersistence(mongo);String job=UUID.randomUUID().toString();
        assertTrue(restarted.attachJob(restored,binding,job));assertTrue(restarted.attachJob(restored,binding,job));
        assertFalse(restarted.attachJob(restored,binding,UUID.randomUUID().toString()));assertFalse(restarted.bind(restored,binding));
        assertEquals(binding.withJobId(job),mongo.findById(id,Project.class).getVersions().getFirst().getScanResult().getRemoteReview());
    }
    @Test void changedContextCannotBindOrAttachRemoteJob() {
        var binding=prepareRemote();var remote=new net.modtale.service.security.scan.RemoteReviewPersistence(mongo);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)),new Update().set("versions.0.manifestVersion","changed"),Project.class);
        assertFalse(remote.bind(version,binding));
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)),new Update().set("versions.0.manifestVersion",version.getManifestVersion()),Project.class);
        assertTrue(remote.bind(version,binding));
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)),new Update().set("versions.0.hash","c".repeat(64)),Project.class);
        assertFalse(remote.attachJob(version,binding,UUID.randomUUID().toString()));
    }
    @Test void newRequestAndCompetingConfigurationCannotReplaceBinding() {
        var binding=prepareRemote();var remote=new net.modtale.service.security.scan.RemoteReviewPersistence(mongo);
        assertTrue(remote.bind(version,binding));
        var other=new RemoteReviewBinding(binding.projectId(),binding.versionId(),binding.requestId(),binding.attempt(),binding.filePath(),
                binding.artifactSha256(),binding.contextSha256(),binding.policyVersion(),"c".repeat(64),null,false,new net.modtale.model.project.RemoteReviewOrigin("11111111-1111-1111-1111-111111111111","e".repeat(64)));
        assertFalse(remote.bind(version,other));
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)),new Update().set("versions.0.scanResult.scanRequestId",UUID.randomUUID().toString()),Project.class);
        assertFalse(remote.attachJob(version,binding,UUID.randomUUID().toString()));assertFalse(remote.bind(version,binding));
    }
    @Test void legacyRecoveryCannotReplaceRemoteReviewEvenWithOldSnapshot() {
        var binding=prepareRemote();var observed=version.getScanResult();var remote=new net.modtale.service.security.scan.RemoteReviewPersistence(mongo);
        assertTrue(remote.bind(version,binding));
        var scans=new net.modtale.service.security.scan.ScanPersistenceService(mongo,
                org.mockito.Mockito.mock(net.modtale.repository.project.ProjectRepository.class),org.mockito.Mockito.mock(net.modtale.service.project.query.ProjectService.class));
        assertFalse(scans.queueRetryAttempt(id,version.getId(),1,new ScanResult(),observed,1));
        assertFalse(scans.updateTimedOutScan(id,version.getId(),new ScanResult(),1,observed,1));
        var restored=mongo.findById(id,Project.class).getVersions().getFirst();
        assertFalse(scans.queueRetryAttempt(id,version.getId(),1,new ScanResult(),restored.getScanResult(),1));
        assertEquals(binding,restored.getScanResult().getRemoteReview());
    }
    private net.modtale.service.security.scan.RemoteReviewPollStore pollStore() {
        return new net.modtale.service.security.scan.RemoteReviewPollStore(mongo);
    }
    private RemoteReviewBinding boundRemote() {
        var binding=prepareRemote();assertTrue(new net.modtale.service.security.scan.RemoteReviewPersistence(mongo).bind(version,binding));return binding;
    }
    @Test void onlyOneConcurrentPollerOwnsTheRetainedRequest()throws Exception {
        var binding=boundRemote();var start=new java.util.concurrent.CountDownLatch(1);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var a=executor.submit(()->{start.await();return pollStore().claim(binding,30000);});
            var b=executor.submit(()->{start.await();return pollStore().claim(binding,30000);});start.countDown();
            var first=a.get();var second=b.get();assertTrue((first==null)!=(second==null));
            assertTrue(pollStore().isCurrent(first==null?second:first));assertNull(pollStore().claim(binding,30000));
        }
    }
    @Test void pollLeaseTakeoverFencesExpiredOwnerAndPreservesUnrelatedFields() {
        var binding=boundRemote();mongo.updateFirst(Query.query(Criteria.where("_id").is(id)),new Update()
                .set("versions.0.unmodeledField","keep").set("versions.1.rejectionReason","sibling").set("title","keep title"),Project.class);
        var old=pollStore().claim(binding,30000);assertNotNull(old);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)),new Update().set("versions.0.scanResult.remotePoll.leaseUntil",new Date(0)),Project.class);
        assertFalse(pollStore().isCurrent(old));assertFalse(pollStore().release(old,100));assertNull(pollStore().attachJob(old,UUID.randomUUID().toString()));
        var next=pollStore().claim(binding,30000);assertNotNull(next);assertNotEquals(old.token(),next.token());assertFalse(pollStore().release(old,100));
        assertTrue(pollStore().isCurrent(next));var project=mongo.findById(id,Project.class);assertEquals("keep title",project.getTitle());assertEquals("sibling",project.getVersions().get(1).getRejectionReason());
        assertNotNull(project.getVersions().getFirst().getScanResult().getRemotePoll());
        assertEquals("keep",mongo.getCollection("projects").find().first().getList("versions",Document.class).getFirst().getString("unmodeledField"));
    }
    @Test void completedPollPersistsDatabaseDelayAndCannotBeReused() {
        var binding=boundRemote();var before=client.getDatabase(database).runCommand(new Document("hello",1)).getDate("localTime");
        var owner=pollStore().claim(binding,30000);assertNotNull(owner);
        var after=client.getDatabase(database).runCommand(new Document("hello",1)).getDate("localTime");
        var acquired=mongo.findById(id,Project.class).getVersions().getFirst().getScanResult().getRemotePoll();
        assertTrue(acquired.leaseUntil().getTime()>=before.getTime()+30000);assertTrue(acquired.leaseUntil().getTime()<=after.getTime()+30000);
        assertTrue(pollStore().release(owner,30000));assertFalse(pollStore().release(owner,100));assertFalse(pollStore().isCurrent(owner));assertNull(pollStore().claim(binding,30000));
        var state=mongo.findById(id,Project.class).getVersions().getFirst().getScanResult().getRemotePoll();assertNull(state.token());assertEquals(new Date(0),state.leaseUntil());
        assertTrue(state.nextPollAt().getTime()>System.currentTimeMillis());
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)),new Update().set("versions.0.scanResult.remotePoll.nextPollAt",new Date(0)),Project.class);
        assertNotNull(pollStore().claim(binding,30000));
    }
    @Test void onlyLivePollCanAttachJobAndMustUseUpdatedBindingAfterward() {
        var binding=boundRemote();var owner=pollStore().claim(binding,30000);String job=UUID.randomUUID().toString();
        assertFalse(new net.modtale.service.security.scan.RemoteReviewPersistence(mongo).attachJob(version,binding,job));
        var attached=pollStore().attachJob(owner,job);assertNotNull(attached);assertEquals(binding.withJobId(job),attached.binding());
        assertFalse(pollStore().isCurrent(owner));assertTrue(pollStore().isCurrent(attached));assertNotNull(pollStore().attachJob(attached,job));
        assertThrows(IllegalArgumentException.class,()->pollStore().attachJob(attached,UUID.randomUUID().toString()));assertTrue(pollStore().release(attached,100));
    }
    @Test void contextAndRequestChangesInvalidatePollOwnership() {
        var binding=boundRemote();var owner=pollStore().claim(binding,30000);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)),new Update().set("versions.0.manifestVersion","new runtime"),Project.class);
        assertFalse(pollStore().isCurrent(owner));assertFalse(pollStore().release(owner,100));assertNull(pollStore().claim(binding,30000));
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)),new Update().set("versions.0.manifestVersion",version.getManifestVersion())
                .set("versions.0.scanResult.scanRequestId",UUID.randomUUID().toString()),Project.class);
        assertFalse(pollStore().isCurrent(owner));assertNull(pollStore().attachJob(owner,UUID.randomUUID().toString()));
    }
    @Test void malformedPollAndDuplicateVersionIdentityCannotBeClaimed() {
        var binding=boundRemote();mongo.updateFirst(Query.query(Criteria.where("_id").is(id)),new Update()
                .set("versions.0.scanResult.remotePoll",new Document("leaseUntil","bad")),Project.class);assertNull(pollStore().claim(binding,30000));
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)),new Update().unset("versions.0.scanResult.remotePoll")
                .set("versions.1._id",version.getId()),Project.class);assertNull(pollStore().claim(binding,30000));
    }
    @Test void pollIntervalsAreBoundedBeforeDatabaseWork() {
        var binding=boundRemote();assertThrows(IllegalArgumentException.class,()->pollStore().claim(binding,999));assertThrows(IllegalArgumentException.class,()->pollStore().claim(binding,120001));
        var owner=pollStore().claim(binding,30000);assertThrows(IllegalArgumentException.class,()->pollStore().release(owner,99));assertThrows(IllegalArgumentException.class,()->pollStore().release(owner,3600001));
    }
    @Test void staleRecoveryCannotReplaceAnAttemptThatStartedAfterItsSnapshot() {
        var scan = new ScanResult(); scan.setStatus(ScanStatus.SCANNING); scan.setScanState("QUEUED");
        scan.setScanAttempt(1); scan.setScanTimestamp(System.currentTimeMillis()-120_000);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), new Update()
                .set("versions.0.reviewStatus", ProjectVersion.ReviewStatus.PENDING).set("versions.0.scanResult", scan), Project.class);
        var scans = new net.modtale.service.security.scan.ScanPersistenceService(mongo,
                org.mockito.Mockito.mock(net.modtale.repository.project.ProjectRepository.class),
                org.mockito.Mockito.mock(net.modtale.service.project.query.ProjectService.class));
        assertTrue(scans.markAttemptRunning(id,"version-a",1));
        var queued = new ScanResult(); queued.setStatus(ScanStatus.SCANNING); queued.setScanState("QUEUED"); queued.setScanAttempt(2);
        assertFalse(scans.queueRetryAttempt(id,"version-a",1,queued,scan,60_000));
        assertFalse(scans.updateTimedOutScan(id,"version-a",new ScanResult(),1,scan,60_000));
        assertEquals("SCANNING",mongo.findById(id,Project.class).getVersions().getFirst().getScanResult().getScanState());
    }
    @Test void recoveryRequiresDatabaseExpiryAndAllowsOnlyOneStaleRetry() {
        var scans = new net.modtale.service.security.scan.ScanPersistenceService(mongo,
                org.mockito.Mockito.mock(net.modtale.repository.project.ProjectRepository.class),
                org.mockito.Mockito.mock(net.modtale.service.project.query.ProjectService.class));
        var observed = new ScanResult(); observed.setStatus(ScanStatus.SCANNING); observed.setScanState("SCANNING");
        observed.setScanAttempt(1); observed.setScanTimestamp(System.currentTimeMillis()+86_400_000);
        var query = Query.query(Criteria.where("_id").is(id));
        mongo.updateFirst(query,new Update().set("versions.0.reviewStatus",ProjectVersion.ReviewStatus.PENDING)
                .set("versions.0.scanResult",observed).set("futureField","preserve"),Project.class);
        var queued = new ScanResult(); queued.setStatus(ScanStatus.SCANNING); queued.setScanState("QUEUED"); queued.setScanAttempt(2);
        assertFalse(scans.queueRetryAttempt(id,"version-a",1,queued,observed,60_000));
        assertFalse(scans.updateTimedOutScan(id,"version-a",new ScanResult(),1,observed,60_000));
        observed.setScanTimestamp(System.currentTimeMillis()-120_000);
        mongo.updateFirst(query,new Update().set("versions.0.scanResult",observed),Project.class);
        // Same state/attempt but a newer timestamp must reject the stale recovery snapshot.
        mongo.updateFirst(query,new Update().set("versions.0.scanResult.scanTimestamp",System.currentTimeMillis()),Project.class);
        assertFalse(scans.queueRetryAttempt(id,"version-a",1,queued,observed,60_000));
        mongo.updateFirst(query,new Update().set("versions.0.scanResult",observed),Project.class);
        assertTrue(scans.queueRetryAttempt(id,"version-a",1,queued,observed,60_000));
        assertFalse(scans.queueRetryAttempt(id,"version-a",1,queued,observed,60_000));
        assertEquals("preserve",mongo.getCollection("projects").find().first().getString("futureField"));
        assertEquals(2,mongo.findById(id,Project.class).getVersions().getFirst().getScanResult().getScanAttempt());
    }
    @Test void expiredTerminalRecoveryIsConditionalAndPreservesOtherVersions() {
        var scans = new net.modtale.service.security.scan.ScanPersistenceService(mongo,
                org.mockito.Mockito.mock(net.modtale.repository.project.ProjectRepository.class),
                org.mockito.Mockito.mock(net.modtale.service.project.query.ProjectService.class));
        var observed=new ScanResult(); observed.setStatus(ScanStatus.SCANNING); observed.setScanState("WAITING_RETRY");
        observed.setScanAttempt(3); observed.setScanTimestamp(0);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)),new Update()
                .set("versions.0.reviewStatus",ProjectVersion.ReviewStatus.PENDING).set("versions.0.scanResult",observed),Project.class);
        var failed=new ScanResult(); failed.setStatus(ScanStatus.FAILED); failed.setScanState("FAILED");
        assertTrue(scans.updateTimedOutScan(id,"version-a",failed,3,observed,60_000));
        assertFalse(scans.updateTimedOutScan(id,"version-a",failed,3,observed,60_000));
        assertEquals("version-b",mongo.findById(id,Project.class).getVersions().get(1).getId());
    }
    @Test void oldAttemptOneCannotFailAReplacementAttemptOne() throws Exception {
        var scans = new net.modtale.service.security.scan.ScanPersistenceService(mongo,
                org.mockito.Mockito.mock(net.modtale.repository.project.ProjectRepository.class),
                org.mockito.Mockito.mock(net.modtale.service.project.query.ProjectService.class));
        var current = new ScanResult(); current.setStatus(ScanStatus.SCANNING); current.setScanState("SCANNING"); current.setScanAttempt(1); current.setScanRequestId("new-request");
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)),new Update()
                .set("versions.0.reviewStatus",ProjectVersion.ReviewStatus.PENDING).set("versions.0.scanResult",current),Project.class);
        var failed = new ScanResult(); failed.setStatus(ScanStatus.FAILED);
        assertFalse(scans.updateFailedScan(id,"version-a",failed,1));
        assertEquals(ScanStatus.SCANNING,mongo.findById(id,Project.class).getVersions().getFirst().getScanResult().getStatus());
        assertFalse(scans.updateFailedScan(id,"version-a",failed,1,"old-request"));
        // Reproduce the pre-fix predicate directly: it only distinguishes attempt/state, so it matches the replacement.
        var legacy = scans.getClass().getDeclaredMethod("buildVersionAttemptQuery",String.class,String.class,int.class,String[].class);
        legacy.setAccessible(true);
        var query = (Query)legacy.invoke(scans,id,"version-a",1,new String[]{"SCANNING","QUEUED","WAITING_RETRY",null});
        assertEquals(1,mongo.updateFirst(query,new Update().set("versions.$.scanResult",failed),Project.class).getModifiedCount());

    }
    @Test void requestIdentityBindsClaimsOutcomesAndRecoveryEvenWhenAttemptAndTimestampMatch() {
        var scans = new net.modtale.service.security.scan.ScanPersistenceService(mongo,
                org.mockito.Mockito.mock(net.modtale.repository.project.ProjectRepository.class),
                org.mockito.Mockito.mock(net.modtale.service.project.query.ProjectService.class));
        var current = new ScanResult(); current.setStatus(ScanStatus.SCANNING); current.setScanState("QUEUED");
        current.setScanAttempt(1); current.setScanRequestId("replacement"); current.setScanTimestamp(System.currentTimeMillis()-120_000);
        var query=Query.query(Criteria.where("_id").is(id));
        mongo.updateFirst(query,new Update().set("versions.0.reviewStatus",ProjectVersion.ReviewStatus.PENDING)
                .set("versions.0.scanResult",current),Project.class);
        assertEquals("replacement",mongo.findById(id,Project.class).getVersions().getFirst().getScanResult().getScanRequestId());
        assertFalse(scans.markAttemptRunning(id,"version-a",1));
        assertFalse(scans.markAttemptRunning(id,"version-a",1,"obsolete"));
        current.setScanRequestId("obsolete");
        assertFalse(scans.queueRetryAttempt(id,"version-a",1,new ScanResult(),current,60_000));
        assertFalse(scans.updateTimedOutScan(id,"version-a",new ScanResult(),1,current,60_000));
        assertTrue(scans.markAttemptRunning(id,"version-a",1,"replacement"));
        assertFalse(scans.markAttemptRunning(id,"version-a",1,"replacement"));
        mongo.updateFirst(query,new Update().set("versions.0.hash","b".repeat(64)),Project.class);
        var reviewed=mongo.findById(id,Project.class).getVersions().getFirst();
        var result=ScanEvidenceFixtures.complete(false);
        var routing=new net.modtale.service.security.scan.ScanRoutingService.RoutingDecision(
                net.modtale.service.security.scan.ScanRoutingService.RoutingAction.REQUIRE_REVIEW,0);
        assertFalse(scans.applyScanOutcome(id,"version-a",1,result,routing,reviewed));
        assertFalse(scans.applyScanOutcome(id,"version-a",1,result,routing,reviewed,"obsolete"));
        assertTrue(scans.applyScanOutcome(id,"version-a",1,result,routing,reviewed,"replacement"));
        assertEquals("replacement",mongo.findById(id,Project.class).getVersions().getFirst().getScanResult().getScanRequestId());
    }
    @Test void metadataRepairPreservesVersionsEvidenceAndUnknownFields() {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), new Update().set("futureField", "retain"), Project.class);
        var repairs = new ProjectReviewPersistence(mongo);
        var project = mongo.findById(id, Project.class);
        var snapshot = repairs.capture(id, ProjectReviewSnapshot.token(project));
        var originalVersions = snapshot.raw().get("versions");
        assertTrue(repairs.applyMetadataRepair(snapshot, Map.of("title", "Metadata repaired", "links", Map.of("source", "https://example.com"))));
        var raw = mongo.getCollection("projects").find().first();
        assertEquals(originalVersions, raw.get("versions"));
        assertEquals("retain", raw.getString("futureField"));
        assertEquals("Metadata repaired", raw.getString("title"));
        assertTrue(net.modtale.service.security.scan.ArtifactClearancePolicy.complete(mongo.findById(id, Project.class).getVersions().getFirst().getScanResult()));
    }
    @Test void metadataRepairRejectsForgedStateAndConcurrentDecisions() {
        var repairs = new ProjectReviewPersistence(mongo);
        var project = mongo.findById(id, Project.class);
        var snapshot = repairs.capture(id, ProjectReviewSnapshot.token(project));
        assertThrows(ResponseStatusException.class, () -> repairs.applyMetadataRepair(snapshot, Map.of("status", "PUBLISHED")));
        assertEquals("original", mongo.findById(id, Project.class).getTitle());
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), new Update()
                .set("versions.0.reviewStatus", ProjectVersion.ReviewStatus.REJECTED), Project.class);
        assertFalse(repairs.applyMetadataRepair(snapshot, Map.of("title", "Stale title")));
        assertEquals("original", mongo.findById(id, Project.class).getTitle());
        assertEquals(ProjectVersion.ReviewStatus.REJECTED, mongo.findById(id, Project.class).getVersions().getFirst().getReviewStatus());
    }
    @Test void conditionalApprovalRetainsFindingEvidenceAfterScanPruningAndReload() {
        var loaded = mongo.findById(id, Project.class);
        var selected = loaded.getVersions().getFirst();
        selected.setHash(selected.getScanResult().getSecurityEvidence().artifactSha256());
        var issue = new ScanResult.ScanIssue(); issue.setFilePath("Mod.class");
        issue.setType("Network"); issue.setDescription("connect"); issue.setSeverity("LOW");
        selected.getScanResult().setIssues(new ArrayList<>(List.of(issue)));
        mongo.save(loaded);
        selected = mongo.findById(id, Project.class).getVersions().getFirst();
        var snapshot = persistence.capture(id, selected.getId(), VersionReviewSnapshot.token(selected));
        var classification = new net.modtale.service.security.issue.SecurityIssueClassificationService(
                new net.modtale.config.properties.AppSecurityProperties("test", 60, 120, 2, 12, 15, 120, 25, 2));
        new net.modtale.service.security.issue.SecurityIssueApprovalService(classification)
                .markIssuesAcceptedForApprovedVersion(selected);
        selected.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);
        assertTrue(persistence.apply(snapshot, selected));
        var restored = mongo.findById(id, Project.class);
        var baseline = restored.getVersions().getFirst().getApprovedIssueBaselines().getFirst();
        assertTrue(baseline.getEvidenceIdentity().matches("ie1:[0-9a-f]{64}"));
        assertNull(restored.getVersions().getFirst().getScanResult());
        var nextScan = ScanEvidenceFixtures.complete(false);
        var nextIssue = new ScanResult.ScanIssue(); nextIssue.setFilePath("Mod.class");
        nextIssue.setType("Network"); nextIssue.setDescription("connect"); nextIssue.setSeverity("LOW");
        nextScan.setIssues(new ArrayList<>(List.of(nextIssue)));
        classification.annotateAgainstBaselines(nextScan, classification.collectApprovedIssueBaselines(restored, null));
        assertTrue(nextIssue.isHistoricalFileEvidenceIdentical());
        assertFalse(nextIssue.isResolved());
        assertFalse(net.modtale.service.security.scan.ArtifactClearancePolicy.cleared(nextScan));
    }
    @Test void duplicateVersionIdentityIsRejectedBeforeSnapshotCapture() {
        var collection=mongo.getCollection("projects");var raw=collection.find().first();var duplicate=raw.getList("versions",Document.class).getFirst();
        collection.updateOne(new Document("_id",raw.get("_id")),new Document("$push",new Document("versions",duplicate)));
        assertThrows(ResponseStatusException.class,()->persistence.captureForRescan(id,version.getId(),VersionReviewSnapshot.rescanToken(version)));
        assertThrows(ResponseStatusException.class,()->persistence.capture(id,version.getId(),VersionReviewSnapshot.token(version)));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"rescan","decision","finding"})
    void duplicateInsertedAfterSnapshotPreventsEveryVersionDecisionWrite(String action) {
        var snapshot=persistence.captureForRescan(id,version.getId(),VersionReviewSnapshot.rescanToken(version));
        var duplicate=new Document(snapshot.version());duplicate.put("versionNumber","concurrent duplicate");
        var collection=mongo.getCollection("projects");collection.updateOne(new Document("_id",snapshot.projectId()),new Document("$push",new Document("versions",duplicate)));
        var before=collection.find().first();boolean applied;
        switch(action) {
            case "rescan" -> applied=persistence.queueRescan(snapshot,queued());
            case "finding" -> applied=persistence.appendFindingReview(snapshot,"new decision");
            default -> {version.setReviewStatus(ProjectVersion.ReviewStatus.REJECTED);applied=persistence.apply(snapshot,version);}
        }
        assertFalse(applied);assertEquals(before,collection.find().first());
    }
    @Test void unrelatedMalformedSiblingDoesNotPreventCapturingUniqueVersion() {
        var collection=mongo.getCollection("projects");var raw=collection.find().first();
        collection.updateOne(new Document("_id",raw.get("_id")),new Document("$push",new Document("versions",new Document("$each",List.of(
                new Document("_id","broken").append("scanResult","invalid"))).append("$position",0))));
        var snapshot=persistence.captureForRescan(id,version.getId(),VersionReviewSnapshot.rescanToken(version));
        assertTrue(persistence.queueRescan(snapshot,queued()));
    }
    @Test void caseInsensitiveCollectionCannotHideAChangedSnapshot() {
        var collection=mongo.getCollection("projects");var original=collection.find().first();collection.drop();
        mongo.getDb().createCollection("projects",new com.mongodb.client.model.CreateCollectionOptions().collation(
                com.mongodb.client.model.Collation.builder().locale("en").collationStrength(com.mongodb.client.model.CollationStrength.SECONDARY).build()));
        collection=mongo.getCollection("projects");collection.insertOne(original);
        collection.updateOne(new Document("_id",original.get("_id")),new Document("$set",new Document("versions.0.versionNumber","release")));
        var current=mongo.findById(id,Project.class).getVersions().getFirst();
        var snapshot=persistence.captureForRescan(id,current.getId(),VersionReviewSnapshot.rescanToken(current));
        collection.updateOne(new Document("_id",original.get("_id")),new Document("$set",new Document("versions.0.versionNumber","RELEASE")));
        assertFalse(persistence.queueRescan(snapshot,queued()));
    }
    private ScanResult queued() {
        var scan = new ScanResult(); scan.setStatus(ScanStatus.SCANNING);
        scan.setScanState("QUEUED"); scan.setScanAttempt(2); return scan;
    }
    @Test void missingPriorManifestDoesNotPreventRescanning() {
        mongo.getCollection(net.modtale.config.db.MongoArtifactManifestStore.COLLECTION).deleteMany(new Document());
        var current = mongo.findById(id, Project.class).getVersions().getFirst();
        var snapshot = persistence.captureForRescan(id, current.getId(), VersionReviewSnapshot.rescanToken(current));
        assertTrue(persistence.queueRescan(snapshot, queued()));
        var result = mongo.findById(id, Project.class).getVersions().getFirst();
        assertEquals(ProjectVersion.ReviewStatus.PENDING, result.getReviewStatus());
        assertEquals("QUEUED", result.getScanResult().getScanState());
        assertFalse(net.modtale.service.security.scan.ArtifactClearancePolicy.cleared(result.getScanResult()));
    }
    @Test void rescanPreservesConcurrentSiblingAndUnknownFieldsAndOnlyOneRequestWins() {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), new Update()
                .set("versions.0.legacyMarker", "retain"), Project.class);
        var snapshot = persistence.captureForRescan(id, version.getId(), VersionReviewSnapshot.rescanToken(version));
        var competing = persistence.captureForRescan(id, version.getId(), VersionReviewSnapshot.rescanToken(version));
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), new Update()
                .set("title", "concurrent title").set("versions.1.reviewStatus", ProjectVersion.ReviewStatus.REJECTED), Project.class);
        assertTrue(persistence.queueRescan(snapshot, queued()));
        assertFalse(persistence.queueRescan(competing, queued()));
        var saved = mongo.findById(id, Project.class);
        assertEquals("concurrent title", saved.getTitle());
        assertEquals(ProjectVersion.ReviewStatus.REJECTED, saved.getVersions().get(1).getReviewStatus());
        assertEquals("QUEUED", saved.getVersions().getFirst().getScanResult().getScanState());
        assertEquals(2, saved.getVersions().getFirst().getScanResult().getScanAttempt());
        assertEquals("retain", mongo.getCollection("projects").find().first().getList("versions", Document.class).getFirst().getString("legacyMarker"));
    }
    @Test void rescanRejectsChangesToArtifactContextOrReviewDecision() {
        var changes = List.of(new Update().set("versions.0.hash", "f".repeat(64)),
                new Update().set("versions.0.gameVersions", List.of("changed")),
                new Update().set("versions.0.reviewStatus", ProjectVersion.ReviewStatus.APPROVED),
                new Update().set("versions.0.reviewStatus", ProjectVersion.ReviewStatus.REJECTED));
        for (var change : changes) {
            var current = mongo.findById(id, Project.class).getVersions().getFirst();
            var snapshot = persistence.captureForRescan(id, current.getId(), VersionReviewSnapshot.rescanToken(current));
            mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), change, Project.class);
            assertFalse(persistence.queueRescan(snapshot, queued()));
            assertThrows(ResponseStatusException.class, () -> persistence.captureForRescan(id, current.getId(), VersionReviewSnapshot.rescanToken(current)));
            assertNotEquals("QUEUED", mongo.findById(id, Project.class).getVersions().getFirst().getScanResult().getScanState());
        }
    }
    private MongoClient client;
    private MongoTemplate mongo;
    private VersionReviewPersistence persistence;
    private String database;
    private ProjectVersion version;
    private final String id="abcdefabcdefabcdefabcdef";
    @BeforeEach void setup() throws Exception {
        database="warden_review_test_"+UUID.randomUUID().toString().replace("-","");
        String port=System.getenv().getOrDefault("WARDEN_REVIEW_DB_PORT","27029");
        if(!Set.of("27029","27030").contains(port)) throw new IllegalArgumentException("Unexpected test database port");
        client=MongoClients.create("mongodb://127.0.0.1:"+port+"/?serverSelectionTimeoutMS=3000");
        var factory=new SimpleMongoClientDatabaseFactory(client,database);
        var conversions=new MongoConfig().mongoCustomConversions(new net.modtale.config.db.MongoArtifactManifestStore(factory));
        var context=new MongoMappingContext();context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());context.afterPropertiesSet();
        var converter=new MappingMongoConverter(new DefaultDbRefResolver(factory),context);
        converter.setCustomConversions(conversions);converter.afterPropertiesSet();
        mongo=new MongoTemplate(factory,converter);persistence=new VersionReviewPersistence(mongo);
        var project=new Project();project.setId(id);project.setTitle("original");
        version=new ProjectVersion();version.setId("version-a");version.setVersionNumber("1.0");
        version.setHash("a".repeat(64));version.setScanResult(ScanEvidenceFixtures.complete(true));
        var sibling=new ProjectVersion();sibling.setId("version-b");sibling.setVersionNumber("2.0");
        project.setVersions(List.of(version,sibling));mongo.insert(project);
    }
    @Test void projectPublicationPreservesUnreviewedVersionsAndUnknownFields() {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), new Update()
                .set("versions.0.legacyMarker", "preserve").set("unmodeledProjectField", "preserve"), Project.class);
        var project = mongo.findById(id, Project.class);
        var projectPersistence = new ProjectReviewPersistence(mongo);
        var snapshot = projectPersistence.capture(id, ProjectReviewSnapshot.token(project));
        snapshot.project().setStatus(ProjectStatus.PUBLISHED);
        snapshot.project().getVersions().getFirst().setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);
        assertTrue(projectPersistence.apply(snapshot, "version-a"));
        var saved = mongo.findById(id, Project.class);
        assertEquals(ProjectStatus.PUBLISHED, saved.getStatus());
        assertEquals(ProjectVersion.ReviewStatus.APPROVED, saved.getVersions().getFirst().getReviewStatus());
        assertEquals(ProjectVersion.ReviewStatus.PENDING, saved.getVersions().get(1).getReviewStatus());
        var raw = mongo.getCollection("projects").find().first();
        assertEquals("preserve", raw.getString("unmodeledProjectField"));
        assertEquals("preserve", raw.getList("versions", Document.class).getFirst().getString("legacyMarker"));
    }
    @Test void concurrentProjectChangePreventsPublication() {
        var project = mongo.findById(id, Project.class);
        var projectPersistence = new ProjectReviewPersistence(mongo);
        var snapshot = projectPersistence.capture(id, ProjectReviewSnapshot.token(project));
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), new Update().set("title", "changed"), Project.class);
        snapshot.project().setStatus(ProjectStatus.PUBLISHED);
        snapshot.project().getVersions().getFirst().setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);
        assertFalse(projectPersistence.apply(snapshot, "version-a"));
        assertEquals("changed", mongo.findById(id, Project.class).getTitle());
        assertEquals(ProjectVersion.ReviewStatus.PENDING, mongo.findById(id, Project.class).getVersions().getFirst().getReviewStatus());
    }
    @Test void changedVersionInvalidatesProjectReviewInBrowser() {
        var project = mongo.findById(id, Project.class);
        String token = ProjectReviewSnapshot.token(project);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), new Update().set("versions.0.hash", "f".repeat(64)), Project.class);
        assertThrows(ResponseStatusException.class, () -> new ProjectReviewPersistence(mongo).capture(id, token));
    }
    @Test void referencedManifestsAreVerifiedAndMissingOrCorruptRecordsCannotClear() {
        var raw = mongo.getCollection("projects").find().first();
        var evidence = raw.getList("versions", Document.class).getFirst().get("scanResult", Document.class).get("securityEvidence", Document.class);
        String reference = evidence.getString("manifestRef");
        assertNotNull(reference); assertFalse(evidence.containsKey("entryHashes"));
        assertTrue(net.modtale.service.security.scan.ArtifactClearancePolicy.complete(mongo.findById(id, Project.class).getVersions().getFirst().getScanResult()));
        var manifests = mongo.getCollection(net.modtale.config.db.MongoArtifactManifestStore.COLLECTION);
        manifests.updateOne(new Document("_id", reference), new Document("$set", new Document("entries", List.of(new Document("path", "substituted").append("sha256", "f".repeat(64))))));
        assertFalse(net.modtale.service.security.scan.ArtifactClearancePolicy.complete(mongo.findById(id, Project.class).getVersions().getFirst().getScanResult()));
        manifests.deleteOne(new Document("_id", reference));
        assertFalse(net.modtale.service.security.scan.ArtifactClearancePolicy.complete(mongo.findById(id, Project.class).getVersions().getFirst().getScanResult()));
    }
    @Test void legacyEmbeddedEvidenceMigratesWithoutChangingTheBrowserReviewToken() {
        var scan = ScanEvidenceFixtures.complete(true);
        var hashes = new LinkedHashMap<String,String>(); hashes.put("z/nested.class", "b".repeat(64)); hashes.put("a.$manifest", "c".repeat(64));
        var evidence = scan.getSecurityEvidence();
        var updated = new ScanResult.SecurityEvidence(evidence.policyVersion(), evidence.artifactSha256(), SecurityManifest.identity(hashes), true, true, "COMPLETED", hashes);
        var legacy = new net.modtale.config.db.SecurityEvidenceConverters.Write().convert(updated);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), new Update().set("versions.0.scanResult.securityEvidence", legacy), Project.class);
        var before = mongo.findById(id, Project.class);
        assertTrue(net.modtale.service.security.scan.ArtifactClearancePolicy.complete(before.getVersions().getFirst().getScanResult()));
        String token = VersionReviewSnapshot.token(before.getVersions().getFirst());
        mongo.save(before);
        var after = mongo.findById(id, Project.class);
        assertEquals(token, VersionReviewSnapshot.token(after.getVersions().getFirst()));
        assertEquals(hashes, after.getVersions().getFirst().getScanResult().getSecurityEvidence().entryHashes());
        var raw = mongo.getCollection("projects").find().first().getList("versions", Document.class).getFirst()
                .get("scanResult", Document.class).get("securityEvidence", Document.class);
        assertTrue(raw.containsKey("manifestRef")); assertFalse(raw.containsKey("entryHashes"));
    }
    @Test void sixLargeManifestsNoLongerExceedTheProjectDocumentLimit() {
        var hashes = new LinkedHashMap<String,String>();
        for (int i = 0; i < 20_000; i++) hashes.put(String.format("p/%05d/", i) + "a".repeat(30) + ".class", "a".repeat(64));
        String identity = SecurityManifest.identity(hashes);
        var project = new Project(); project.setId("large-manifest-fixture");
        var versions = new ArrayList<ProjectVersion>();
        for (int i = 0; i < 6; i++) {
            var item = new ProjectVersion(); item.setId("v" + i);
            var scan = ScanEvidenceFixtures.complete(true);
            var prior = scan.getSecurityEvidence();
            scan.setSecurityEvidence(new ScanResult.SecurityEvidence(prior.policyVersion(), prior.artifactSha256(), identity, true, true, "COMPLETED", hashes));
            item.setScanResult(scan); versions.add(item);
        }
        project.setVersions(versions); mongo.insert(project);
        var raw = mongo.getCollection("projects").find(new Document("_id", "large-manifest-fixture")).first();
        assertNotNull(raw);
        assertTrue(new org.bson.RawBsonDocument(raw, new org.bson.codecs.DocumentCodec()).getByteBuffer().remaining() < 64_000);
        assertEquals(1, mongo.getCollection(net.modtale.config.db.MongoArtifactManifestStore.COLLECTION).countDocuments(new Document("_id", identity)));
        var restored = mongo.findById("large-manifest-fixture", Project.class);
        for (var item : restored.getVersions()) assertTrue(net.modtale.service.security.scan.ArtifactClearancePolicy.complete(item.getScanResult()));
    }
    @AfterEach void cleanup() {if(client!=null) {client.getDatabase(database).drop();client.close();}}
    @Test void conditionalApprovalPreservesConcurrentSiblingAndProjectChanges() {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)),new Update().set("versions.0.legacyMarker","preserve"),Project.class);
        var snapshot=persistence.capture(id,version.getId(),VersionReviewSnapshot.token(version));
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)),new Update().set("title","concurrent title")
                .set("versions.1.rejectionReason","independent sibling edit"),Project.class);
        version.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);version.setScanResult(null);
        assertTrue(persistence.apply(snapshot,version));
        var result=mongo.findById(id,Project.class);
        assertEquals("concurrent title",result.getTitle());
        assertEquals(ProjectVersion.ReviewStatus.APPROVED,result.getVersions().getFirst().getReviewStatus());
        assertEquals("independent sibling edit",result.getVersions().get(1).getRejectionReason());
        var raw=mongo.getCollection("projects").find().first();
        assertEquals("preserve",raw.getList("versions",Document.class).getFirst().getString("legacyMarker"));
    }
    @Test void artifactReplacementDuringDecisionPreventsApproval() {
        var snapshot=persistence.capture(id,version.getId(),VersionReviewSnapshot.token(version));
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)),new Update().set("versions.0.hash","b".repeat(64)),Project.class);
        version.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);
        assertFalse(persistence.apply(snapshot,version));
        assertEquals(ProjectVersion.ReviewStatus.PENDING,mongo.findById(id,Project.class).getVersions().getFirst().getReviewStatus());
    }
    @Test void staleBrowserEvidenceIsRejectedBeforeCapturingDecision() {
        String token=VersionReviewSnapshot.token(version);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)),new Update().set("versions.0.scanResult.scanTimestamp",1234L),Project.class);
        assertEquals(409,assertThrows(ResponseStatusException.class,()->persistence.capture(id,version.getId(),token)).getStatusCode().value());
    }
}
